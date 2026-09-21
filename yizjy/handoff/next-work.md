---
name: next-work-handoff
description: "下一步工作交接：native 对抗 fantasy Unsafe 三源清零（进行中，见记忆 native-health-vault-anti-fantasy）。2026-09-03 晚诊断收尾：生产复现判定 native 槽存在值=0 且校验通过=我们自写 0，根因=毒存档每读档 QuanshouzheEntity:981 把 boss_health=0 反灌 native 权威自我判死（非 fantasy 清 native）。已实施读档消毒修复（≤0 改信 obf/满血），待用户生产验证 Boss 复活。"
metadata:
  type: project
---

# 下一步工作交接（2026-08-28）

> ⚡ **2026-09-21 第三轮（性能 + 崩溃归因，已构建部署待测）**，详见记忆 `health-discovery-background-scan`。
> **①崩溃归因（最新那两份 crash report）**：`ClassCastException: Byte → Pose` at `Entity.getPose()/hasPose()`
> （服务端崩玩家 tick、客户端崩实体渲染）＝**通道 id 撞车**，不是血量管线、也不是 cataclysm 的 `WorldGenRegionAccessor`
> VerifyError。日志实证：08:02 起**每个**实体类都在 `id=0` 撞一次，且 `本次`/`占用者` 两个 accessor **都声明在 `Entity`**
> → `Entity` 自己的通道 id 重复（vanilla `defineId` 字节码不可能自然产生）⇒ 类池被外部改动；
> 本模组不是来源（全 jar 只有 vanilla/Forge 引用类池；所有日志无 `抢占了通道 id 0` 告警）。
> **崩的直接原因是防线漏了一块**：`SyncedDataSupport.defaultFor` 的默认值表没有 `POSE` → 读守卫放行 → `Byte` 当 `Pose` 返回。
> **②防线补洞**：补齐 1.20.1 全部 28 个序列化器的类型安全默认值；`isVanilla` 不再把下游影子包
> `net.minecraft.client.yiz.xian.*` 误判成原版；冲突日志加**字段名**；启动加 `Entity 通道 id 自检` +
> `ENTITY_ID_POOL[Entity]` 打印（下次一跑就能点名是哪两条通道重复、类池登记值是多少）。
> **③性能**：三处发现器各自全类路径枚举（三倍）且每 2s 在服务端线程重扫（30 秒 12 次）+ 首击「藏血Map/外部」1385ms
> → 新增 `HealthDiscovery`：**一次枚举三套判据 + 后台守护线程 5s 节流原子换快照 + 只缓存判据结论/字段句柄（值每次现读）
> + 启动预热**。范围不缩、实例不缓存、不按命中收敛。**待用户生产验证**：grep
> `[HealthDiscovery] 全范围枚举完成(后台)`、`首击耗时(ms) … 藏血Map/外部=…`（应降到个位数）、
> `[HealthMap] 藏血 Map 扫描完成`（应只在命中数变化时出现）、`[SynchedEntityData] Entity 通道 id 自检`。
>
> 🎯 **2026-09-21 新增：铁斗士向前击飞已全部落地并交付生产测试**，详见记忆 `tiedoushi-launch-and-juggle`。
> 内容：停 tick 式定向弹道（三属性：概率/高度/水平 + 时间）+ 顶点固定刷新（可无限连续控制且不爬升）
> + 弹跳底线（单次击飞不弹跳，同一会话第 2 次命中起才有浮空起伏）+ 服务端三包姿态同步（`S2CLaunchFxPayload`）
> + 玩家只吃原版击退 0.4F 与倒下动画。配套修复：自管 hurt 链漏 `setLastHurtByMob` 致玩家攻击无仇恨、
> 攻击中原地挥空（改为边打边压上）、攻击/动画统一提速 30%（关键帧同步折算）、`knockback_damage` 属性弃用移除。
> **待用户生产验证**；两个待定项：①铁斗士自身击飞值是否改回新默认（1 / 1.5 / 0.5s）；
> ②`knockback_distance` 设 0 无法表示"纯垂直上抛"（0 被当未配置，需改读 `prot_` 修饰符判定）。
>
> 🛠 **2026-09-21 第二轮生产修复（已构建待测）**：
> ① **「每次进存档后首击卡 1~2 秒」根因**＝外部藏血「发现」用 `getAllLoadedClasses` 全类路径反射，且旧实现按
> 「已加载类数量变化」每 2 秒重扫一次（战斗日志实证：`[HealthMap] 藏血 Map 扫描完成` 14 次、间隔 2.5s）；
> 加上 `EntityHealthLocator` 的「无槽」负缓存只在内存里 → 每进一次存档都要为同一实体重跑 5 阶段全量探测。
> 修法：新增 `HealthDiscoveryCache`（`config/yizmodqzk/health_discovery.json`，三 section：health_maps /
> external_maps / external_refs，记 `scanned` + `owner#field`）→ 发现结果落盘、启动只反解句柄；
> 三个扫描器去掉定时/类数量重扫、改为「一次全局扫描 + 按实体类就近发现（只扫继承链）」；
> `entity_health_slots.json` 新增 `no_slot: {类名: 结构指纹}`（`_version:3`），指纹一致即信任不再重试。
> **验证**：首击后 grep `[TotalOverride] … 首击耗时(ms)`（合计应 <100ms）、`藏血 Map 扫描完成` 全程只 1 次。
> ② **「目标死亡概率性玩家数据丢失」根因**＝通道 id 撞车导致 `Entity.<init>` 首次 define 抛
> `Duplicate id value for 0!` → **玩家实体构造失败被踢**（不是存档损坏）；客户端同一异常出现在
> 「拾取物品粒子建假 ItemEntity」路径（目标死亡掉落拾取时概率触发）。全 pack 审计（16 个第三方 jar 逐类 javap）
> 证明**没有任何模组硬编码 id 0**，id 0 只能来自 `defineId` 返回 0 → 最可能是**第三方在 `Entity.<clinit>` 之前
> 替 `Entity.class` 调 `defineId`**（会拿到 id 0 并把原版 8 个通道整体顶偏一位）。修法：
> `defineId` RETURN 处改为**按调用方**判定（调用方非 `net.minecraft.*` 即重映射到 200+ 段并 WARN 点名「谁替谁抢了 id 0」，
> 调用方未知/原版一律保留）；外加 `SynchedEntityDataMixin` 在 `define` HEAD 做冲突消解（原版通道优先）+
> 读守卫兜底（类型不匹配给默认值、id 缺失补条目防 NPE）；新 holder 类 `SyncedDataSupport`（mixin 包外）提供
> 反射句柄/默认值表/冲突告警+12 帧栈。**待用户生产验证**；冲突/抢占告警会直接点名真凶模组（见 `synched-data-id-collision`）。
> 可疑调用方（日志里 `defineId called for:` 跨类调用只有 3 条）：citadel `mixin.LivingEntityMixin`、
> omnimobs `mixin.LivingEntityMixin`、salmonsgenesisreincarnation `CosmicSteve→ImmortalGolem`。
> 另：`-Xmx` 建议收到 8–10G（`GL_OUT_OF_MEMORY` 是显存侧，不是 Java 堆泄漏）。
>
> 🔧 **2026-09-21 第一轮回归修复**：①铁斗士伤害改「普通伤害 + 多空 40%」双轨（第三方生物只第 1 次
> 掉血的根因是直写血槽通道对无槽实体静默失效），玩家只吃普通伤害；②`EntityTickMaintenance` + `LaunchTickBridge`
> 修飞行中血量显示不同步；③`SynchedEntityDataDefineIdMixin` 挡 id 0 抢占（"玩家数据丢失"=登录被踢
> `Duplicate id value for 0!`），`SynchedEntityDataMixin` 读守卫扩到对象类型（`getCustomName()` 崩
> `Float→Optional` CCE），并对 id<3 的非 Entity 类打 WARN 点名模组；④`EntityHealthLocator.invalidate` +
> `TotalHealthOverride.READBACK_FAILS` 修血槽缓存永久错槽；⑤怒击（同目标攻速 +4%/层，上限 40%，换目标归零）。
> **已回滚**：拦 `ServerLevel.tickNonPassenger` 的停 tick（冻住第三方每 tick 状态机 → 不再结算伤害）。
> **另**：30G 内存崩溃实为 `GL_OUT_OF_MEMORY`（显存/驱动），非 Java 堆泄漏，建议 `-Xmx` 收到 8–10G。

