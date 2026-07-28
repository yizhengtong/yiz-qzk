---
name: container-menu-pitfalls
description: "自定义 AbstractContainerMenu 的坑：ItemStack.EMPTY 编码失败、客户端容器不反向同步、虚拟槽坐标、EditBox 失焦"
metadata:
  type: feedback
---

自定义容器 Menu（AbstractContainerMenu + AbstractContainerScreen，如光明指南针 LightCompassMenu/Screen）踩过的非显而易见的坑。

## 1. ItemStack.CODEC 不能编码 EMPTY —— 持久化会静默失败

`ItemStack.CODEC`（含 `.listOf()`）校验 `count ∈ [1,99]` 且 `item != air`。`ItemStack.EMPTY` 的 count=0、item=air，**编码失败**。`PlayerDataAPI.set` 内部 `encodeStart().result().ifPresent(...)` —— 失败时 ifPresent 不触发，**set 静默无效**，没有任何异常。表现：写入后立即 get 读到的是旧值/默认值，数据像"从没存进去"。

**Why:** 工作槽只要有空位（EMPTY），整个 `List<ItemStack>` 编码就失败。曾让"ESC/E 关闭后数据丢失"调了很久，根因全在这里。

**How to apply:** 持久化物品列表时**不要直接存 `List<ItemStack>`**。改存物品的稳定标识：
- 若物品无自定义 components（来自展示栏的裸物品）：存 `List<Integer>`（Item 注册表 ID，-1 表空位），用 `Codec.INT.listOf()`。
- 若需保留 components：用 `ItemStack.OPTIONAL_STREAM_CODEC`（走 StreamCodec，不是 Codec）走单独网络通道，或存 `List<Optional<ItemStack>>` 配合自定义 Codec。

存前可先 `codec.encodeStart(NbtOps.INSTANCE, value)` 检查 `.result().isEmpty()` 打印错误，避免静默失败。

## 2. 客户端改 SimpleContainer 不会反向同步服务端

`AbstractContainerMenu` 的容器同步是**单向**的：服务端 `menu.slots` 的 Container 变化通过 `ClientboundContainerSetSlotPacket` 推给客户端；客户端改 `workContainer.setItem(...)` **不会**回传服务端。

**Why:** 自定义"只进不出"槽（mayPickup/mayPlace=false + Menu.clicked 拦截）无法通过原版 slot 点击修改，客户端 Screen 里直接调 `menu.sendToWorkSlot()` 改的是客户端容器，服务端不知道，持久化（在服务端）自然写不进去。

**How to apply:** 客户端发起的容器修改（展示栏点击入槽、左键移除等）**必须发 C2S 网络包**（仿 `C2SAttributeApplyPayload`），服务端在 `player.containerMenu` 上操作，再由原版同步回客户端 + 触发持久化。Menu 的 load/persist 也要加 `!player.level().isClientSide` 判断，只在服务端执行（客户端 Menu 构造时读持久化会用旧数据覆盖刚同步来的内容）。

## 3. 虚拟展示物品用屏幕绝对坐标，不要放 renderLabels

`AbstractContainerScreen.render` 在调 `renderLabels` 前会 `pose.translate(leftPos, topPos)`，之后 `popPose`。`renderLabels` 内是**局部坐标系**。

**Why:** 我曾把展示栏虚拟物品画在 `renderLabels` 里并用 `leftPos + x`（屏幕绝对坐标），导致坐标被平移两次（leftPos + leftPos + x），物品画到屏幕外，中间网格全空。

**How to apply:** 不在 menu.slots 里的"虚拟槽"物品（创造模式式展示栏）——**override `render`，在 `super.render` 之后用屏幕绝对坐标补画**，不要用 `renderLabels`。tooltip 同理：原版 `renderTooltip` 只认真实 Slot，虚拟物品要自己 `g.renderTooltip(font, stack, mx, my)`。

## 4. EditBox 失焦后无法重新获焦

`EditBox` 失焦（`setFocused(false)`）后，再点击它可能不重新获焦——`super.mouseClicked`（AbstractContainerScreen）的分发在失焦态下没正确触发 EditBox 的 mouseClicked。

**How to apply:** 点 EditBox 时**绕过 super，主动**：`this.setFocused(searchBox)` + `searchBox.mouseClicked(mx, my, button)`，强制重新获焦 + 光标定位。点别处则 `this.setFocused(null)`。打开 GUI 时若不希望搜索框默认占键盘焦点，**不要**调 `setInitialFocus(searchBox)`。

## 5. MenuType 创建用 IContainerFactory（1.21.1）

`new MenuType<>(lambda)` 不行——构造签名是 `MenuType(MenuSupplier, FeatureFlagSet)`。用 NeoForge 的 `IContainerFactory`：
```java
new MenuType<>((IContainerFactory<MyMenu>) (id, inv, data) -> new MyMenu(id, inv), FeatureFlags.DEFAULT_FLAGS)
```

## 6. 自定义 payload 传 ItemStack 用 RegistryFriendlyByteBuf

`ItemStack.OPTIONAL_STREAM_CODEC` 类型是 `StreamCodec<RegistryFriendlyByteBuf, ItemStack>`（序列化需注册表上下文）。payload 的 `STREAM_CODEC` 必须声明为 `StreamCodec<RegistryFriendlyByteBuf, ...>`，不能是 `StreamCodec<ByteBuf, ...>`，否则 `composite` 类型推断失败。
