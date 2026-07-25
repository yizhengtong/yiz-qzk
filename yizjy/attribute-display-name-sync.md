---
name: attribute-display-name-sync
description: "改自定义属性显示名时必须同步 lang+ItemAttributeDisplay+EditableAttribute 三处，改前先 grep 三处当前值"
metadata:
  type: feedback
---

改属性显示名（如"吸血"→"全能吸血"）必须在三处同步：
1. `lang/zh_cn.json` — 原版属性 tooltip 的 `attribute.yizmodqzk.<id>` 条目
2. `ui/ItemAttributeDisplay.java` — 自定义属性 tooltip 的 `yiz("id", "显示名", ...)` 调用
3. `editor/EditableAttribute.java` — 属性编辑台列表的 `yiz("id", "显示名", ...)` 调用

这三处**历史不一致**（如 magic_damage：lang="法术提升"、ItemAttr="法术伤害增幅"、Editable="法术提升"），改前必须先 grep 三处当前值，再统一替换。用引号闭合 `"旧名"` 精确匹配（如 `"攻击强度"` 不会误匹配 `"攻击强度防御"`），避免子串误改。

下游装备的硬编码属性名（`AttributeScrollItem` 卷轴映射表、`GuinsooRagebladeItem`/`MeleeWeaponItem` tooltip）也可能引用旧称呼，改完三处后 grep 下游确认无遗留。

**Why:** 本次 15 个属性改名，因三处历史不一致（如 cooldown_reduction：ItemAttr="冷却缩减"/Editable="攻击间隔缩减"/lang="攻击间隔缩减"）踩了多轮；不 grep 直接改会漏掉不一致的旧名。

**How to apply:** 改属性称呼前，先 `grep -n "旧称呼"` 分别在 lang、ItemAttr、Editable 里确认当前值，用引号闭合 old_string 精确 Edit 三处同步；最后 grep 下游确认无遗留。
