---
name: entity-probe-gui
description: 实体探查镜（1.20.1 yizxianmod，右键实体开 GUI）落地现状：元素树编辑器/真槽位换装/血蓝条/A1 实体头像与 A1.1 投影板/布局文件保存语义与全部已踩坑。GUI 方法论见 guiskill skill modules/04+05。做探查镜后续功能前必读。
metadata:
  type: project
---

# 实体探查镜 GUI（yizxianmod 1.20.1）落地现状与坑

> 方法论语境 = guiskill skill（`~/.proma/agent-workspaces/yiz/skills/gui/`），三定则 + `modules/04-probe-gui-implementation.md` + `modules/05-input-and-depth.md`。**本文件存代码层事实与坑**，与 skill 互相引用（联动）。

## 功能与结构（2026-09-10 更新）
- 物品 `yizxianmod:entity_probe`（EntityProbeItem）：右键任意 LivingEntity → 服务端 openMenu（`EntityProbeMenu`）+ S2C `S2CEntityProbeTargetPayload` 推目标 id（**1.20.1 ServerPlayer 无带数据 openMenu**，勿再走 extraData）。GUI = `EntityProbeScreen`（AbstractContainerScreen）。
- 元素：0-背包面板 + A 卡(A1~A5 ×2，A6 原尺寸居中) + A6 五装备格。几何规格集中 `EntityProbeGuiSpec`（A 卡显示 2× =192×344，A_OY=-(344+14) 整卡在 0-背包上方）。A 底/A1~A5 ×2、A6 保持 90×18，为**用户最终确认的默认**。
- **元素树编辑器**（EntityProbeScreen 内）：Shift+DEL 编辑；左键拖元素；Shift+点=下钻子扩展(A→A1..A6；A6→c0..c4)；ESC 回退/退出；顶栏 保存/重置。**编辑态屏蔽一切下层点击（mouseReleased 也要拦）**。
- **保存语义**：仅「保存」按钮落盘 `run/config/yizxianmod/gui_layouts.json`；ESC/关闭丢弃 → 打开时 `GuiLayoutConfig.reload()`。不要再改回自动保存。布局存 guis.id.nodes.{元素}.{x,y,scale}。
- A6 换装=真槽：`EntityEquipContainer extends SimpleContainer` 包装目标实体 5 装备槽读写；点击/Shift/数字键全走 vanilla（**勿再用 1 格 dummy 容器**，会越界崩溃）。Shift 快捷只存盔甲→对应空装甲槽，其它类型不做（否则挪第1格/翻倍）。
- 血/蓝条：空条整条 + 填充 enableScissor 裁百分比；条内文本 0.85× 叠字无底色。蓝量服务端 ManaTracker 持有 → 打开先 S2C 快照 + C2S 心跳每 20tick 回传。
- **A1 = 实时实体头像**（2026-09-10）：圆框内用 `InventoryScreen.renderEntityInInventoryFollowsMouse` 渲染被探查实体（跟随鼠标转向、按包围盒缩放、scissor 裁剪在框内）；客户端取不到目标实体时只画空框；渲染异常只记一次日志。
- **A1.1 = 整体投影板**（2026-09-10）：点击 A1 切换显隐（默认隐藏）；纹理 `element_a1_1.png` 112×144，与 A 面板统一 ×2 显示（224×288）；默认在 A1 左侧留 8px、顶部对齐（A 局部 x=-216,y=16）；纳入元素树可拖动/保存；板内渲染实体整体模型（宽高双向约束缩放）。

