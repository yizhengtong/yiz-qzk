# GUI 纹理拆解计划表

## 核心工作守则

**`GUI/description/` 目录下的所有图片文件为原本样式（原件），严格执行以下规则：**

1. 禁止移动、删除、重命名原件
2. 禁止修改原件的像素内容
3. 需要使用时，**必须复制原件到工作目录**，在工作副本上进行删减、重命名、裁剪、拼接等任何操作

---

## 思路

玩家背包的 36 格物品槽 + 9 格快捷槽（9行×4列）是所有 GUI 中永远不变的通用大积木块。以此为基础，其他 GUI（工作台、箱子、铁砧等）都是在这个块的上方叠加额外区域。

---

## 玩家背包积木块

### 核心大块

图片名称：player_slots_9x4
工作名称：玩家背包槽位群（36格+9格）
纹理大小：162×76
用途：**所有 GUI 通用大积木块**。包含物品栏3行(27格)+快捷栏1行(9格)，9列×4行的固定格子阵列。在任何需要显示玩家背包的界面上方加额外区域即可组成完整 GUI。

结构说明：
```
┌──┬──┬──┬──┬──┬──┬──┬──┬──┐
│  │  │  │  │  │  │  │  │  │  ← 物品栏第1行 (9格)
├──┼──┼──┼──┼──┼──┼──┼──┼──┤
│  │  │  │  │  │  │  │  │  │  ← 物品栏第2行 (9格)
├──┼──┼──┼──┼──┼──┼──┼──┼──┤
│  │  │  │  │  │  │  │  │  │  ← 物品栏第3行 (9格)
├──┼──┼──┼──┼──┼──┼──┼──┼──┤
│  │  │  │  │  │  │  │  │  │  ← 快捷栏 (9格)
└──┴──┴──┴──┴──┴──┴──┴──┴──┘
```

### 附加积木块

图片名称：slot_default
工作名称：标准物品槽
纹理大小：18×18
用途：构成格子阵列的最小单元，也可单独使用

图片名称：border_corner_tl
工作名称：左上角边框
纹理大小：5×5
用途：GUI窗口左上角

图片名称：border_corner_tr
工作名称：右上角边框
纹理大小：5×5
用途：GUI窗口右上角

图片名称：border_corner_bl
工作名称：左下角边框
纹理大小：5×5
用途：GUI窗口左下角

图片名称：border_corner_br
工作名称：右下角边框
纹理大小：5×5
用途：GUI窗口右下角

图片名称：border_edge_top
工作名称：顶部边框横条
纹理大小：1×5
用途：窗口上边框，水平平铺

图片名称：border_edge_bottom
工作名称：底部边框横条
纹理大小：1×5
用途：窗口下边框，水平平铺

图片名称：border_edge_left
工作名称：左边框竖条
纹理大小：5×1
用途：窗口左边框，垂直平铺

图片名称：border_edge_right
工作名称：右边框竖条
纹理大小：5×1
用途：窗口右边框，垂直平铺

图片名称：fill_white
工作名称：纯白填充
纹理大小：1×1
用途：GUI背景面板填充，平铺铺满内部区域

图片名称：split_bar
工作名称：横格条（容器与玩家背包分隔带）
纹理大小：1×14
用途：水平平铺，分隔容器的槽区（上方）和玩家背包槽区（下方）

---

## 翻译表（完整）

```json
[
  {
    "name": "player_slots_9x4",
    "path": "GUI/description/player_slots_9x4.png",
    "width": 162,
    "height": 76,
    "desc": "玩家背包槽位群9列×4行",
    "source": "player_inventory"
  },
  {
    "name": "slot_default",
    "path": "GUI/description/slot_default.png",
    "width": 18,
    "height": 18,
    "desc": "标准物品槽位",
    "source": "player_inventory"
  },
  {
    "name": "border_corner_tl",
    "path": "GUI/description/border_corner_tl.png",
    "width": 5,
    "height": 5,
    "desc": "窗口边框左上角",
    "source": "container"
  },
  {
    "name": "border_corner_tr",
    "path": "GUI/description/border_corner_tr.png",
    "width": 5,
    "height": 5,
    "desc": "窗口边框右上角",
    "source": "container"
  },
  {
    "name": "border_corner_bl",
    "path": "GUI/description/border_corner_bl.png",
    "width": 5,
    "height": 5,
    "desc": "窗口边框左下角",
    "source": "container"
  },
  {
    "name": "border_corner_br",
    "path": "GUI/description/border_corner_br.png",
    "width": 5,
    "height": 5,
    "desc": "窗口边框右下角",
    "source": "container"
  },
  {
    "name": "border_edge_top",
    "path": "GUI/description/border_edge_top.png",
    "width": 1,
    "height": 5,
    "desc": "顶部边框横条（水平平铺）",
    "source": "container"
  },
  {
    "name": "border_edge_bottom",
    "path": "GUI/description/border_edge_bottom.png",
    "width": 1,
    "height": 5,
    "desc": "底部边框横条（水平平铺）",
    "source": "container"
  },
  {
    "name": "border_edge_left",
    "path": "GUI/description/border_edge_left.png",
    "width": 5,
    "height": 1,
    "desc": "左边框竖条（垂直平铺）",
    "source": "container"
  },
  {
    "name": "border_edge_right",
    "path": "GUI/description/border_edge_right.png",
    "width": 5,
    "height": 1,
    "desc": "右边框竖条（垂直平铺）",
    "source": "container"
  },
  {
    "name": "fill_white",
    "path": "GUI/description/fill_white.png",
    "width": 1,
    "height": 1,
    "desc": "背景填充（平铺铺满内部区域）",
    "source": "container"
  },
  {
    "name": "split_bar",
    "path": "GUI/description/split_bar.png",
    "width": 1,
    "height": 14,
    "desc": "横格分隔条（水平平铺，分隔容器区与背包区）",
    "source": "container"
  }
]
```