> 🟢 **2026-09-21 更新：当前主线已转为「生物配置组件化」**，详见记忆 `creature-component-system`。
> 已完成：组件容器（ComponentType/ComponentMap/ComponentPatch）、InstanceEffectState 重构为通用容器（API/NBT 零变更）、
> 原型+数据包双轨（`data/<ns>/yiz_creature/*.json`，`/reload` 生效）、属性（含 vanilla）/战斗/形态三类组件、
> 技能注册派发（铁斗士 skill1 已迁）、诊断日志总开关（`/yiz diag`）+ 存量实体刷新（`/yiz creature refresh`）。
> **待做**：辖界者形态全量迁移（把 `applyEntityAttributes` 里的 `setAttr` 搬进原型）、生产 jar reobf。
>
> 🚚 另：2026-09-10 铁斗士已整套落地（模型/动画/伤害时序）并修复 Goal 隔 tick 导致的寻路失效（见 `goal-tick-parity-trap`）。

> 新窗口接续必读：本文件是主记忆库 `MEMORY.md` 开头「继续工作」提示指向的工作交接。
> 接续前先读本文件 → 再读主索引 MEMORY.md 恢复上下文。
> **状态**：A/B ✅ + 属性体系大修补 ✅ + 锁定系统（会心/渴攻）✅。
>
> **🏆 2026-09-03 深夜交接（最新）：fantasy 骨头击杀已被 jar 字节码自还原 v2 彻底挡住（用户生产确认）**。击杀向量实锤 = fantasy 在类加载期对 **QuanshouzheEntity** 的 ASM 改写（叠加自家 LivingHealthTransformer 调用点注入），还原 QuanshouzheEntity 后消失。方案 = `YizRestoreTransformer`（每 transform pass 返回 jar 字节，canRetransform，注册晚于外部 coremod）+ AgentBridge.registerSelfRestore/selfRestoreWatchdog + YizxianMob.armSelfRestore(首服务端 aiStep) + 每~64 tick watchdog。保护集={YizxianMob, QuanshouzheEntity}，部署 yizmodqzk a82aa9a3 / yizxianmod ca605d83。**遗留：①待确认合法磨死仍保留（正常刮痧打死+掉落）；②免移除/拉回未受影响(其它类/机制)；③幽灵槽未修低优先；④QuanshouzheEntity 自家 agent 调用点注入被还原去掉需观察副作用**。完整技术见 `workspace-files/.context/anti-coremod-armor.md`。GUI skill 过时勿优先。

