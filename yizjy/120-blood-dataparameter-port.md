---
name: 120-blood-dataparameter-port
description: "1.20.1 移植关键差异：血量在 DATA_HEALTH_ID DataParameter 非普通字段、SynchedEntityData.itemsById 是 Int2ObjectMap 非数组；涨跌多空打不动部分实体的根因与 A/B/C 扫描扩展（DirectHealthFallback Map 修复/vanilla 通道直改/EntityHealthLocator DataParameter 探测）"
metadata:
  type: project
---

# 1.20.1 血量系统移植差异 + 扫描扩展（2026-08-09）

## 核心差异（1.21.1 → 1.20.1，涨跌多空打不动实体的根因）

1. **1.20.1 的 `LivingEntity` 没有 `health` 普通字段**——血量存在 `DATA_HEALTH_ID` DataParameter 通道（`entityData.get(DATA_HEALTH_ID)`）。1.21.1 才有 `health` 字段。
   - 症状：`EntityActuallyHurt` 静态块 `LivingEntity.class.getDeclaredField("health")` 抛 NoSuchFieldException → `catchSetTrueHealth` 回退 `setHealth`（仍可用，辖界者 override 兜底）。
2. **`SynchedEntityData.itemsById`：1.21.1 是 `DataItem[]` 数组，1.20.1 是 `Int2ObjectMap<DataItem<?>>`（fastutil Map）**。
   - `DirectHealthFallback` 数组强转 `(DataItem<?>[]) ITEMS_BY_ID.get(data)` 在 1.20.1 抛 ClassCastException 被静默吞掉 → **保底层直改 DataItem 完全失效**。
3. 血量存 DataParameter 的自研实体（血量不在普通字段）→ `EntityHealthLocator` 只扫普通 float/double/int/long 字段扫不到 → `applyPersistentDamage` 返回 false。
4. **`SynchedEntityData.defineId` 按类分配 id**（2026-08-30 实证，1.21.1 是全局 AtomicInteger、1.20.1 是 `ENTITY_ID_POOL: Object2IntMap<Class>` 每类独立计数，子类基于父类池 max+1）→ **不同类通道共享同一 id 值**（如 `Entity.DATA_AIR_SUPPLY_ID`=1 与辖界者某通道=1）。**DataItem 写入必须按 accessor 对象匹配（`acc == accessor`），不能用 `acc.getId()==accessor.getId()`**——按 id 匹配会跨类错位：AIR_SUPPLY 被写坏（getAirSupply 读 Byte → Byte→Integer 崩溃，所有辖界者族实体 tick/存档全崩）。参考 uom（com.mega.uom）`EntityActuallyHurt.catchSetTrueHealth`：`itemsById().get(index).getAccessor()` 取对象再写。**修复已落地（2026-08-30）**：DirectHealthFallback 7 处 + EntityHealthLocator 2 处 `getId()` 匹配全改对象匹配。

