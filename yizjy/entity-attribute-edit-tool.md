---
name: entity-attribute-edit-tool
description: "实体属性编辑工具（yizxianmod:entity_attribute_editor）的实现方案与关键坑：右键任意实体打开原版容器界面编辑 yizmodqzk 15 属性（含绝妄生机率/绝妄生机时间/最初梦幻）；本模组实体受保护写入、其他实体反射按需注入不套保护"
metadata:
  type: project
---

# 实体属性编辑工具（2026-08-05 落地）

物品 `yizxianmod:entity_attribute_editor`（模型复用木棍，创造标签页「召唤物」）。**手持右键任意 LivingEntity** 打开原版容器界面（generic_54 箱子背景 176×222），点选 12 个 yizmodqzk 属性 + 滚轮增减 + 「应用」生效。

## 结构（下游 yizxian1.21.1）
- `item/EntityAttributeEditorItem` — `interactLivingEntity` 钩子（`Player.interactOn` 在实体端 interact 返回 PASS 时调用，对任意 LivingEntity 生效），服务端 `player.openMenu(provider, buf -> buf.writeInt(target.getId()))`
- `menu/EntityAttributeEditMenu` — 只存目标实体 **id**（非 UUID），经 `IContainerFactory` 的 buf 传给客户端；槽位仅玩家背包（对齐 generic_54）
- `client/screen/EntityAttributeEditScreen` — 复用原版 generic_54 背景，属性列表点选 + 滚轮增减 + 原版 Button「应用」
- `network/C2SEntityAttributeEditPayload` — `(targetId:int, attrId:String, value:double)`，服务端校验距离后写入

## 写入策略（与保护范围一致）
- 目标是 **YizxianMob**（本模组实体）→ `EntityAttributeGate.set`（`prot_` 前缀 + 鉴权 + mixin 防移除）
- 目标是 **其他实体** → `ItemAttributeHandler.setEntityAttribute`（`entity_` 前缀，普通写入**不套保护**）；若该实体未挂 yizmodqzk 属性，则**反射往 `AttributeMap.attributes` map 注入 AttributeInstance**（`ensureAttribute`），当次存活期间生效

## 关键坑（踩过）
1. **Screen 构造器里 `minecraft` 为 null**：`Screen.minecraft` 在 `init()` 才赋值，任何用 `minecraft.level` 的逻辑（如读实体属性）必须放 `init()`（或懒加载），构造器调用会 NPE。曾因构造器里 `loadCurrentValues()` 触发 `Failed to handle advanced open screen`
2. **按 id 查实体**：`Level.getEntity(int)` 按实体 id；`Level.getEntities()` 无参是 protected；`Level.getAllEntities()` 是 ServerLevel 专属，客户端没有。UUID→实体没有公开 API
3. **1.21.1 `mouseScrolled` 是 4 参数** `(double mouseX, double mouseY, double scrollX, double scrollY)`，垂直滚动量是第 4 个
4. **`Attribute` 接口没有 `getMinValue/getMaxValue`**（在 `RangedAttribute` 子类）
5. **UUID codec**：`ByteBufCodecs` 无 `UUID`，用 `net.minecraft.core.UUIDUtil.STREAM_CODEC`
6. **`interactLivingEntity` 只在实体端 `interact()` 返回 PASS 时触发**；对挤奶/喂食等消耗型实体不触发（编辑工具对象是怪/玩家，无碍）
7. **反射注入是内存态**：当次存活生效、不持久化（实体重载/重生后丢失）、客户端不同步（界面不刷新显示 0，服务端伤害计算生效）
8. **属性列表会盖住「应用」按钮（血泪坑）**：`mouseClicked` 先遍历属性行拦截点击，按钮若落在属性行区域则永远点不到 → C2S 包不发 → 属性看似不生效且服务端无 `[AttrEdit]` 日志。属性增至 15 个后列表向下延伸盖住按钮，已改**两列布局 + 按钮放顶部**解决。**给列表加项时必须复查按钮/列表几何不重叠**

## 关联
- 受保护设施与辖界者接入：[[entity-attribute-gate]]
- 前置库给 `PLAYER` 挂了全部 15 属性（含绝妄生机率/绝妄生机时间/最初梦幻）→ 玩家也可被编辑（普通写入）