---

## 坐标系统核心规则（必须遵守）

### 现象

Minecraft 的 `AbstractContainerScreen.isHovering()` 判断点击命中时，起点是 `(slot.x - 1, slot.y - 1)` 而不是 `(slot.x, slot.y)`：

```java
// AbstractContainerScreen 第 637-650 行
private boolean isHovering(Slot slot, double mouseX, double mouseY) {
    return this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
}
protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
    int i = this.leftPos;
    int j = this.topPos;
    mouseX -= (double)i;
    mouseY -= (double)j;
    return mouseX >= (double)(x - 1)        // ← 命中盒起点 = slot.x - 1
        && mouseX < (double)(x + width + 1)
        && mouseY >= (double)(y - 1)        // ← 命中盒起点 = slot.y - 1
        && mouseY < (double)(y + height + 1);
}
```

### 三条坐标路径对比

```
renderBg 绘制槽位纹理（slot_default / player_slots_9x4）：
  isHovering 命中盒起点  = slot.x - 1       ← 纹理应该从这里开始画
  错误画法                 = slot.x           ← 直接画会导致右下多 1px
  修正后                   = slot.x - 1

renderSlot 渲染物品 / renderSlotHighlight 悬停高亮：
  都在 (slot.x, slot.y) 绘制
  → 经过 pose.translate(leftPos, topPos) 后，绝对坐标 = (leftPos + slot.x, topPos + slot.y)

isHovering 点击检测：
  鼠标绝对坐标减 leftPos/topPos 后，判断范围：(slot.x-1, slot.y-1) ~ (slot.x+16, slot.y+16)
```

### 关键公式

```
slot_background 纹理绘制起点 = leftPos + slot.x - 1     ← 永远用这个公式
slot_background 纹理绘制终点 = leftPos + slot.x + 17    (= leftPos + slot.x - 1 + 18)
物品渲染起点                = leftPos + slot.x           (物品 16×16，在槽位纹理内部居中)
命中检测范围                = (slot.x-1, slot.y-1) ~ (slot.x+16, slot.y+16)
```

**核心原则**：所有槽位背景纹理必须从 `(slot.x - 1, slot.y - 1)` 开始绘制（不是 `slot.x`），才能与 `isHovering` 点击命中盒完美重叠。

### 代码中的体现

```java
// slot_default：绘制在 (slot.x-1, slot.y-1)，偏移 -1px 对齐命中区
drawTex(graphics, TEX_SLOT,
    x + BORDER + col * SLOT - 1,
    chestY + row * SLOT - 1,
    18, 18);

// player_slots_9x4：整块纹理偏移 -1px
drawTex(graphics, TEX_SLOTS, x + BORDER - 1, playerY - 1, 162, 76);
```

### 容器点击修复（containerId）

客户端直接 `mc.setScreen()` 打开的容器使用 `containerId = -1`，服务端不认这个 ID，所有槽位点击都会被丢弃。

**修复方式**：在单机模式下通过集成服务端打开容器，获得有效 containerId：

```java
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

这会让服务端分配真实 containerId、发送 `ClientboundOpenScreenPacket`，客户端收到后自动打开对应 Screen。此后所有槽位点击发往服务端处理，服务端同步结果回客户端。

---

## 后续

| 步骤 | 内容 | 状态 |
|------|------|------|
| 1 | 拆解 player_slots_9x4（玩家槽位群162×76） | ⬜ |
| 2 | Slot点击偏移分析：确认根因为 isHovering 从 slot.x-1 开始判断 | ✅ |
| 3 | 修复Slot点击偏移：纹理绘制偏移 -1px，对齐 isHovering 命中盒 | ✅ |
| 4 | 将坐标计算规则和容器点击修复写入计划书 | ✅ |
| 5 | 你指定下一类 GUI 来拆 | ⬜ |
