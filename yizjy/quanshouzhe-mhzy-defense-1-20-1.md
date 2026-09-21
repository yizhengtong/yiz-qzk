---
name: quanshouzhe-mhzy-defense-1-20-1
description: "1.20.1 辖界者血量操作级控制技术研究。✅2026-08-11 改血文件夹深研已完成：super-steve 待深研点全部解决（PLZBase/Agt/SSThread/forceHurt/hurtOffset/SSCore/tryModifyHealth），mhzy/TrialMonolith 多处纠正（双通道不成立/Invader 不可击杀/非纯Mixin/BAN_HEALING 数据层拦截/-inf delta 破清delta）。完整报告：工作区 .context/supersteve-deep-dive.md 与 mhzy-trialmonolith-supplement.md。已实现：血量存储迁移为实体自身混淆 String DataParameter（FloatObf+SECURE_HEALTH+SECURE_HEALTH_KEY，per-key确定性），SecureHealthClosure 调用栈鉴权，基类下沉，agent FRETURN 修复，Bootstrap 崩溃修。⚠️2026-08-11 生产对抗 fantasy 未完全防御（IMPL_LOOKUP 改表盲区），战果见 fantasy-anti-tamper-battle.md + flashfur-health-hiding.md，用户已放弃该方向转数值调整。super-steve 技术栈完整分析见文件内专节。2026-08-12 涨跌多空攻击线最终态：等比软压（DREAM_ACCUM）+双跨阈值死亡链/深层移除+后门白名单+复活清负面+/yiz setHealth 指令（见文件内节）。"
metadata:
  type: project
---

# 1.20.1 辖界者血量操作级控制技术研究（2026-08-10）

## ⚠️ 目标定位（用户强调，别理解成"防御某个模组"）

- **不是去防御梦幻终焉这个模组**，而是**追求技术上的提升**：在哪些底层方法注入、用哪些技术手段，
  获取更强的操作级（对实体血量/生死的底层控制能力）。
- 用户拒绝点名外部模组（不出现 `com.mega.uom` / `uom$livingECData` 字样）——**防御与攻击全通用，不针对特定模组/特定方法**（2026-08-10 用户明示：A 不允许针对特定模组或特定方法，B 允许学思路、用带不稳定性的底层字节码技术）。

## 技术现状（1.20.1 yizmodqzk+yizxianmod 已落地）

1. **agent（Instrumentation）注入 getHealth/isAlive/isDeadOrDying**：`LivingHealthTransformer`/
   `HealthAgent` 放行辖界者类，`EntityASMUtil.special*` 对 SecureHealthClosure.isSecure 实体直接读表。
   **⚠️ 2026-08-10 发现并修复注入模式无限递归 bug**：原"方法开头 INVOKEVIRTUAL 取原始值"是虚分派，
   对覆写 getHealth 的类派发回自身 → StackOverflow（独立 ASM 复现证实）；已改 **FRETURN/IRETURN 返回值
   包装**（学 mhzy 思路，零递归、改名免疫），复现程序验证 NO_RECURSION。
2. **baseTick 不死强制**：表>0 时 `dead=false; deathTime=0`。
3. **通用清 delta**：扫非 vanilla 血量 Float 通道归 0。
4. **库 SecureHealthClosure**：tick 按表判死、removeAll 表>0 拒绝。
5. **vanilla 伤害类型闸门**：hurt() 只放行 minecraft 命名空间伤害类型；setHealth 外部扣血丢弃。
   （1.20.1 无 `source.type().getKey()`，用 `Registries.DAMAGE_TYPE` registry 反查 key，已编译通过。）

## ✅ 防御线实施（2026-08-10，编译验证通过，未进游戏实测）

用户确认"先防御线直接开工"，四项落地：
1. **SecureHealthClosure 加固**：写入口（setHealth/register/removeAll/setMaxHealth）复用
   `EntityAttributeGate.isCallerTrusted()`（StackWalker+yiz 家族包白名单）鉴权，堵外部直调秒杀/清表。
   ⚠️ **XOR 混淆已移除（2026-08-10 晚）**：进游戏出现垃圾负表值（-519/-646），怀疑 XOR noise 在实体
   生命周期不匹配；去掉后负表值仍出现（-142/-646）→ 负表值另有来源（SECURE_PULSE=0 时 getHealth 回退
   getMaxHealth，而 getMaxHealth 可被外部打成负值）。读入口无鉴权（热路径）。