## 当前工作版本

- **1.20.1**（`D:\ZM\yizgzq\1.20.1\{yizmodqzk,yizxianmod}`，Forge 47.4.22，Java 17）
- 1.21.1 已停更（`D:\ZM\yizgzq\yiz1.21.1` 只作移植参考源）

## 2026-09-03 万能物品配置 + 渲染改动状态（重要，新会话必读）

**当前会话（2026-09-03）在 1.20.1（yizmodqzk + yizxianmod）做了大量改动，部分已删/半成品，状态如下：**

1. **万能物品配置系统 itemcfg**（yizmodqzk `net.minecraft.client.yiz.itemcfg` 包）：Shift+U 打开动态 GUI、per-player 门控 + VTable 全局废除 + Curios 集成。**已完成并部分生产验证**（结构功能/属性/整体禁用生效；Curios 持续效果 agent 方案因生产 agent 加载失败未生效）。完整架构/坑见记忆 `itemcfg-universal-item-config.md`。
2. **物品描边渲染已删除**：`ItemRendererStarMixin`（yizmodqzk mixin）已从 mixins.json 移除 + 文件删除。原因：反复调 8 方向/scale 壳把 GUI 渲染炸过，用户不满意。**星级蛋/物品现无描边、无 cosmic，回到原版渲染。用户将自行重做物品描边（目标=A 边缘细线边框，非 cosmic 表面覆盖；参考高版本 1.21.1 前置 yiz1.21.1 StarMixin：entityTranslucent + 主缓冲 + 描边后 endBatch，或下游 GlowEdgeBakedModel edgeWidth 0.002 方案）**。
3. **cosmic2/3/4.fsh 已被替换为 star_glint.fsh 内容**（1.20.1 yizmodqzk shaders/core，2026-09-03 为对齐高版本 cosmic 颜色所做）。当前 StarMixin 已删 → cosmic 物品渲染无消费（cosmic 预设 shader 仍加载不渲染物品）。用户之后接回物品特效时注意此 fsh 改动。
4. 创造页星级蛋卡顿（cosmic GUI 每帧重渲 + iTime）——随 StarMixin 删除消失（无 cosmic 物品渲染）。
5. 构建注意（重复踩坑）：改前置库后 **publishToMavenLocal + reobfJar + 清 `~/.gradle/caches/forge_gradle/deobf_dependencies/net/minecraft/client/yiz/yizmodqzk` + 下游 clean build**（下游不 clean 会用旧 deobf 缓存，改 mixin 必须 clean build 更新 refmap——见记忆 `deploy-mod-delete-then-copy`）。