## 已踩必记的坑（未来新 GUI 直接复用）
1. `Slot.x/y` final → 真实槽坐标 Menu 构造时定死；运行时移动用**反射 set**（applyLiveSlotLayout 反射改，供拖槽位宿主时热点跟随）。
2. 槽位纹理 blit 用 `槽坐标-1` 对齐（gui-bg-offset 的 1px 坑）；否则槽格与热点差 1px。
3. 快捷栏与 27 主格间有 **4px** 间隔（用户尺寸，勿贴死）。
4. `tick()` 被 AbstractContainerScreen 占用 → 用 `containerTick()`。
5. `instanceof ServerPlayer` 与 `ctx.getSender()`（已 ServerPlayer）类型冲突编译错 → 去掉模式匹配直接强类型。
6. **缩放已关闭**（用户嫌 Bug 多）：scale 一律 1，勿再启用；若做需子树级联+槽位同步的真坐标缩放。
7. 截图/交付默认：清 `run/config/yizxianmod/gui_layouts.json` 即用代码默认；默认值=EntityProbeGuiSpec+Screen NODES 里烧死的常量。
8. **`super.mouseClicked` 恒返回 true**（2026-09-10）：自定义点击判定（A1 切换/A2 闪白/整窗拖动）必须放在它**之前**，否则全是死代码（A1 点击无效即此因）；整窗拖动区域宽度同步修正 96→192，并用 `hoveredSlot == null` 让位真实槽位。详见 skill `modules/05-input-and-depth.md` 坑 1。
9. **GUI z 越大越近**（2026-09-10）：`renderEntityInInventory*` 固定 z=50，A1.1 大 scale 下模型远端顶点 z<0，被投影板（z=0）按深度剔除 → 实体随鼠标转向“被盖住”缺块。修法=渲染前 `translate(0,0,+200)` 拉到板前。详见 skill 05 坑 2。

## A7 属性面板（2026-09-03 定稿规则）
- 本体按「初始 model ×2」：A7 板 90×49 → **180×98**，A 局部 (3,82)×2=(6,164)；A 底=A1~A5×2、A6 槽位保持原尺寸（槽位放大点不了，勿改）。
- 排布：**行主序**（左→右，换行下一行）、**固定行高 18**、两列(每列 90 宽)；每格 = **文本名 + 图标(32→16px)+ 数值**，图标与文本同行垂直居中；缺失属性**剔除、现有紧凑补位**（不留空洞）。
- 属性序：最大生命→护甲防御→攻击力→法防→暴率→暴伤→全能吸血→法强→最大法力；护甲/攻击对非本模组实体**回退原版 Attributes.ARMOR / ATTACK_DAMAGE**，其余仅本模组属性存在才显示。

## 真实生命“读透真值”（2026-09-03）
- 探查镜 A3 血条/条内数值/A7 生命全部改读 **`SecureHealthClosure.getHealth/getMaxHealth`**（真值）；写侧拦截(SecureHealthClosure gate/hurt 传导)不动。
- 其它模组只读真值：直接调上述两个静态方法；vanilla `getHealth()` 对本模组实体已透真(YizxianMob override)，`getMaxHealth()` **不能 override（1.20.1 该方法映射为 final，编译报“无法覆盖”）**，但真实上限每 tick 被镜像进 vanilla MAX_HEALTH 属性，故外部 getMaxHealth 读到也是真值。
- 联机真值：不新增网络；真实值镜像写回 DATA_HEALTH 随实体数据自动同步客户端。**依赖现有同步即可，勿加 HP 心跳包。**

## 现状/后续待办
- 已完成：GUI 显示、真槽换装、Shift 安全、血/蓝条数值、元素树编辑器(拖动/下钻/保存/重置)、布局默认烧录、**A1 实体头像 + A1.1 整体投影板（2026-09-10 用户确认）**。build+runClient 验证通过。
- 待办：A2 售卖槽后端、A5 技能纹理、实体描边（用户自行重做）、探索镜其它子扩展；原版 Warden 召唤问题(待排查，非本 GUI 引入)。

## 关联
- skill：guiskill `modules/04-probe-gui-implementation.md`（方法论）+ modules/01-03。
- 记忆：`container-menu-pitfalls`、`gui-bg-offset`、`hud-position-config`、`itemcfg-universal-item-config`。