2. **血量保护 override 下沉 YizxianMob 基类**：getHealth/setHealth/isAlive/isDeadOrDying/actuallyHurt/
   heal/kill/die/remove/setPose/dropAllDeathLoot/saveAsPassenger/shouldBeSaved/baseTick 全在基类；
   基类默认 hurt()=vanilla 闸门→传导 CD（INVINCIBILITY_MULT 动态）→传导限伤（CONDUCTION_CAP 默认5%）→直扣表；
   `registerSecureHealth()` 首次 aiStep 自动注册 → **任何 YizxianMob 子类免改血，不依赖 agent/mixin**。
   QuanshouzheEntity 删重复 override（继承基类），保留强化 hurt（护甲/法防指数减免+反击）与 die（Boss 清理）。
3. **每 tick 强制校正泛化**：`enforceSecureHealthState()`（aiStep 调）= 表值回写自身通道
   （EntityActuallyHurt.catchSetTrueHealth）+ 清未知 Float delta + 防 removed/removalReason 字段 + 防 MAX_HEALTH 篡改。
4. **agent 递归修复**：见"技术现状 1"。

**注意**：agent 修复后对所有实体 getHealth/isAlive/isDeadOrDying 生效（行为与原意一致）；base hurt 无护甲
指数减免（子类可扩展）；enforce 每 tick 写 DATA_HEALTH 通道有网络开销（原辖界者已如此）。

## 已验证 / 待研究

- `[QZK-HURT]` 显示梦幻剑 `fantasy_ending.ds_power` 经 hurt() 路径被 CD 限到 ~21/下（cap 48, CD 30tick）。
- 用户观察到它能突破 25%/30tick → 存在绕过 hurt() 的路径（catchSetTrueHealth 直写 DataItem / delta 通道 / FRETURN 包装覆盖），攻击线需针对性研究。

## ⚠️ 传导属性驱动最终态 + 编辑器实时生效（2026-08-12，用户确认效果满意）

**核心坑（必读）**：`QuanshouzheEntity` 曾把 `conductionCap()`/`conductionHitCdTicks()` **硬编码 override**
（25% / 16 tick）——伤害链 cap/CD 完全不走属性，**编辑器改 CONDUCTION_CAP/INVINCIBILITY_MULT 永远不生效**。
曾误排查 ConductionCapVault 同步/AttributeStandardizer 还原，真正根因=硬编码 override 绕过基类属性驱动。
⚠️教训：给实体 override 防御方法前先想清楚是否要"属性驱动"，测试值硬编码会封死编辑器调参。

**最终态（1.20.1）**：
- 删 conductionCap()/conductionHitCdTicks() override → 走基类 `YizxianMob` 属性驱动版本。
- 传导标准：`CONDUCTION_CAP=25`（25% 最大生命值）、`INVINCIBILITY_MULT=16`（0.8s=16tick，固定不随难度）。
- hurt() 里 vanilla 无敌帧 `invulnerableTime` 硬编码 20 → 跟随 `conductionHitCdTicks()`（解除耦合，传导 CD 真实跟随属性）。
- **编辑器实时生效机制**：`conductionCap()`/`ConductionDamageLimiter.readConductionCap` 对 **markEdited**（编辑器编辑）属性
  直接读属性值（实时跟随）；未编辑走 `ConductionCapVault` 权威表防篡改。`ConductionCapVault.checkAndRestore` **尊重
  markEdited**（防把编辑器改的值当篡改还原）。`EntityAttributeGate.set` 同步权威表用 **ResourceLocation 比较**
  （RegistryObject.create 实例 == 不可靠）。markEdited 已在 `C2SEntityAttributeEditPayload.handle`（豁免 AttributeStandardizer 还原）。