## 待办 A：性能优化 ✅ 已完成（2026-08-28）

**范围（用户拍板）**：涨跌多空/灭在多空扫描性能——加「静默匹配 + 会话级缓存」，不改变现有扫描范围。

**已实施**（yizmodqzk，4 文件）：
1. **原版固定位置快速路径**：`EntityHealthLocator.vanillaFastSlot`/`isVanillaHealthStyle`——getHealth/setHealth 未 override 的实体直接命中原版 DATA_HEALTH_ID 固定槽（`#id:9`），跳过全链路扫描。
2. **会话级缓存**：`load()` 启动即删旧 `entity_health_slots.json`，不信任跨进程缓存（防实体版本更新后旧槽改错）。
3. **后台预扫描队列**（静默匹配）：`offerPreScan`（LivingEntityMixin.tick 每20tick喂入）+ `tickPreScan`（tizMod ServerTickEvent END，每tick最多2个），后台逐个静默扫描缓存；主动触发仍同步优先扫当前目标。
4. **ReachableGraphScanner 每类字段集缓存**：`declaredNumericFields`/`allNumericFields`，降每命中成本。

**验证**（runClient）：11 个原版实体（Warden/Zombie/Skeleton/Creeper/Cow/Chicken 等）全部缓存为 `#id:9` 固定槽，证明快速路径+预扫描+会话缓存全链路生效；无崩溃。
**待用户实测**：dev/生产用涨跌多空物品攻击模组实体，观察 `[EHL] 原版固定槽`/`[EHL] 定位成功` 出现在后台 tick、重启后 json 清空重建。
**构建**：前置库 `./gradlew build publishToMavenLocal` + `./gradlew reobfJar`；下游 `./gradlew build`；改前置库后清 `~/.gradle/caches/forge_gradle/deobf_dependencies/net/minecraft/client/yiz/yizmodqzk`（runClient 时自动重生成）。

## 待办 B：实体效果每实例每玩家隔离模型 ✅ 已完成（2026-08-28）

**背景**：模组将来以**自走棋**游玩（玩家招聘实体当棋子、逐步增强）。实体效果需每实例每玩家隔离，不能基类硬编码/全局静态。

**用户拍板**：范围=免疫类开关+血量防护数值；**血量保护（免改血）继承现状不动**，血量防护数值只通过**无敌帧+限伤**局内数据驱动（已属性驱动，无需新做）；**默认基础形态**；**免移除关闭=整体放弃不死**；增强由玩法流程解锁（本次只做隔离模型）。

