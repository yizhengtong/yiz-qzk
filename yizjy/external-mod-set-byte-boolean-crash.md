---
name: external-mod-set-byte-boolean-crash
description: 第三方 Boss 模组的 SynchedEntityDataMixin 在拦截 set() 时把 Byte 通道写成 Boolean（或反之），yizmodqzk 死亡链走 SynchedEntityData.set 触发它 → Byte→Boolean ClassCastException；修复=死亡链改用 DirectHealthFallback 直写绕开 set
metadata:
  type: project
---

# 第三方 SynchedEntityDataMixin 触发 Byte→Boolean 崩溃（2026-08-24 实锤）

## 现象

`ClassCastException: java.lang.Byte cannot be cast to java.lang.Boolean`，位置在原版读 boolean 同步字段时：
- `Entity.saveWithoutId`（m_20151_，存盘时）
- `Entity.isNoGravity`（m_20068_，玩家 tick 时）

崩在 vanilla 代码、`Suspected Mods: NONE`，换掉 Boss 模组（梦幻终焉/Ashes/omnimobs）换一个崩一个，看似"无解"。

## 根因

第三方 Boss 模组（village_mod 大贤者 / omnimobs / Ashes 等）的 `SynchedEntityDataMixin` 在拦截
`SynchedEntityData.set()` 时做自己的"flag 操作"，把 `DATA_SHARED_FLAGS_ID`（Byte 通道）写成 Boolean
（或反过来），导致后续 vanilla 读 boolean/byte 通道时 `(Boolean)`/`(Byte)` 强转炸。

**触发点**：yizmodqzk 的死亡链走 `SynchedEntityData.set()`，正好踩中第三方 mixin：
- `EntityASMUtil.finishDeathblow` 的 `target.setHealth(-Inf)` + `setHealthDelta(-Inf)`
- `DeathMarkerAccessor.setBool` 的 `entityData.set(acc, value)`
- `DynamicHealthAccessor.tamper` 的 `entityData.set(slot.death(), true)`

（注释里作者早记录过同款："补 hurt 会触发 SynchedEntityDataMixin 的 flag 操作，把 DATA_SHARED_FLAGS_ID(Byte) 写成 Boolean"。）

## 修复

死亡链所有写实体同步字段的地方，**改用 `DirectHealthFallback` 直写**（内部走 `DataItem.setValue`，
不触发 `SynchedEntityData.set`，也就不触发第三方 mixin）：
- `finishDeathblow` 用 `setFloatChannelValue(HealthChannels.getDeltaHealth()/VANILLA_HEALTH_ACCESSOR, ...)`
- `DeathMarkerAccessor.setBool` / `DynamicHealthAccessor.tamper` 用 `setBooleanChannelValue(...)`

## 补漏（2026-08-26，waifu_of_god 触发）

- 上轮只改了死亡链末端的 `finishDeathblow`/`DeathMarkerAccessor`/`DynamicHealthAccessor`，但 **DreamDelta 压 delta 和涨跌多空扣血**的每 tick 路径仍走 `SynchedEntityData.set()`，改 `waifu_of_god`（EnderGirl2Entity）血量归 0 时触发其 mixin → 渲染读 `Entity.m_20151_`（isInvisible，DATA_SHARED_FLAGS_ID Byte）时 Byte→Boolean 崩溃。
- 补改 5 处为 `DirectHealthFallback.setFloatChannelValue` 直写：
  - `EntityASMUtil.setHealthDelta`（原 `bridge.yizmodqzk$setHealthDelta` → set）
  - `EntityASMUtil.addDelta` 的 delta 累加 + 遍历 Float 通道 set
  - `EntityASMUtil.modifyHealth` 治疗方向遍历 set
  - `LivingEntityMixin.yizmodqzk$onDie` 清 delta 的 set
- 教训：**凡是对第三方实体写同步字段，无论死亡链还是每 tick 的 delta/扣血/治疗，一律直写绕开 set**，别只盯着死亡链末端。
- **再补漏（2026-08-26 三修）**：死亡链 `clearMobGoalsSafely` 里的 `mob.setNoAi(true)` 也走 `SynchedEntityData.set`（DATA_LIVING_ENTITY_FLAGS Byte），触发 waifu_of_god mixin → 玩家 tick 读 `isNoGravity`（m_20068_，DATA_SHARED_FLAGS_ID Byte）时 Byte→Boolean 崩溃。修复=去掉 setNoAi（goals/target 已清空，noAi 冗余）。教训：死亡链里连 vanilla 的 setNoAi/setTarget 这类「看似无害」的 set 都要审，第三方 mixin 拦截的是**所有** set 调用，不止改血那几条。

## 核心教训

对**第三方 Boss 实体**做死亡/移除/判死操作时，**不要走 `SynchedEntityData.set()`**（会触发第三方 mixin 的
类型错乱的 flag 操作），一律用 `DirectHealthFallback` 直写 `DataItem.value` 绕开 set。这条对所有"改第三方实体"的代码都适用。

相关：[[entity-presence-hardening-1-20-1]] [[hidden-class-health-tamper]]