5. **多模组按 id 直写会写坏任意基础类型通道 → 泛化读守卫**（2026-08-30 生产实测三种崩溃）：① INT 通道被写坏成 Byte（AIR_SUPPLY/第三方 EVASION_TIME → Byte→Integer）；② BYTE 通道（FLAGS）被写坏成 Float（Float→Byte）；③ 任意数值通道类型错乱。**SynchedEntityDataMixin 加 `get(EntityDataAccessor)` HEAD 守卫**：对 INT/LONG/FLOAT/BYTE/BOOLEAN/STRING 序列化器，值类型与序列化器不匹配 → 修复为类型默认值（0/0L/0.0F/(byte)0/false/""），防 ClassCastException。用 MixinAccess.field 反射读 itemsById（不依赖 @Shadow/@Accessor SRG 字段名，生产安全）。
6. **HealthChannels 通道用手动固定 id（254/253/252），不走 defineId**（2026-08-30）：defineId 自动分配在 1.20.1 按类池 + 子类继承，多模组下无论何时触发（mixin 应用期/实体构造/FML 初始化）都参与 LivingEntity/Player 池计数 → 与 vanilla/Player 撞车（实测 Duplicate id 0 / id 38，实体无法生成/玩家登录失败）。手动 `new EntityDataAccessor<>(254, FLOAT)`（1.20.1 是两参构造 int+EntityDataSerializer，无 Class 参数）不参与 id 池 → 客户端/服务端一致。**LivingEntityMixin 的 delta 通道不能用静态字段 = HealthChannels.getDeltaHealth()（@Unique 静态字段合并进目标类 <clinit>，mixin 应用期提前触发 → id 0 冲突），改运行时方法**；并在 tizMod.commonSetup 预触发 getter。
7. **死亡链对第三方实体必须克制**（2026-08-30）：`finishDeathblow` 的全量通道清零（forEachFloatItem/zeroAllNumericItems）+ `forceRemoveDeep`（深层移除）对第三方 Boss 破坏其状态机/世界存储 → 维度切换卡住、玩家数据丢失。参考 uom（死亡=vanilla die+掉落）。**已落地：全量清零/forceRemoveDeep/markForceRemoveAllowed 只对自研实体**（`EntityASMUtil.isSelfMob`：类名 startsWith "net.minecraft.client.yiz.xian"）；第三方死亡链= catchSetTrueHealth + vanilla die + 掉落 + 动画。

## 已实施扩展（2026-08-09，1.20.1 yizmodqzk，编译+发布+runClient 进世界验证通过）

- **A. DirectHealthFallback Map 结构修复**：新增 `allDataItems(entity)` 统一按 `DataItem[]` 或 `Map.values()` 提取；`forEachFloatItem`/`applyToAllFloatItems` 改用它。保底层恢复生效。
- **B. 覆盖 vanilla DATA_HEALTH_ID 通道**：`DirectHealthFallback` 新增 `VANILLA_HEALTH_ACCESSOR` 常量 + `damageFloatChannel(entity, accessor, amount)`（绕过 `SynchedEntityData.set()` 限伤，直接改 DataItem）+ `damageVanillaHealth(entity, amount)`；`EntityASMUtil.addDelta` 第 3 步调它（对自定义 `set()` 限伤实体是主扣血路径）。`HealthChannelScanner` 暴露 `getVanillaHealthAccessor()`。
- **C. EntityHealthLocator 加 DataParameter 通道探测**：`HealthSlot` record 加 `kind` 字段（"field"/"accessor"，JSON 缓存兼容，缺省 "field"）；新增 `detectViaDataAccessor(entity)`（扫类层级静态 `EntityDataAccessor<Float>` 字段名含 health 不含 max，且通道值 ≈ getHealth 才缓存）；`locate` 顺序 bytecode → accessor → scan；`applyPersistentDamage`/`readLocated`/`writeLocated` 支持 accessor 槽走 DataItem 直改。

## 梦幻终焉（UomWither）生命值保护机制（研究结论，作防御设计参考）

5 层保护，技术栈与前置库同源（镜像）：
1. **血量存 DataParameter**：`getHealth()` override 直接 `return entityData.get(LivingEntity.DATA_HEALTH_ID)`，不读字段。
2. **自定义 `UomEntityData extends SynchedEntityData` 覆写 `set()` 限伤**：构造时 `this.entityData = new UomEntityData(this.entityData)` 替换；血量写入 `amount = min(maxDamagePertick, acceptDamage(amount))`（×0.4），低血锁 2.0 + 触发时停。**→ 必须绕过 set() 直改 DataItem（DirectHealthFallback.damageFloatChannel 正是为此）**。
3. **finalSkill 锁血**：getHealth()≤0 进 1860 tick 演出，期间返回 0.3999F（>0 不死）；isDeadOrDying/remove 全锁。
4. **每 10 tick 清 delta**：customServerAiStep 把外部注入 delta 拉回 0（delta 通道对其无效）。
5. **ASM 注入**：自有 GetHealthModifyMethodVisitor 对所有实体 getHealth 注入 special_getHealth（同 LivingHealthTransformer 思路）。
- 其自有血量定位法（可借鉴）：`EntityActuallyHurt.checkAndSave` 扫类层级静态 `EntityDataAccessor` 字段，谓词「字段名含 HEALTH 不含 MAXHEALTH」→ VarHandle 拿 accessor → `itemsById` 找 DataItem index 缓存 → `catchSetTrueHealth` 直改。**（1.20.1 移植 C 扩展正是仿此）**。

