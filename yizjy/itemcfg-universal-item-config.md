---
name: itemcfg-universal-item-config
description: 1.20.1 万能物品配置系统（itemcfg）：Shift+U 动态 GUI、per-player 门控 + VTable 全局废除、内置 vanilla 持有类适配、GLFW 直读按键
metadata:
  type: project
---

# 万能物品配置系统 itemcfg（1.20.1）

给第三方 mod 物品做"功能可开关"的通用系统（`net.minecraft.client.yiz.itemcfg` 包，2026-09-01 落地，下游 yizxianmod 启动验证通过）。按键 Shift+U 打开动态 GUI，根据物品功能自动生成开关行，服务端权威 per-player 配置写透持久化，mod 移除重装按注册名恢复。

**架构四层**：
- 发现层 `ItemFeatureDiscoverer`：遍历 ForgeRegistries.ITEMS，反射判定覆写 Item 方法 → FeatureType 列表，items.json 缓存。判定用 getMethod+declaringClass（规避 SRG/Mojmap 名差异），排除 Item/IForgeItem/Object。
- 配置层 `ConfigRegistry`：players.json（per-player 覆盖）+ global.json（管理员默认），生效值 `players ?? global ?? false`，写透。
- 关闭引擎**双粒度**：
  - per-player 门控（vanilla 链 mixin，动态可撤销）：结构功能（右键/放置/攻击/交互/食用/蓄力/丢弃），全 vanilla 目标、无 @Shadow、`yizmodqzk$` 前缀。
  - 全局废除层 `ItemConfigAbolition`（VTable 覆写 Item 方法回空，全服）：背包持续效果 inventoryTick/onEquip（per-player 无法切断）。
  - 语义功能（仇恨免疫/取消受击/增伤）：adapter 声明后**本系统施加效果**（Mob.setTarget cancel / hurt false / 伤害×factor），关闭→恢复 vanilla；ItemConfigGates.rescan 每 20 tick 重算 per-player 激活缓存。
- 适配层 `AdapterRegistry`：`config/yizmodqzk/adapters/<modid>.json` 声明式（match 支持 item id 或 mod:*）。

**关键签名坑（1.20.1 Mojmap）**：
- `Item.use` 返回 `InteractionResultHolder<ItemStack>`（非 InteractionResult）。
- `Item.onDroppedByPlayer` 是 IForgeItem default **死钩子**（源码无调用点），UNDROPPABLE 只能靠 `Player.drop(ItemStack,boolean)` 2 参拦截（3 参被死亡掉落复用，拦 3 参吞死亡掉落）。
- 实体交互走 `Player.interactOn`，不是 ServerPlayerGameMode。
- 放置拦截下移到 `ItemStack.useOn`（useItemOn 含方块交互：开箱/拉杆，误禁）。
- `AbstractContainerMenu.doClick` 是 private（禁持有兜底时注意最小判定，宁漏勿误伤所有容器）。
- **持有类引擎级效果（不死图腾免死等）反射识别不了**：vanilla 特殊处理不覆写 Item 方法。GUI 对**所有物品**显示通用"持有/背包效果"（HELD_EFFECTS）开关（declaredBy=2，Screen 标 `[持有]`）；关闭后由**适配库**驱动效果失效——不死图腾=`ItemCfgTotemGateMixin` 挂 HELD_EFFECTS（`checkTotemDeathProtection` HEAD 返回 false，1.20.1 不死图腾无 TotemItem 类，用 `Items.TOTEM_OF_UNDYING` 判断），第三方 mod 的持有类效果靠 adapter 声明。Toggle 校验需允许 HELD_EFFECTS（非 FeatureType 非 adapter）。

**按键检测坑**：vanilla KeyboardHandler 只遍历 `options.keyMappings` 更新 KeyMapping 状态，RegisterKeyMappingsEvent 未注册成功则 consumeClick 永远 false → 按键无效。**解决：GLFW 物理键直读**（`InputConstants.isKeyDown(win, InputConstants.KEY_U)` + 边沿检测 prevUDown），KeyMapping 仅作设置页显示。

**VTable 废除移植**：从 1.21.1 `core/VTableReplace.java`（纯 Unsafe 改 Method 入口指针，偏移运行时探测）+ `tool/abolish/ItemAbolitionHelper.java`（Item 方法描述符常量；1.20.1 需改 appendHoverText 无 TooltipContext、getUseDuration 只收 ItemStack）。**VTable 全局且不可精确逆**（恢复=移除配置+重启）。1.20.1 不死图腾无 TotemItem 类，是普通 Item + `Items.TOTEM_OF_UNDYING`。

