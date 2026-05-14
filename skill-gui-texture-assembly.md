# GUI 纹理积木块拼装技能

## 基本原则（严格禁止）

- 禁止移动、删除、重命名 `GUI/description/` 目录下的任何源文件
- 禁止修改源文件的像素内容
- 需要使用时，**必须复制源文件到工作目录**，在工作副本上进行删减、重命名、裁剪、拼接等操作

---

## 资源文件

### 源文件目录

```
GUI/description/
```

### 纹理积木块清单

| 文件名 | 大小 | 用途 |
|--------|------|------|
| `player_slots_9x4.png` | 162×76 | 玩家背包槽位群（9列×4行：物品栏3行+快捷栏1行） |
| `slot_default.png` | 18×18 | 标准物品槽最小单元（内含1px边框，中心16×16为物品渲染区） |
| `border_corner_tl.png` | 5×5 | 窗口边框左上角 |
| `border_corner_tr.png` | 5×5 | 窗口边框右上角 |
| `border_corner_bl.png` | 5×5 | 窗口边框左下角 |
| `border_corner_br.png` | 5×5 | 窗口边框右下角 |
| `border_edge_top.png` | 1×5 | 顶部边框横条（水平平铺） |
| `border_edge_bottom.png` | 1×5 | 底部边框横条（水平平铺） |
| `border_edge_left.png` | 5×1 | 左边框竖条（垂直平铺） |
| `border_edge_right.png` | 5×1 | 右边框竖条（垂直平铺） |
| `fill_white.png` | 1×1 | 背景填充（灰色 c6c6c6，平铺铺满内部区域） |
| `split_bar.png` | 1×14 | 横格分隔条（水平平铺，分隔容器槽区与背包槽区） |

### 翻译表位置

翻译表定义在 `GUI纹理拆解计划表.md` 中，JSON 格式索引所有积木块的名称、路径、尺寸和来源。

---

## 纹理拼装思路

### 总体结构

所有容器类 GUI 由以下五层叠加构成（从下到上）：

```
第1层：fill_white 背景填充        → 铺满内容区
第2层：border 边框（四边+四角）    → 窗口框架
第3层：容器自己的槽位（slot_default）→ 3×9 等自定义布局
第4层：split_bar 分隔条           → （可选）分隔容器区和背包区
第5层：player_slots_9x4 玩家背包   → 所有 GUI 通用的底部大块
```

### 窗口尺寸计算

```
内容区宽度  = 9列 × 18px = 162
总窗口宽度  = 5px(左边框) + 162 + 5px(右边框) = 172

内容区高度  = 容器行数 × 18 + split_bar(14) + 玩家背包(76)
总窗口高度  = 5px(上边框) + 内容区高度 + 5px(下边框)
```

### 示例：箱子 GUI（TestChestScreen）

```
总窗口 172×154：
┌─ TL ── top_edge(平铺) ── TR ─┐  ← 5px
│  ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░  │  ← 18px  箱子槽区
│  ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░  │  ← 18px  (slot_default × 27)
│  ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░  │  ← 18px
├────── split_bar ──────────────┤  ← 14px
│       player_slots_9x4        │  ← 76px  玩家背包
└─ BL ── bottom_edge ── BR ────┘  ← 5px
```

---

## ⚠ 核心：坐标系统（最容易出错的地方）

### 问题根源

Minecraft 的 `AbstractContainerScreen.isHovering()` 判断鼠标点击命中时，起点为 `(slot.x - 1, slot.y - 1)` 而不是 `(slot.x, slot.y)`：

```java
// AbstractContainerScreen 第 637-650 行
protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
    mouseX -= leftPos;
    mouseY -= topPos;
    return mouseX >= (double)(x - 1)        // ← 命中盒起点 = slot.x - 1
        && mouseX < (double)(x + width + 1)
        && mouseY >= (double)(y - 1)        // ← 命中盒起点 = slot.y - 1
        && mouseY < (double)(y + height + 1);
}
```

### 三条坐标路径

```
renderBg 绘制槽位纹理（slot_default / player_slots_9x4）：
  isHovering 命中盒起点 = slot.x - 1       ← 纹理必须从这里开始画
  错误画法                = slot.x           ← 直接画 slot.x 会导致右下多 1px

renderSlot 渲染物品 / renderSlotHighlight 悬停高亮：
  绘制在 (slot.x, slot.y)
  → 经过 pose.translate(leftPos, topPos) 后，绝对坐标 = (leftPos + slot.x, topPos + slot.y)

isHovering 点击检测：
  鼠标绝对坐标减 leftPos/topPos 后，判断范围：(slot.x-1, slot.y-1) ~ (slot.x+16, slot.y+16)
```

### 关键公式（必须遵守）

```
slot_background 纹理绘制起点 = leftPos + slot.x - 1     ← 永远用这个公式
slot_background 纹理绘制终点 = leftPos + slot.x + 17    (= leftPos + slot.x - 1 + 18)
物品渲染起点                = leftPos + slot.x           (物品 16×16，在槽位纹理内部居中)
命中检测范围                = (slot.x-1, slot.y-1) ~ (slot.x+16, slot.y+16)
```

### renderBg 中的实现