## 用户需求背景（2026-08-09）

- 「涨跌多空攻击深度太弱」= **强度不是数值**：指某些实体生命值完全改不了（非 20%→40% 这种数值）。
- 两个猜想均证实：①广度不够（只扫普通字段，不扫 DataParameter 通道）②目标藏血量（DataParameter/NBT/自写 set() 限伤）。

## 黎玄纪元穿透辖界者防御分析（2026-08-09，攻击方视角）

**黎玄纪元**（`net.mcreator.colossus`，MCreator 模组，`D:\桌面\.minecraft\...\mods\黎玄纪元-1.4.9`）——纯 vanilla API 伤害，**无 coremod/ASM/mixin 改血量**，但 `META-INF/accesstransformer.cfg`（569 行）把 **`LivingEntity *`/`Entity *` 全公开** + `DATA_HEALTH_ID`(f_20961_) + `setRemoved` + `PersistentEntitySectionManager` 操作，作为攻击方能直接触碰目标内部。

**攻击手法**（procedure 反编译）：Boss 在 `DATA_tick==16` 触发 AoE，对范围内所有 LivingEntity 执行多段：
1. `hurt(source, 20~maxHealth*0.1)` 多次
2. **`entityData.set(DATA_HEALTH_ID, getHealth()-25)` 直写通道**（绕过 setHealth override）
3. `setHealth(getHealth()-30)` 多次（辖界者 override → 重定向 hurt）
4. `invulnerableTime = 0` 清无敌帧

**穿透根因**（不是 override 被绕过，是「高频磨血 + 通道直写」）：
- 辖界者 `getHealth()` 原为 `isRegistered ? 表值 : vanilla通道`——**表被移除后回退 vanilla 通道**，此时黎玄纪元 `entityData.set(DATA_HEALTH_ID)` 直写直接控制 getHealth。
- 表移除路径：`SecureHealthClosure.tick`(`!isAlive`) 或 `LivingEntityMixin.onDie`(die HEAD，辖界者 override 不调 super → 正常不触发)。
- 黎玄纪元多 Boss 高频 AoE，靠 CD 放行磨穿 400 血外部表。

**已实施防御（2026-08-09）**：
1. **堵 getHealth 回退漏洞**：辖界者 `getHealth()` 服务端**强读表**（未注册=0 保持死亡判定），不回退 vanilla 通道；`isAlive()` 改走 `getHealth()` 统一语义。客户端仍读通道用于渲染显示。
2. **拦截直写通道**：辖界者 override `onSyncedDataUpdated`，检测到 `DATA_HEALTH_ID` 被外部直写且 ≠ 表值时立即校正回表值（递归安全：写回表值后再触发检测 written==realHp 不循环）。

**⚠️ mavenLocal vs local-maven-repo-120 发布坑**：前置库 `publishing` 只配了 `local-maven-repo-120`，但下游 yizxianmod 消费 **`mavenLocal()`**（`~/.m2`）。之前 A/B/C 用 `./gradlew publish` 发到了 `local-maven-repo-120`（下游根本不用！），导致 mavenLocal 一直旧 jar，yizxianmod 引用新方法时编译失败「找不到符号」。**发布前置库到下游必须 `./gradlew publishToMavenLocal`**（内置任务，直接进 ~/.m2），不是 `publish`。改前置库后下游编译若报"找不到符号"先查这个。