**验证流程**：yizmodqzk `build`→`publishToMavenLocal`（下游 mavenLocal 引用 fg.deobf）→ yizxianmod `runClient`。dev 下游可测 vanilla 物品（不死图腾/原版剑）；第三方 mod 物品需生产部署测（anti-tamper-test-not-dev）。

## Curios 饰品槽位集成（2026-09-01）

饰品 mod 通过 Curios 槽位触发效果，不覆写 Item 方法，反射识别不了。**检测基准**：物品是否注册 Curios 饰品槽位 → 注册了就仔细扫描。

- **检测**：`CuriosApi.getItemStackSlots(ItemStack, boolean)` 返回槽位 Map 非空 = 注册了槽位（官方方法，比 `curios:registered_curios` tag 可靠——tag 是代码动态维护，jar 里无数据文件）。扫描时 `new ItemStack(item)` 探针 + false（服务端）。
- **关闭（per-player 通用）**：监听 `CurioEquipEvent`（top.theillusivec4.curios.api.event），CURIOS_SLOT 关闭 → `event.setCanceled(true)` → 该玩家无法装备饰品槽 → 效果不触发。纯事件，不碰 Curios 内部。
- **FeatureType.CURIOS_SLOT**（饰品槽位效果，无结构探针）。
- **编译依赖**：Curios jar 复制到 `yizmodqzk/libs/curios-forge-5.14.1+1.20.1.jar`，build.gradle `compileOnly fg.deobf(files("libs/curios-forge-5.14.1+1.20.1.jar"))`。
- **安全隔离**：`CuriosBridge`（isLoaded 保护 + try-catch）集中 Curios 类引用；`CuriosEquipHandler` 仅在 `ModList.isLoaded("curios")` 时由 tizMod 注册——生产未装 Curios 不加载相关类，不 NoClassDefFound。
- 生产环境必须装 Curios mod（前置），dev 下游无 Curios 时该检测自动跳过。

## Curios 方法级功能 + agent/ASM 持续效果关闭（2026-09-01 二轮）

**方法级识别**：检测 ICurioItem 覆写 → CURIO_TICK(持续效果)/CURIO_ATTRIBUTES(属性)/CURIO_ON_EQUIP(装备)/CURIO_ON_UNEQUIP(卸下)/CURIO_BREAK(损坏)。所有注册槽位饰品默认给 CURIO_TICK 开关。`CurioBridge.detectFeatures`（getMethod + declaringClass != ICurioItem）。

**关闭分层**：
- 属性加成：`CurioAttributeModifierEvent.clearModifiers()`（per-player 精确，事件层，无需 mixin）。
- 持续效果（curioTick）：**agent/ASM** 改写 CuriosEventHandler 的 `ICurio.curioTick(SlotContext)` 调用点（`agent/src/CuriosTickTransformer`，按调用指令匹配不依赖 lambda 名），插入 `CuriosAgentHooks.shouldSkipCurioTick`（per-player 跳过，饰品留在槽里效果停）。
- 整体禁用：CurioEquipEvent cancel（per-player）。

**关键坑（都会翻车）**：
- mixin 第三方类：@Mixin 必须 value=类（public 类不能用 targets）；@Redirect/@Inject 第三方方法需 `remap=false`（无 obfuscation 映射）；lambda 方法（lambda$tick$34）编译 processor 找不到（require=0 也运行时不会注入）→ **mixin 第三方不可靠**。
- VTableReplace 只操作 vtable（类方法，沿 superclass 链定位），**无法触及接口 default 方法**（curioTick 在 itable）→ 全局废除 curioTick 不可行。
- agent 生产 self-attach 不稳定（日志 Agent 加载失败降级）→ CuriosTickTransformer 可能不注册，持续效果关闭失效（需先修 agent 加载）。

**Curios 检测 API**：`CuriosApi.getItemStackSlots(ItemStack, boolean)` 非空=注册槽位（比 registered_curios tag 可靠，tag 是代码动态维护）。CurioEquipEvent 有 getStack()/getEntity()（LivingEvent）。**SlotContext 无 stack**（要 entity+identifier+index 反查：CuriosApi.getCuriosInventory(entity).getStacksHandler(id).get().getStacks().getStackInSlot(index)）。