- **cap 语义**：CONDUCTION_CAP 是**单发传导伤害上限**（maxHp×cap%），调小限制单发、调大放宽到减伤后原伤害（**非倍率**）。
  验证：cap=2% → 扣8/发；cap=8888% → 扣 reduced(~114)；传导 CD 实时跟随。
- 诊断日志：`[AttrEdit]`（编辑器写入）、`[CapD]`（cap 来源）、`[COND-DIAG]`（传导 CD 读值）。

## ✅ 涨跌多空攻击线最终态（2026-08-12，编译+runClient 进世界无崩溃验证通过）

用户要求「对照 long_short 攻击线逐条加强，能用对方思路就用对方思路」，已完成：
- **A 等比软压血**：`EntityASMUtil.applyProportionalDreamDamage` + `DREAM_ACCUM`（UUID→累积系数）。
  真实槽直改优先（applyPersistentDamage+永久禁疗）；失败→等比累积 `accum += dream/maxHp`，软压
  `delta=-maxHp×min(accum,1)` → getHealth 被钳 `min(health, maxHp×(1-min(accum,1)))`（delta 通道全实体生效）。
  双跨阈值：累积≥1→`dreamDeathblow` 完整死亡链；≥10→`dreamDeepRemove` 深层移除。
- **B 完整死亡链**：`dreamDeathblow` = -inf 判定死亡 + recordDamage(死亡消息显示攻击者) + 反射 die
  + 清 goals/brain+noAi + kill() 兜底 + 非玩家 forceRemoveDeep 兜底。
- **C 百分比兜底到阶段1**：QuanshouzheEntity.hit 全阶段统一走等比累积，阶段1/2=2.5%、阶段3=5% maxHp 涨跌多空伤害追加。
- **D KILLED 移除语义**：`EntityRemovalUtil.forceRemoveDeep` 用 RemovalReason.KILLED（触发死亡掉落语义）。
- **后门白名单**：`EntityASMUtil.isBackdoorExempt`（开关 backdoorWhitelistEnabled 默认 true）——目标为玩家且
  创造/旁观 → 攻击线不生效（applyProportionalDreamDamage/dreamDeathblow/dreamDeepRemove 三入口豁免）。
- **复活清负面状态**：`PlayerRespawnHandler`（yizmodqzk.handler）挂 PlayerEvent.Clone+PlayerRespawnEvent →
  clearDreamAccum + setHealthDelta(0) + VitalitySeveranceConfig.remove + VitalitySeveranceHandler.clear/removeTempBan。
- **/yiz setHealth `<radius>` `<value>` 指令**：`YizSetHealthCommand`——范围内实体血量直设为指定值
  （不限正负：负值判定死亡/零死亡/超大超高血量），排除使用者自己。混淆实体走
  `SecureHealthClosure.setHealthUnbounded`（无 clamp+先 removeIntegrity 防完整性回滚）；其它走
  `EntityHealthLocator.writeLocated`；兜底 `DirectHealthFallback` 直写 vanilla DataItem。
- **坑**：
  ① DREAM_ACCUM 按 UUID 存，玩家死亡重生 UUID 不变会残留（delta 还会经 NBT 从旧实体克隆回新实体）
  → 必须 PlayerRespawnHandler 清；
  ② LivingEntityMixin.onDie 清 delta 通道但不清 DREAM_ACCUM，需补 clearDreamAccum；
  ③ 混淆实体设负值会被 enforceTableIntegrity 当外部直写回滚 → setHealthUnbounded 先 removeIntegrity；
  ④ 改前置库必须 publishToMavenLocal（下游 fg.deobf 从 mavenLocal 引 jar），再下游 build + runClient。
- **命名**：delta 通道字段 `yizmodqzk$HEALTH_DELTA`（原 FE_GET_HEALTH_DATA 已改名去外部缩写）。
- 此前方向：真实槽定位增强（detectViaBytecode 分析 setHealth PUTFIELD）已落地；VarHandle 直写（方向①）未做（保留反射）。
- 完整拆解：工作区 .context/trialmonolith-health-tech-teardown.md（对照模组的血量操作技术彻底拆解）。