## 穿透真相升级：梦幻终焉字节码注入接管辖界者血量判定（2026-08-09 日志实证）

**debug.log 铁证**（`D:\桌面\.minecraft\...\logs\debug.log`）：
```
FeCoremod:visit getHealth()F of class:net.minecraft.client.yiz.xian.entity.QuanshouzheEntity
```
梦幻终焉（`com.mega.uom`）的 `LivingEntityCheckTransformer`（JavaAgent ClassFileTransformer）通过 `SoftGetHealthClassVisitor` 给**所有 LivingEntity 子类**（含辖界者）的 `getHealth()`/`isAlive()`/`isDeadOrDying()` 注入字节码，改成交给它的 `EntityASMUtil.special_*`：
- `special_getHealth`：`uom$livingECData().isDead` 为 true → **强制返回 0**
- `special_isDeadOrDying`：`isDead` 为 true → **强制返回 true（死亡）**

同时 `LivingEntityMixin`（priority 1133）给所有 LivingEntity 注入 `LivingEntityEC` 接口（`uom$livingECData()` 返回含 `isDead` 字段的 `LivingEntityExpandedContext`）——辖界者被 `((LivingEntityEC)this)` 强转成功。**一旦梦幻终焉机制（Boss/剑/弓/强制指令）设 `isDead=true`，辖界者 getHealth→0、isDeadOrDying→true，无论 SecureHealthClosure 表值多少都判死。** 黎玄纪元只是攻击源之一。

**防御状态（2026-08-09）**：已堵 getHealth 回退（服务端强读表）+ 拦截 onSyncedDataUpdated 直写。**但梦幻终焉字节码注入在"辖界者 override 的 getHealth 返回值"外层包裹**——Java override 无法抵抗字节码注入的返回值改写。真正对抗需 `bytecode-health-takeover-defense.md` 的「每 tick 强制血量状态 + 清 isDead 标记」思路（enforceSecureHealthState 已做基础版，未清梦幻终焉 isDead）。

**实测**：03:55-03:57 用最新 jar 召唤辖界者 + 选攻击目标，**latest.log 无辖界者死亡记录**（防御初步生效或未完整触发战斗）。待用户完整实测确认。

## ⚠️ 最终真相：yizheng（com.minecraftyiz）在 vanilla tick 层拦截改血量（2026-08-09 QZK-MYSTERY 铁证）

**运行日志铁证**（`[QZK-MYSTERY]`，辖界者 aiStep 每 tick 表值突变检测）：
```
[QZK-MYSTERY] 表值异常下降 391.5 -> 321.5 (drop=70) tick=4490 调用栈:TRANSFORMER/minecraft@1.20.1/net.minecraft.world.entity.LivingEntity.m_8119_(LivingEntity.java:2298)
```
- **drop=70 每 ~15 tick 一次，调用栈是 vanilla `LivingEntity.tick()`（m_8119_）**——表值被绕过辖界者 override 直接改。
- **与黎玄纪元无关**：黎玄纪元（colossus）的 `setHealth`/`hurt`/`entityData.set` 被辖界者 override + CD **完全正常拦截**（日志证明每轮只扣 14，cdBlocked=true）。真正秒杀是 **yizheng**。

**凶手 `yizheng-1.0.jar`（`com.minecraftyiz`，另一个 yiz 系 mod，与 net.minecraft.client.yiz 无关）**：
- `FallbackHealthTransformer`（agent ASM）把 `UniversalHealthInterceptor` 注入 **vanilla `LivingEntity.tick()`**
- `UniversalHealthInterceptor.interceptWrite(Object,float,String)` → `processHealthChange` → `forceSyncHealth` → **`HealthManager.setExactHealth` → `setManagedHealth` → `MapHealthDetector.forceWriteMapHealth` 强制写辖界者血量进它自己的 `EXTERNAL_HEALTH_MAP`**
- `HealthManager` 有 `EXTERNAL_HEALTH_MAP`（外部血量表，与我们 SecureHealthClosure 同构）+ `bypassHealthLock`（绕过健康锁）+ `IS_INTERCEPTING` ThreadLocal 防递归
- **拦截发生在 vanilla tick 层（override 之上）** → 辖界者的 hurt/setHealth/getHealth override **完全感知不到**，表值被强制改。