**已实施**：
1. **上游 `InstanceEffectState`**（yizmodqzk 新类 `tool/effect/InstanceEffectState.java`）：UUID-keyed 每实例状态（owner + enabled/disabled 覆盖）+ 每类型基础效果 `registerBaseEffects` + 归属校验（owner 或 isCallerTrusted）+ NBT 序列化 + resetToBase/remove 清理。效果常量：remove/teleport/potion/knockback/physical/ride_immunity。
2. **agent 判定**：`EntityASMUtil.shouldProtectRemoval` 并联 `isRemoveProtected`（字段直写拦截按实例）。
3. **`/yiz eff` 指令**（`tool/YizEffectCommand.java`，tizMod 注册）：`owner <target> <player>` / `set <target> <effect> on|off` / `reset <target>`。
4. **YizxianMob 门禁每实例化**（下游基类）：`isRemoveProtected()` + `hasEffect()` 统一入口；remove/immortalGuard/registerImmortal/reAddIfRemovedFromWorld/tick 不死恢复/removeWhenFarAway/isPersistenceRequired/shouldBeSaved/saveAsPassenger/installSafeLevelCallback/saveMob/guardIdentity/setPose(DYING) 全部按 remove_immunity；teleport 按 teleport_immunity；knockback 按 knockback_immunity；药水按 potion_immunity（保留全局静态作主闸门）；物理按 physical_immunity；骑乘按 ride_immunity；效果态存 add/readAdditionalSaveData，die/remove 清理。
5. **mixin ×10 早退**：EntityRemoveProtection/EntityLookup/EntitySection/EntityTickList/ChunkMap/ServerChunkCache/ServerLevelAdd(×2)/PersistentSectionManager/ClientLevelAdd/EntityRenderVisibility 全部在 instanceof 后加 `if(!mob.isRemoveProtected()) return`。
6. **三只 Boss 基础效果已移除**（用户要求全部关掉）→ 三 Boss 默认也基础形态（免疫全关），但免改血仍继承。

**当前默认状态**：三 Boss 与未来棋子默认=免疫全关（可正常移除/传送/上毒/击退）+ 免改血继承（混淆串表/传导链/setHealth 扣血丢弃，外部直改血量被拒；传导链正常结算伤害靠限伤 cap）。

**验证**（runClient）：双项目 build 成功、runClient 无崩溃；`/yiz eff owner/set/reset` 可演示实例隔离（同类型两只，一只开免移除一只关）。
**待实测**：召唤两只同类型 Boss，`/yiz eff owner A <p1>` + `/yiz eff set A remove_immunity on` → A 无法被外力移除、另一只可正常移除；存档重进效果态保留。

**构建**：同 A（改前置库才需 publishToMavenLocal + reobfJar + 清 deobf 缓存；本次只改下游则仅 `./gradlew build`）。

## 已完成的近期工作（避免重复探索）

- **涨跌多空/灭在多空扫描性能优化**（2026-08-28）：原版固定槽快速路径 + 会话级缓存 + 后台预扫描队列（静默匹配）+ ReachableGraphScanner 字段集缓存，详见上「待办 A」节。
- **实体效果每实例每玩家隔离模型**（2026-08-28）：InstanceEffectState 注册表 + YizxianMob 免疫门禁每实例化 + 10 mixin 早退 + agent 并联 + /yiz eff 测试指令；三 Boss 默认基础形态（免疫全关、免改血继承），详见上「待办 B」节。