```java
// 背景填充 — 仅填充内容区内部
drawTiled(graphics, TEX_FILL, x + BORDER, y + BORDER, CONTENT_W, TOTAL_H - BORDER * 2, 1, 1);

// 边框 — 四边平铺 + 四角
drawTiled(graphics, TEX_TOP,    x + BORDER, y,               CONTENT_W, BORDER, 1, 5);
drawTiled(graphics, TEX_BOTTOM, x + BORDER, y + TOTAL_H - BORDER, CONTENT_W, BORDER, 1, 5);
drawTiled(graphics, TEX_LEFT,   x,          y + BORDER,      BORDER, TOTAL_H - BORDER * 2, 5, 1);
drawTiled(graphics, TEX_RIGHT,  x + TOTAL_W - BORDER, y + BORDER, BORDER, TOTAL_H - BORDER * 2, 5, 1);
drawTex(graphics, TEX_TL, x, y, 5, 5);
drawTex(graphics, TEX_TR, x + TOTAL_W - BORDER, y, 5, 5);
drawTex(graphics, TEX_BL, x, y + TOTAL_H - BORDER, 5, 5);
drawTex(graphics, TEX_BR, x + TOTAL_W - BORDER, y + TOTAL_H - BORDER, 5, 5);

// 容器槽位 — ⚠ 必须偏移 -1px 对齐命中盒
for (int row = 0; row < CHEST_ROWS; row++) {
    for (int col = 0; col < COLS; col++) {
        drawTex(graphics, TEX_SLOT,
            x + BORDER + col * SLOT - 1,   // ← -1px 偏移
            chestY + row * SLOT - 1,        // ← -1px 偏移
            18, 18);
    }
}

// 分隔条
drawTiled(graphics, TEX_SPLIT, x + BORDER, splitY, CONTENT_W, SPLIT_H, 1, 14);

// 玩家背包 — ⚠ 必须偏移 -1px
drawTex(graphics, TEX_SLOTS, x + BORDER - 1, playerY - 1, 162, 76);
```

---

## 容器点击修复

### 问题

客户端用 `mc.setScreen()` 直接打开的容器使用 `containerId = -1`，服务端不认此 ID，导致所有槽位点击被丢弃。

### 修复方式

在单机模式（开发环境）下，通过集成服务端打开容器获取有效 containerId：

```java
// 通过集成服务端分配真实 containerId
MinecraftServer server = mc.getSingleplayerServer();
if (server != null) {
    ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
    if (serverPlayer != null) {
        serverPlayer.openMenu(new SimpleMenuProvider(
            (id, playerInv, p) -> new TestChestMenu(id, playerInv),
            Component.literal("Test Chest")
        ));
    }
}
```

服务端分配真实 `containerId` → 发送 `ClientboundOpenScreenPacket` → 客户端自动打开对应 Screen → 所有点击正常发往服务端处理。

### 槽位坐标定义

```java
// TestChestMenu 中的槽位坐标（GUI 相对坐标）
// 这些坐标与 isHovering 的 (slot.x-1, slot.y-1) 配合使用

// 容器槽位（3×9）：起始 y = BORDER(5)
addSlot(new Slot(chestInv, index,
    BORDER + col * SLOT,       // slot.x = 5 + col*18
    BORDER + row * SLOT));     // slot.y = 5 + row*18

// 玩家物品栏（跳过盔甲栏，从物品栏开始）
addSlot(new Slot(playerInv, index,
    BORDER + col * SLOT,
    PLAYER_INV_Y + row * SLOT));  // PLAYER_INV_Y = 73

// 快捷栏
addSlot(new Slot(playerInv, index,
    BORDER + col * SLOT,
    HOTBAR_Y));                    // HOTBAR_Y = 131
```

---

## 注册流程（MenuType + Screen）

```java
// 1. 注册 MenuType（ModMenus.java，mod 总线）
public static final DeferredRegister<MenuType<?>> MENUS =
    DeferredRegister.create(BuiltInRegistries.MENU, MODID);
public static final Supplier<MenuType<TestChestMenu>> TEST_CHEST =
    MENUS.register("test_chest", () -> new MenuType<>(TestChestMenu::new, FeatureFlags.DEFAULT_FLAGS));
public static void register(IEventBus modEventBus) { MENUS.register(modEventBus); }

// 2. 绑定 Screen（tizModClient.java，RegisterMenuScreensEvent）
event.register(ModMenus.TEST_CHEST.get(), TestChestScreen::new);

// 3. 打开容器时通过服务端分配 containerId
serverPlayer.openMenu(new SimpleMenuProvider(
    (id, inv, p) -> new TestChestMenu(id, inv),
    Component.literal("Title")
));
```

---

## 渲染辅助方法

```java
// 直接绘制纹理（不缩放）
private void drawTex(GuiGraphics g, ResourceLocation tex, int x, int y, int texW, int texH) {
    g.blit(tex, x, y, 0.0f, 0.0f, texW, texH, texW, texH);
}

// 平铺/拉伸绘制纹理
private void drawTiled(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
    if (w <= 0 || h <= 0) return;
    g.blit(tex, x, y, 0.0f, 0.0f, w, h, texW, texH);
}
```
