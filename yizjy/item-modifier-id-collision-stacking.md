---
name: item-modifier-id-collision-stacking
description: "同一属性加到多件不同部位装备同时穿戴时累加丢失的坑：原版 AttributeMap 按 modifier id 去重，所有物品若共用固定 modifier id 会互相覆盖；修复与后续约束"
metadata:
  type: project
---

# 物品属性 modifier id 冲突 → 多件装备累加丢失

## 现象
同一 yizmodqzk 属性（如减伤）被添加到**多件不同部位装备**（盔甲槽 / 主副手）上，同时穿戴时属性**不累加**，只保留最后穿戴的一件的值（脱下某件还会连带移除其他件的同属性加成）。

## 根因
原版 `AttributeInstance.addModifier` 用 `modifierById.putIfAbsent(modifier.id())` **按 modifier id 去重**（同 id 二次 add 抛 `IllegalArgumentException`，走 `addOrUpdate` 则覆盖）。原版装备检测 `collectEquipmentChanges` 对每个穿戴槽位 `removeModifier(id)` 再 `addTransientModifier`。而所有物品给同一属性写入 `ATTRIBUTE_MODIFIERS` 组件时用的是**固定 id**：
- `ItemAttributeHandler.setVanillaModifier` → `yizmodqzk:item_<name>`
- `EditableAttribute.setAttr` → `yizmodqzk:attr_<name>`

同一属性在多件装备上 id 相同 → 原版 AttributeMap 只保留一件 → 累加丢失。

## 修复（2026-08-04 两轮，yiz1.21.1）
1. **modifier id 唯一化**：`setAttr` / `setVanillaModifier` 复用物品上该属性已有 id，无则生成物品唯一 id（idKey + 8 位随机 hex）。
2. **旧固定 id 迁移**（关键）：第一轮"复用已有 id"会复用到**历史版本写入的固定 id**（`attr_<name>` / `item_<name>`，无随机后缀），旧装备重设后仍冲突（实测胸甲45/头盔25/靴子25 只生效25）。第二轮：复用前先判断——若已有 id 的 path 等于旧固定格式（`legacyPath`），丢弃并生成新唯一 id。**用户重设一次即迁移**。
改了两处：`ItemAttributeHandler.setVanillaModifier`、`EditableAttribute.setAttr`。技能装载槽不受影响（`EquipmentAttributeSync` 每 tick 内部累加后挂单一 `sync_` modifier），无需改动。

## 后续约束（易复发的坑）
- **给物品写属性组件的 modifier id 必须每物品唯一且稳定**，禁止跨物品共用固定 id。下游直接 `ItemAttributeModifiers.builder()` 写固定 id 的装备（equipment/*）目前各用各的前缀不冲突，但若做"套装多部位共享固定 id"会复现此 bug。
- **id 必须稳定（复用已有/持久记录）**：不能每次 set 都新生成随机 id——物品组件每次变会让原版装备检测按新 id remove，找不到实体上旧 id，造成 modifier 残留（双倍）。一次性迁移旧 id 后即稳定；**穿戴中重设属性会残留旧 id**，应提醒玩家先脱下再改。
- 实体级 `setEntityAttribute` 每 tick remove+add 单 id，无此问题。