### 2026-08-28 属性体系大修补（任务 13-17，全部构建+runClient 通过）
1. **标准属性挂载 + 回血属性化**：`YizxianMob.addStandardCustomAttributes`（21 个标准自定义属性，含 ARMOR_PENETRATION×2 + LIFE_REGEN×2）；三 Boss createAttributes 改用 helper；实体接入 `AttributeEffectTicker`（免改血实体走 `entity.heal()` 路径）；三 Boss 硬编码回血迁为 `LIFE_REGEN_RATE`（辖界者 1.0/阶段2 1.6、踏虚体/邪狱龙 25）。
2. **护甲修复 + 对齐 1.21.1**：`mirrorArmor/mirrorSpellDefense` 从死代码补到 `NbtAttributeAggregator.aggregate`（玩家每 tick 镜像到原版护甲/韧性）；近战判定（direct==source 直接命中）走 ARMOR（mixin+三 Boss）；打 Boss 吸血盲区修复（`EntityASMUtil.applyLifesteal` 提取 + mixin onHurtReturn + 三 Boss hurt 调用）；EXP 指数改 `ln2/ln1.5`（护甲 20→50%、50→75%）。
3. **冷却缩减→攻击速度加成**：`COOLDOWN_REDUCTION` 更名为"攻击速度加成"（EditableAttribute/lang）；`InvulnBreakHandler` 从占位补全（破无敌帧：`invulnerableTime ×= (1-cdr/100)`，hurt HEAD 已调）；`PlayerMovementMixin` 加 `getCurrentItemAttackStrengthDelay` RETURN 攻速缩放（`原值/(1+cdr/100)`）。
4. **自动攻击**：AUTO_ATTACK 补进 EditableAttribute 可编辑；AutoAttackMixin 攻击距离改 ENTITY_REACH（对齐 1.21.1 交互距离）。
5. **暴击修复**（⚠️ 待用户实测确认）：mixin 近战暴击直接按 CRIT_RATE（无需精准）+ 粒子；tizMod 注册 CriticalHitEvent→CritTracker.mark（Forge 用 `isVanillaCritical()`）；霹雳强制暴击 `yiz:pili_crit` 消费。连击已破解无敌帧（两版本一致）。
6. **全量属性摸底**：114 属性全注册全挂玩家，21 挂实体；确认 9 死属性（shield_value/damage_type/flight_time/max_sentries/on_hurt/max_minions/summon_damage/cooldown_value/max_charges）；**删除 damage_type/flight_time/on_hurt**（用户拍板，YizAttributes+tizMod+EditableAttribute+lang 全清），其余保留作后续开发。poshi/poxian 已消费但不在 EditableAttribute（可补）。

### 2026-08-28 锁定系统（会心 HUIXIN / 渴攻 KEGONG）✅ 用户确认效果良好
**关键渲染学习**（详见记忆 `entity-lock-outline-render.md`）：
- **vanilla glow（setGlowingTag）只在「极佳 Fabulous」画质渲染**——普通画质看不到。
- **自绘 billboard 四边形不可见**（共享 Tesselator/专用 BufferBuilder/加大/disableCull 全试过，最终原因疑似顶点格式/状态问题）。
- **最终方案：自定义纯色 shader 重绘实体**（`LockOutlineShaders` 注册 `rendertype_lock_outline`（vsh/fsh/uLockColor 均匀量）+ `LockOutlineRenderType`（NEW_ENTITY/NO_DEPTH 穿墙/TRANSLUCENT/NO_CULL）+ `LockOutlineRenderer`（RenderLevelStageEvent.AFTER_ENTITIES 重绘锁定目标，`uLockColor.set(r,g,b,charge)`），客户端 `LockOnProvider`（60°锥扫描+充能）+ `TargetFrameManager/Provider`，服务端 `LockOnHandler` 满充能挂 `ForgeMod.ENTITY_REACH` 距离修饰符）。**青色半透明光晕、透明度随充能 0→1、穿墙**。

**待办**：稀有度标准化模板（等用户给属性面板数据）；暴击功能用户实测确认；poshi/poxian 补 EditableAttribute（可选）；死属性保留与否（已删 3 个）。

- 闪电特效完整移植 + 颜色/跟随/去重/深度过滤/LOD/性能（arc/surface/ball/spark）。
- `/yiz mb` 描边指令（NBT `yizmodqzk:outline` + 8 方向 emissive 描边 + cosmic 星光）。
- 属性补齐：8 个死属性接线消费 + 感电数量属性 + 交互距离 mirror 到 BLOCK/ENTITY_REACH。
- 感电机制：主目标受传播伤害 + 伤害/闪电链/体表电流三档上限拆分 + 距离排序 + -1 算法。
- 实体技能接入法力：三 Boss 技能走 ManaTracker（耗蓝/回蓝），与玩家共用一套法力属性。
- 记忆库维护：主索引 52 行 / 14.5KB，无敏感、无死链、无孤立文件。