## ✅ 借鉴落地（08-11，防御/攻击线激进完整版）

存储层迁移：血量静态 Map → 实体自身混淆 String DataParameter（FloatObf 确定性 per-key 混淆 + SECURE_HEALTH/SECURE_HEALTH_KEY + 调用栈鉴权 + 持久化串+key）。借鉴落地：D1 SynchedEntityData 数据层拦截（ThreadLocal 写门禁，不用 isCallerTrusted）/ D2 onSyncedDataUpdated 钳制 + correctObfHealthString / D3 isDead 平行标记清除 / A1 forceRemoveDeep（onSoulRemove 式）/ A2 applyDreamDamageAggressive + dreamDeathblow / A3 三阶段 hit 接入。

## ⚠️ super-steve 技术栈分析（08-11 深研）

顶级防御标杆：血量存混淆 String DataParameter + `ssSetHealth(v,this)` 自鉴权 + vanilla setHealth 空实现 + hurt 伤害转百分比+反射回攻击者 + tryModifyHealth 盲探定位器（被混淆串免疫）+ forceHurt 重写 actuallyHurt。底层：PLZBase（全权限 Lookup）/ Agt（self-attach agent retransform 隐藏类）/ SSCore（重定义 getEntities）/ SSThread（独立线程 tick，数据竞争/CPU 双坑）。mhzy/Trial 关键纠正：UomWither"每 tick 清 delta"实为 10tick ±1 收敛且对 -inf 无效；TrialMonolith 非纯 Mixin（有 ILaunchPluginService 自注册 GenericTransformer）；InvaderMonolith 灵魂伤害也免疫、几乎不可击杀；BAN_HEALING 在 SynchedEntityData.set 数据层拦增写（防御借鉴价值最高）。完整分析见 workspace-files/.context/supersteve-deep-dive.md + mhzy-trialmonolith-supplement.md。

## ✅ 黑科技 + 生产修复（08-12）

- **三个黑科技（学 Trial）**：①HEAD 守卫（mixin getHealth/isAlive/isDeadOrDying @Inject HEAD 早退，抗外部覆盖）②强制 tick（agent 注入 EntityTickList.forEach 双 tick + guardEntityTick cancel + ITickTracker）③LevelInvoker（暴露 Level.getEntities 底层 LevelEntityGetter）。
- mixin priority 全部 Integer.MAX_VALUE。
- **SRG 适配**：DirectHealthFallback（VANILLA_HEALTH_ACCESSOR=f_20961_/itemsById=f_135345_/isDirty=f_135348_）、EntityHealthLocator（getHealth=m_21223_/setHealth=m_21153_）、EntityRemovalUtil（remove=m_142687_/getSection=m_156895_）。⚠️ die 反射名 **m_6667_**（勿写 m_6677_）、getSection **m_156895_**（勿写 m_156893_），08-13 核对 tsrg 修正。
- **agent 动态加载**：savedProps+attach+loadAgent，已封装 skill `agent-self-load`。
- **生产修复**：refMap 改后 clean build、setHealth clamp 上限、客户端误判死回退混淆串、shouldOverrideTick 排除死亡实体、血条文本 boss.getProgress()。

## ✅ 多辖界者击杀问题已解（2026-08-13）

客户端 getHealth 判死竞态：多实体共存时部分客户端 SECURE_OBF_KEY 广播丢失 → 混淆串 dec 用错 key 出垃圾 → 误判死/误判活。六次重测最终修复=**obf0 判死改严格 `obf==0.0f`**（FloatObf 模 2^32 对称，dec(enc(0,key),key) 严格还原 +0.0f 无噪声，垃圾永不等于 0）+ **YizxianMob.remove 死亡后手动发 ClientboundRemoveEntitiesPacket 兜底**（Destroy 广播竞态双保险）。遗留：key 竞态仍概率性存在，只影响"无倒地动画"，不影响击杀移除。已清 CLIENT-HP/KEY_MISMATCH 诊断（保留 QZK-REMOVE/QZK-DEATH 少量诊断）。