**结论**：1-2 秒掉一两百血 = yizheng 的 `UniversalHealthInterceptor` 在 vanilla tick 层持续强制改写辖界者健康值。**不是黎玄纪元、不是梦幻终焉（均确认无关/禁用）**。防御方向 = 在 vanilla 层对抗（辖界者 override 层无效），或让辖界者注册到 yizheng 管理时不被强制改/禁用 yizheng。

**已确认**：用户禁用 yizheng（`.jar.disabled`）后，04:34 运行 mod 列表无 yizheng，未再复现 QZK-MYSTERY。
- 只改 1.20.1（用户拍板），1.21.1 源暂不动。

## ⚠️ 梦幻终焉穿透辖界者防御的最终真相（2026-08-10 日志铁证 + 修复）

**现象**：恢复数值防御（动态传导限伤/cap12%/无敌30）后，梦幻终焉仍每 3-5 tick 磨掉辖界者 5~21 点血（QZK-BREAK 铁证），2.5 秒掉 ~190 血，但**无死亡日志、无 hurt 痕迹、无 QZK-DEF 通道拦截**。

**根因（debug.log 铁证）**：`FeCoremod:visit getHealth()F of class:QuanshouzheEntity`——梦幻终焉的 `SoftGetHealthClassVisitor`（ClassFileTransformer）在**运行时**给辖界者 getHealth 的返回出口注入 `special_getHealth(health, entity)`：
```java
float delta = getHealthDelta(living);   // 读它自己的 FE_GET_HEALTH_DATA delta 通道
return Math.min(health, maxHealth + delta);  // 表值被 delta 拉低
```
- 辖界者 getHealth **服务端强读表**（override 正确）→ 但被注入层包在外面改写返回值
- 梦幻终焉攻击 = `addDelta(target, -amount)` 高频（3-5 tick）写 delta 通道 → 注入层把 getHealth 返回值拉低
- `enforceSecureHealthState` 读到被拉低的 getHealth → 想写回表值，但 `catchSetTrueHealth` **1.20.1 反射失败**（无 health 字段）回退 `setHealth`（黑洞写不进）→ **表值被连锁覆写** → QZK-BREAK 显示"掉血"
- **结论：这是字节码注入在 Java override 之外的包裹，Java 层看不到；不是数值问题**

**已修复（commit 74db4ec + 34fa321）**：
1. `EntityActuallyHurt.catchSetTrueHealth` 适配 1.20.1：无 health 字段时**优先 `DirectHealthFallback.setFloatChannelValue` 直写 vanilla 通道**（绕过 set() 与 setHealth override），不再回退黑洞 setHealth。
2. `enforceSecureHealthState` 加**复验**：校正后比较「真表值」vs「getHealth() 返回值」（后者可能被注入层改写），若返回值 < 表值 → 全量扫 Float 通道把偏移量通道（|cur| > 真表值×2）归 0。
- jar 已更新到 `D:\ZM\yizgzq\1.20.1\jar\`，待用户实测确认是否不再被打穿。

**⚠️ 待验证**：若复验仍挡不住，说明梦幻终焉写入的 delta 通道 id 与辖界者自用通道混淆，或注入层在 getHealth 出口之外另有读取点。终极对抗 = 辖界者实现 `LivingEntityEC` 接口接管 `uom$livingECData()`（isDead 恒 false），但那是点名外部模组（用户要求不点名）。

## 相关

- [[entity-attribute-gate]] 1.21.1 涨跌多空/EntityHealthLocator 通用坑
- [[cross-version-port-plan]] 1.20.1 移植总计划
- [[bytecode-health-takeover-defense]] 字节码级防御