附带：`/yiz sx zddk <数值>` 给主手加涨跌多空属性（PlayerDreamAttackHandler 触发，攻击者 Player 且 FIRST_DREAM>0）；前置库 build/libs 是 dev jar，部署必须显式 `reobfJar` 产 SRG jar（否则 NoSuchFieldError）。
- **涨跌多空击杀无掉落修复（2026-08-13）**：涨跌多空绕过 hurt()（不走 vanilla 伤害链）从不设置 lastHurtByPlayer/lastHurtByPlayerTime → vanilla die() 掉落分支（lastHurtByPlayerTime>0）不执行 → 实体只被深层直删移除、无 LootTable/经验。修复=EntityASMUtil.applyProportionalDreamDamage 每次命中反射设置目标 lastHurtByPlayer/lastHurtByPlayerTime（SRG 名 f_20888_/f_20889_），死亡时 vanilla 掉落链正常触发、掉落归属攻击者。
- **DreamDelta 日志刷屏卡死修复（2026-08-13）**：测试涨跌多空打 UomWither/AshesWarden（验证掉落）时游戏卡死（PCL"0 报错"、无堆栈、内存充足=非崩溃，磁盘 I/O 饱和）。根因=EntityASMUtil.specialGetHealth（agent 注入所有 getHealth() 的包装）里 `[DreamDelta]` 诊断日志限频 `%50==1`（每 50 次打 1 条、无限刷），delta 非 0 期间客户端每帧+服务端每 tick 大量调用 → 日志刷爆卡死。修复=`<=50`（前 50 条；SPECIAL_LOG/SPECIAL_MAX_LOG 本就是 `<=20` 正确）。⚠️附带发现：UomWither 有效血量 34.5 几乎不降（delta 软压对 UomWither 无效，5 层保护）——待后续。
- **学 Trial 修 UomWither 无效 + Ashes Warden 卡死（2026-08-13）**：①UomWither 涨跌多空无效根因=UomWither 每 10 tick 清 delta 通道（yiz 软压被清），Trial 用 vanilla `setHealth(-inf)` 直接写（不走 delta）→ 有效。修复=EntityASMUtil.dreamDeathblow 在 setHealthDelta(-inf) 后追加 `target.setHealth(-inf)`。②Ashes Warden 即将死亡卡死根因（推测）=dreamDeathblow 深层直删兜底 forceRemoveDeep（Unsafe 绕过 vanilla 移除链）对 Warden 特殊实体死循环；Trial onSoulRemove 用 vanilla `remove(KILLED)`+levelCallback.onRemove 安全。修复=移除兜底改**先 vanilla remove(KILLED)，失败才 forceRemoveDeep**。⚠️Ashes Warden 修复为推测待重测验证。
- **掉落实测不生效根因（2026-08-13，为何 yiz 掉不了而 Trial 能）**：①**SRG 名写错**——setLastHurtByPlayerReflect 误用 f_20890_/f_20891_，实际是 **dead/lastHurtByPlayerTimestamp** 字段（mappings.tsrg 确认：lastHurtByPlayer=f_20888_、lastHurtByPlayerTime=f_20889_、dead=f_20890_）→ 生产 SRG 下 lastHurtByPlayer 从未真正设置 → vanilla die() 掉落分支（lastHurtByPlayerTime>0）永不满足。②**无显式 dropAllDeathLoot 兜底**——Trial onSoulDeath 在 die() 后 `!dead` 显式调 dropAllDeathLoot（Invoker m_6668_），UomWither 锁血拦 die 也掉落；yiz 只依赖 vanilla die 掉落。修复=①修正 SRG 名②dreamDeathblow die 后 `!dead` 显式 dropAllDeathLootReflect（m_6668_）。
- **掉落+残留问题（2026-08-13）**：掉落修复后血量归零有掉落但实体残留原地。根因=vanilla remove(KILLED) 只设 removed 标志（isRemoved 立即 true）不真正反注册 → dreamDeathblow 移除兜底 `!isRemoved()` 判断跳过 forceRemoveDeep → 残留。修复=血量≤0 无条件 deepRemoveSafely/forceRemoveDeep（去 isRemoved 判断，forceRemoveDeep 幂等安全）。同时数据层操作强化：伤害阶段 applyPersistentDamage 失败后 forEachFloatItem 直改所有 Float DataItem 扣血；死亡阶段 setHealth(-inf)+catchSetTrueHealth+forEachFloatItem 设 0+无条件 die。⚠️forceRemoveDeep 无条件调用可能对特殊实体卡死待观察。
- **交接（2026-08-13 03:17，上下文耗尽）**：后续工作=全面扫描目标模组 TheTrialMonolith（灵魂伤害/移除/掉落/保护机制）对比 yiz 涨跌多空找差距对接改进。**完整自包含交接提示词见工作区 `.context/handoff-trialmonolith-scan.md`（新会话复制全文执行）**。yiz 最新状态：数据层操作强化（forEachFloatItem 反射直改 DataItem 扣血/设 0，绕过 override）+ 无条件移除（forceRemoveDeep 反注册）+ 掉落 SRG 名修正（f_20888_/f_20889_）+ 注释清理（只留 SRG 名，不提及外部模组）。
- **操作链完整性对接（2026-08-13 上午，扫描 Trial 后落地）**：用户要求"绝对相等甚至更胜一筹"——数值次要，操作链完整优先：归零后必须「死亡动画→掉落→移除回调→物理移除」全走完，不出现 A 缺动画/B 缺掉落/C 缺移除。根因=yiz 移除用 Unsafe 暴力反注册（跳 tickDeath 动画 + 缺 levelCallback.onRemove 回调）+ 累积只走 delta 通道（累积=0 时 override 门控不通过→掉落/动画被拦）。四项改进（前置库 EntityASMUtil+EntityRemovalUtil）：①**绝对累积兜底** DREAM_ABS_ACCUM 双轨阈值 1000/10000（修 Infinity/巨大 maxHp 打不死，Invader/UomWither 型）；②**读侧强制判死** specialGetHealth/isAlive/isDeadOrDying 加 isDreamDeathAccum（override die/dropAllDeathLoot 的 isDeadOrDying 门控通过→掉落+动画生效）；③**死亡流程分离** dreamDeathblow 不立即反注册+不清累积（tickDeath 跑 20tick 动画，深层移除交给 dreamDeepRemove≥10，同时解决第 223 条"无条件反注册卡死"隐患）；④**移除链正规化** forceRemoveDeep 学 onSoulRemove 完整顺序（remove→setRemoved→onRemovedFromWorld→levelCallback.onRemove→反注册兜底→invalidateCaps；levelCallback 是 private 字段，反射按 EntityInLevelCallback 类型定位，比按字段名反射更稳）。⚠️待重测：打 UomWither/AshesWarden/Invader 型实体验证动画+掉落+移除三完整。
- **两步归零保底（2026-08-13）**：dreamDeathblow 拆分——目标当前血量>1 时先数据层直写设到 1（濒死过渡，实体存活一 tick），用 HealthModificationScheduler.once(delay=0) 调度 finishDeathblow 下一 tick 归零+完整死亡链（抽出的独立方法）。避免一步归零让客户端看到血量跳变、跳过受伤/死亡过渡，感官更自然。
- **Ashes Warden 卡死修复 + 客户端 C2S 触发（2026-08-13）**：①Ashes Warden 归零卡原地（0/500 不倒地变红）根因=**die 反射 SRG 名写错 m_6677_（实际 m_6667_）+ getSection 写错 m_156893_（实际 m_156895_）**→ 生产环境反射一直失败、die 从未被调用。核对 tsrg 后修复，其他 SRG 名（getHealth m_21223_/setHealth m_21153_/isDeadOrDying m_21224_/isAlive m_6084_/remove m_142687_/dropAllDeathLoot m_6668_/字段 f_20961_/f_135345_/f_135348_/f_20888_/f_20889_/f_20890_）均正确。②涨跌多空玩家触发改**客户端判断+C2S**：新增 PlayerAttackMixin（客户端 Player.attack 读 FIRST_DREAM>0 发 C2SDreamAttackPayload）+ 服务端 handle 执行 applyDreamDamage，删 PlayerDreamAttackHandler（服务端 LivingAttackEvent 会被目标 hurt 流程/counter 血量实体阻断）。
- **气丹三个技能动画（2026-08-13，验证通过）**：辖界者三个气丹技能动画（`QuanshouzheAnimations`）：`QI_DAN`（凝聚 1.375s，气弹体内放大到 9 倍上移到头顶）/`QI_DAN_2`（发射 0.75s，气弹向后上方飞出）/`QI_DAN_3`（凝聚+发射合并 2.0833s，用户 Blockbench 拼好）。bone2 气弹挂 body 胸前（offset 0,-19,0）+ 三个 AnimationState（qiDan/qiDan2/qiDan3，handleEntityEvent **byte 60/59/58**，⚠️63 被原版 Sniffer 占用勿用）+ `/yiz dh xjz skill 1/2/3` 让周围 5 格内辖界者播放。⚠️**AnimationState 不自动 stop**，动画播完卡最后一帧（气弹停在飞出帧）→ setupAnim 里 `stopAnimationWhenDone(getAccumulatedTime()>=时长ms 则 stop)` 回 idle；单位=getAccumulatedTime 返回毫秒（start 里 lastTime=tickCount*50）。贴图 warden.png。**下一步=实际技能安排**（动画已通，用户先细化动画）。

## ✅ 读路径拦截对齐 Trial（2026-08-14，大贤者型实体已打通）

外部模组实体用「override getHealth 读加密 String DataParameter + KlassHacker 换头成隐藏类」藏血量时，delta 软压只在服务端生效，客户端 Boss 血条/remove() 读原值。根因链（均已修）：

- **调用点包装只看 `owner==LivingEntity`** → 改 `isLivingEntitySubclass` 走父类链（Trial isSubclass 思路）；`readSuperName` 双 loader（定义 loader + 游戏 loader，SecureJarClassLoader 只搜本 jar 找不到游戏父类）。
- **COMPUTE_FRAMES 用 agent 隔离 classloader** → 复杂类帧重算失败回退 COMPUTE_MAXS 关掉调用点包装；且 `Class.forName` 求公共父类会触发 JPMS 模块重复定义 LinkageError。修复=`newFrameClassWriter` 覆盖 `getCommonSuperClass` 走 .class 资源父类链（零加载）。
- **retransform 失败**（agent 自挂载晚，早加载类靠 retransform 补）：数组类拖垮整批→过滤 + 分批逐个重试；retransform 线程 context classloader 拿不到游戏类→HealthAgent 解析 AgentBridge 后把其 Class 注入 transformer（`BRIDGE_CLASS_REF`，其 loader=游戏 loader），`readSuperName`/反射都走它。
- **死亡流程**：`dreamDeathblow` 之前读 `getHealth` 判断误入「延迟 finishDeathblow」分支，让目标转阶段（getHealth≤50%）先于死亡触发（"重生特效" + 需二次攻击才移除）。修复=累积跨阈值直接同步 `finishDeathblow`（学 Trial onSoulDeath 同步 die）+ die 后 `DREAM_ACCUM` 重置 1 保持判死（防 onDie 清累积"复活"导致二次死亡取消 forceRemoveDeep）。

验证：大贤者 600 血 Boss 血条随累积下降、归零倒地+移除。⚠️遗留：转阶段粒子仍可能闪现（死亡后无害），如需压掉可在 finishDeathblow 同步压 DATA_TRANSITIONING。诊断日志已清（只留 /yiz agent 计数）。

## 相关

- [[bytecode-health-takeover-defense]] 字节码注入对抗通用思路
- [[modding-tech-landscape]] 技术深度 6 层分类/Instrumentation/AT/TransformationService
- [[120-blood-dataparameter-port]] 1.20.1 血量 DataParameter 差异
- [[conduction-health-protection]] 传导限伤+外部表终态
