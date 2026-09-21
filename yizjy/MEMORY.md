# Memory Index

> ⚠️ **继续工作前必读**：下一步工作交接 → [handoff/next-work.md](handoff/next-work.md)。**当前主线 = 生物配置组件化**（见 `creature-component-system`）；对抗 fantasy 的收尾遗留项见下方 09-03 块。
>
> 🟢 **2026-09-22 第五轮（累加器型血量「锁死」根因）**：legendary_monsters 的 `IAnimatedBoss` 系 boss 血量是**派生值** `getHealth() = clamp(上限 − totalDamageTaken, 0, 上限)`，累加器只在 `LivingHurtEvent → addDamage()`（单次上限 `damageCap()=21`、命中后 `hurtCD=30`）与 `heal()` 里变。我们的定位一直是对的（`entity_health_slots.json: totalDamageTaken, inverse=true`），**但 `VitalitySeveranceHandler.enforceFieldTick` 的回血判据把 inverse 语义重复套在已经是逻辑血量的值上 → 判反**：自己的扣血被当回血回滚、boss 的真回血放行 ⇒「首次能改、之后永远锁死在首次改完的值」。已修：判据只认逻辑血量升高 + 每 tick 棘轮（`enforceFieldFastTick`）；反向槽「被拉回」改为直接重写同一字段钉回而不是猎门控；门控验收容差 5%→1% + 必须与探测前基线不同 + 双向复核 + 死亡还原（原实现把 `dimensional_shoot_cooldown` 误钉成 1e9 且不再验证）；反向槽上限 `b` 补齐（field 槽以前不记 b，只能自推导，会算错）。详见 `accumulator-health-inverse-slot`。
>
> 🟢 **2026-09-21 第四轮（生产反馈两项已修）**：①**玩家误伤** —— 改血线公共入口 `applyProportionalDreamDamage` 原先只豁免创造/旁观玩家，生存玩家被整条线命中（直改真实血量绕过护甲、挂 100% 禁疗、门控击穿、判死标记）→ 现加闸门「目标是玩家只走普通伤害轨」，铁斗士/辖界者/C2S 攻击包一处收敛（见 `dev-prod-reflection-and-player-gate`）。②**生产对外血量不刷新（开发正常）** —— 反射字符串常量不会被 reobf 重映射：`HealthChannelScanner` 只写 official 名 `DATA_HEALTH_ID` → 生产解析成 null → `YizxianMob` 的 Float 通道清零循环失去「跳过 vanilla 血量通道」守卫 → 每 tick 把真血写进通道又被清零。已统一到 `DirectHealthFallback` 的三级解析（official 名 → SRG 名 `f_20961_` → 类型兜底）并让失败/成功都留日志。
>
> 🔴 **2026-09-21 第三轮（性能 + 发现分层，已构建部署待测）**：**藏血发现性能改造** —— 三处发现器（`HealthMapRegistry`/`ExternalHealthStore`/`ExternalRefStore`）各自做「`getAllLoadedClasses()` + 逐字段反射」= 三倍全类路径枚举，且随类数量每 2s 在**服务端线程**重扫（实测 30 秒 12 次）+ 首击「藏血Map/外部」段 **1385ms**。修法：新增 `HealthDiscovery`（一次枚举三套判据 + 后台守护线程 5s 节流原子换快照 + 按类缓存判据结论与**字段句柄**，字段值每次现读 + 启动预热）。**范围不缩、实例不缓存、不按命中收敛**（那三条正是上一轮的错）。**发现分层（用户定策）**：新增 `HealthTier` —— **常规实体缓存**（跳过外部藏血发现，30s 复验 + 结构指纹作废）、**非常规生命值实体一律现场扫描**（粘性）；判据全走行为证据：定位到主槽/发现器命中/FSUB/2 tick 写回被拉回/回读未落地/命中门控 → 非常规；全量探测无命中 **且** 2 tick 写回保持或已被本次写入击杀 → 常规。同时补崩溃防线洞：`defaultFor` 默认值表漏 `POSE` → 通道 id 撞车后 `Entity.getPose()` 读 `Byte` 抛 CCE 崩 tick/渲染，已补齐 1.20.1 全部 28 个序列化器；`isVanilla` 不再把下游影子包 `net.minecraft.client.yiz.xian.*` 当原版。详见 `health-discovery-background-scan`。
>
> 🔴 **2026-09-21 生产修复（已被上一轮取代）**：①**通道 id 撞车自愈** —— 第三方硬编码 id 0 的 accessor 与 `Entity.DATA_SHARED_FLAGS_ID` 同槽 → 实体构造抛 `Duplicate id value for 0!`（玩家登录被踢「无效的玩家数据」/ 拾取粒子崩客户端），现于 `SynchedEntityData.define` 层兜底（原版通道优先）见 `synched-data-id-collision`；②**外部藏血发现扫描**改为「落盘 + 按实体类就近发现」——**已回退**（缩小发现范围致「map 生命实体改不动」），见 `health-discovery-cache`（反面教材）与 `health-discovery-background-scan`（现行方案）。
>
> 🟢 **2026-09-21**：生物配置组件化落地 —— 组件容器（ComponentType/ComponentMap/ComponentPatch）、InstanceEffectState 重构为通用容器、原型+数据包双轨（`data/<ns>/yiz_creature`）、属性·战斗·形态三类组件、技能注册派发、诊断日志总开关（`/yiz diag`）+ 存量实体刷新（`/yiz creature refresh`）。**待做：辖界者形态全量迁移、生产 jar reobf**。
>
> 🔴 **2026-09-03（遗留项尚未生产验证）**：fantasy 骨头击杀已被 jar 字节码自还原 v2 挡住（用户生产确认）。方案=`YizRestoreTransformer`(每次 transform pass 返回 jar 字节)+AgentBridge.registerSelfRestore/watchdog+YizxianMob.armSelfRestore；保护集={YizxianMob, QuanshouzheEntity}。**遗留：①确认合法磨死仍保留；②免移除/拉回未受影响；③QuanshouzheEntity 自家 agent 注入被还原去掉需观察副作用**。详情 `native-health-vault-anti-fantasy` + `handoff/next-work.md` + `workspace-files/.context/anti-coremod-armor.md`。

- [记忆存放分类守则](00-memory-classification.md) — 先分类再存放：安全限制→settings/hooks；项目规则→CLAUDE.md；任务相关→Auto Memory。受保护，禁删。
- [代码注释风格](code-comment-style.md) — 注释去 AI 味：无表情/无外部模组名/无行话；术语"梦幻"→"涨跌多空"；标识符不含外部缩写
- [No magic number hacks](no-magic-number-hacks.md) — 全场/无限制用正确 API，别塞魔法数字
- [属性改名三处同步](attribute-display-name-sync.md) — 改属性显示名必须同步 lang+ItemAttr+Editable 三处，改前 grep
- [HUD 位置持久化](hud-position-config.md) — hud_positions.json 的位置/格式/换算/被清空排查
- [GUI 背景偏移 1px](gui-bg-offset.md) — AbstractContainerScreen 拼装面板必须 leftPos-1/topPos-1，否则 slot 点击差 1px
- [实体探查镜 GUI 落地](entity-probe-gui.md) — 1.20.1 右键实体GUI：元素树编辑器(拖动/下钻/保存)/真槽换装/血蓝条/A1实体头像+A1.1投影板；GUI方法论见 guiskill skill modules/04+05（联动）
- [Mixin 1.21.1 踩坑](mixin-gotchas-1-21-1.md) — refmap 不生成：@Inject 最稳(短名可命中)、@ModifyArg/ExpressionValue 需完整描述符 target、LiquidBlock 双重陷阱
- [Mixin @Unique 静态字段→目标<clinit>生产崩溃](mixin-unique-static-clinit-crash.md) — @Unique static 字段初始化引用 vanilla 字段合并进目标类<clinit>，生产 SRG 未重映射→Bootstrap NoSuchFieldError；修复=独立 holder 类
- [自定义容器 Menu 的坑](container-menu-pitfalls.md) — ItemStack.CODEC 不能编码 EMPTY、客户端容器不反向同步、虚拟槽用绝对坐标、EditBox 失焦
- [穿墙轮廓共面合并](outline-render-coplanar-merge.md) — 相邻方块中间分割棱消失要靠"共面接缝判定"，不是去重也不是渲染配置；附 EdgeKey 碰撞避坑
- [vanilla 暴击 baked 换算](crit-damage-vanilla-baked.md) — 跳劈已把 1.5x 算进 amount，叠加 CRIT_DAMAGE 要用 /150 换算不是 /100；且 consume 必须在伤害块开头取出，否则标记残留误加暴击
- [热数据别走 PlayerDataAPI](hot-data-no-persistent-api.md) — 每 tick 写的数据会让 set 全量解析+全量 S2C 同步整个 root；退出即弃的数据改纯内存+事件驱动下发
- [空间GUI](空间GUI.md) — 世界光屏完整系统：5硬坑+假关闭+准星左右键+多光屏切换+空白区穿透+防抖+左键保护+容器摧毁同步+FBO实时更新+组合面板(开发中)
- [强制实体移除](entity-force-remove-unsafe.md) — 当 Entity.remove/discard/ChunkSource 全被 override 时的最底层绕过方案：Unsafe + EntityLookup 内部 Map 反射
- [模块包名冲突](module-export-package-conflict.md) — run/mods 旧 jar 导致 Modules X and Y export package Z 崩溃的排查与修复
- [模组技术谱系](modding-tech-landscape.md) — 技术深度 6 层分类、关键术语释义(Instrumentation/AT/TransformationService/VTable)、竞品强度分析
- [护甲图标生成规则](armor-icon-texture-rule.md) — 母模板=包边圈(圈外透明)、按部位独立取色(k-means)、胸甲包边用肩部(45,20)(45,24)两点RGB分上下、头盔完工覆盖阴影层
- [物品属性 modifier id 冲突累加丢失](item-modifier-id-collision-stacking.md) — 原版 AttributeMap 按 modifier id 去重，多件装备共用固定 id 会互相覆盖不累加；id 必须每物品唯一且稳定(复用已有)，改 setVanillaModifier/setAttr 两处
- [万能物品配置 itemcfg](itemcfg-universal-item-config.md) — Shift+U 动态 GUI + per-player 门控 + VTable 全局废除 + Curios 集成的架构与坑（GUI 开发参照）
- [Photon 纯代码粒子 API 与坑](photon-code-particle-api.md) — 8 坑：纹理完整路径/ARGB 色/ADDITIVE 亮度累积换 alpha 混合/billboard 非体积圆走 Model+ObjModelSource/simulationSpace Local vs World/反射构造 protected 发射器；调参别靠重启试错
- [bbmodel 转原版 ModelPart](bbmodel-to-modelpart-convert.md) — 加生物必读：group origin 是世界坐标、原版渲染 scale(-1,-1,1) 需 X/Y 与绕 X/Y 旋转取反、box_uv 需图集重排、up/down 面 V 翻转、Renderer scale 里 translate 用正数；辖界者(原全首者)转换已落地
- [NeoForge maven 离线构建](neoforge-maven-offline-build.md) — maven.neoforged.net 被 TLS 阻断时用本地仓库绕过：点号group缓存/完整.module/metadataSources gradleMetadata/neoform ArtifactManager 缓存路径
- [跨版本移植计划](cross-version-port-plan.md) — 当前工作版本 1.20.1(1.21.1 已停更)；目标 1.20.1 Forge 47.4.22；难点=网络层20+Payload重写/DataComponent改NBT/JavaAgent重写
- [辖界者 Boss 当前状态 + Warden 动画复用](warden-animation-reuse.md) — 新窗口接续必读：模型照抄 WardenModel、纯近战、中立反击、狂暴(半血/5秒)、属性=困难模板随难度缩放；涨跌多空=攻击×20%
- [自走棋棋子属性排查](auto-chess-piece-attributes.md) — 新会话接手：实体属性清单(21标准+辖界者基础值表)+挂载 API(EntityAttributeGate/Standardizer)+已定费用/星级(3合1)/倍率表；待用户给基础数字
- [受保护实体属性维护设施](entity-attribute-gate.md) — EntityAttributeGate(prot_前缀+调用栈鉴权)+AttributeInstanceMixin(防外部移除)；辖界者属性挂载+防御镜像；通用血量处理(EntityHealthLocator/applyDreamDamage)
- [实体属性编辑工具](entity-attribute-edit-tool.md) — 物品右键编辑 16 属性；本模组实体受保护写入、其他反射注入；踩坑(init加载/4参 mouseScrolled)
- [实体移除保护](entity-remove-protection.md) — YizxianMob 移除/新增总闸门：拦 Entity.setRemoved(所有移除汇聚点)+ServerLevel.addFreshEntity/addDuringTeleport，白名单=保存/死亡/本模组包；1.21.1 无 ServerLevel.removeEntity、维度传送直接 setRemoved 不走 remove
- [实体存在性通用加固](entity-presence-hardening-1-20-1.md) — 命门是「删掉后回不回得来」：自愈绝不能依赖官方加入入口；三层防线+身份被改致按 id 保护静默失效+自然清除缺口
- [免移除对抗测试不进 dev](anti-tamper-test-not-dev.md) — 对抗外部模组（omnimobs/终极骷髅/超级史蒂夫）的免移除/改血测试不要 runClient（dev 测不了外部模组），改完构建 jar 让用户部署生产环境测
- [辖界者免移除 agent 拦截](yizxianmob-remove-protection-agent.md) — 1.20.1 agent ASM 拦字段直写(FieldWriteAdapter)+服务端 Map.remove；坑=VerifyError/NoClassDefFoundError
- [绝妄生机（原禁疗）](vitality-severance.md) — 改名 anti_heal→vitality_severance；三层机制(永久配置/临时+叠加/属性驱动)；字段级禁疗=定位真实字段+回弹抵消；辖界者每次攻击+5%
- [实体生命值保护补强（1.21.1 历史）](conduction-health-protection.md) — 1.21.1 历史终态：SecureHealthClosure 外部表+传导限伤+MAX_HEALTH 保护；已改 1.20.1 混淆串方案(见 quanshouzhe-mhzy-defense-1-20-1)
- [字节码级血量接管防御](bytecode-health-takeover-defense.md) — 外部 Coremod/Instrumentation 改写 getHealth/isAlive 字节码注入，Java override 无法抵抗；对抗=每 tick 强制血量状态(catchSetTrueHealth 校正)
- [1.20.1 血量 DataParameter 移植差异+扫描扩展](120-blood-dataparameter-port.md) — ⚠️按类池 defineId+多模组按id直写→DataItem 对象匹配(acc==accessor)+SynchedEntityData.get 泛化读守卫+HealthChannels 手动 id(254)+死亡链第三方克制
- [1.20.1 辖界者血量操作级控制技术研究](quanshouzhe-mhzy-defense-1-20-1.md) — 新窗口必读：血量保护主文档（混淆串 SECURE_OBF+权威表+死亡链+agent）+ 生产修复/踩坑全记录
- [readAdditionalSaveData 借道篡改血量](read-additional-save-data-tamper.md) — 外部 mod 调 public readAdditionalSaveData 塞 NBT 绕过 setHealth 扣血丢弃+传导限伤改权威表血量；修复=isVanillaEntityLoadCaller 调用栈鉴权，必须放 super 之前
- [隐藏类藏血实体通用改血](hidden-class-health-tamper.md) — 隐藏类(AES/差值血量)定位失败→DREAM_ACCUM 累积→dreamDeathblow 死亡链；四坑(别清累积/判死用 isEntityDead/误判槽回退/别补 hurt)
- [模组部署：先删后复制](deploy-mod-delete-then-copy.md) — 部署 PCL mods 前必删旧 jar 再复制 build/libs 最新，保证百分百最新，勿怀疑版本
- [生产对抗 fantasy 改血](fantasy-anti-tamper-battle.md) — 2026-08-11 战果：藏名/权威表/鉴权容器/客户端钳制/不死守卫/自绘头顶血条；IMPL_LOOKUP 改表盲区未完全防御，已放弃转数值调整
- [omnimobs-Flashfur 藏血方案](flashfur-health-hiding.md) — 血量存外部 ProtectedWeakHashMap + AccessChecker(token+防反射/invoke帧)；已落地 ProtectedHealthMap
- [实体死亡移除与自定义掉落](entity-death-removal-drops.md) — 已改 vanilla 完整死亡链(die→super.die→LootTable→tickDeath 动画移除)；免移除保护表值>0 绝对生效
- [Git 远程仓库](git-remote-repos.md) — 1.20.1 两项目 GitHub 地址（yizhengtong/yizmodqzk1.20.1 + yizxianmod1.20.1）+ 推送命令
- [通用藏血 Map 检测+篡改](health-map-tamper.md) — 泛型判据定位藏血 Map<实体,Number> + unreflectSpecial 锁基类 put 绕过重写鉴权 + 同步改写多个 Map 绕过每 tick 拉回；已攻破 omnimobs Flashfur；⚠️某未知模组致 map 检测失效（待探究）
- [通用差值血量 DataParameter 检测+篡改](dynamic-health-accessor.md) — getHealth=normal-away 差值动态计算 + 独立 Boolean 死亡标记，双向行为验证识别 normal/away，增加 away 正确扣血；⚠️梦幻终焉截断 getHealth 致检测失效（已放弃兼容）
- [通用强制判死标记检测+篡改](death-marker-accessor.md) — 行为验证枚举 Boolean 设 true 看 isAlive 变 false，识别 coremod 软 getHealth 型模组的判死标记，直设 true 强制判死绕过软压/护甲/混淆串；涨跌多空改血第 3 分支
- [Forge 1.20.1 类加载与枚举](forge-classload-enumeration.md) — 单一 TransformingClassLoader+JDK17 移除 ClassLoader.classes+agent resolveBridge 借游戏 loader；多模组提前触发 self-attach 失败
- [GeckoLib 4.8.4 接入 1.20.1](geckolib-integration-1-20-1.md) — ⚠️2026-09-10 两只 Boss（邪狱龙/踏虚体）与 geckolib 依赖已全部移除，本文留作将来再接入的参考：离线装 jar + GeoModel 直写三资源路径 + 弹道空渲染器 NPE + YizxianMob 非 PathfinderMob
- [通用加密字符串藏血反解](encrypted-string-health-cipher.md) — 血量藏 String(加密 int)；XOR_ROT+solveKeyedRotation 反推+写探针确认；坑(密钥存源/满血坍缩/id不缓存)
- [JDK 17 GC 崩溃](jdk-17-gc-crash.md) — 生产环境 17.0+35 + -UseCompressedClassPointers 致 GC 线程崩溃（jvm.dll+0xc0000005）；GC崩溃+无Java frames+搜不到模组类=环境问题非代码，换新版JDK 17/21 去参数
- [第三方 SynchedEntityDataMixin 触发 Byte→Boolean](external-mod-set-byte-boolean-crash.md) — 死亡链走 SynchedEntityData.set 触发第三方 Boss 模组 mixin 的 Byte/Boolean 类型错乱；改第三方实体一律用 DirectHealthFallback 直写绕开 set
- [反射 Field.get 触发第三方 clinit](reflection-field-get-triggers-clinit.md) — 反射读静态字段会初始化声明类，扫全量类时引爆第三方库 <clinit>；用 Unsafe.staticFieldBase/Offset+getObject 绕过类初始化
- [实体彩色描边渲染方案](entity-lock-outline-render.md) — 1.20.1 锁定描边=FBO+全屏后处理(OutlineBufferSource 双写+通用描边接口)；坑=outputState切主缓冲/NDC quad/FILL_TARGET clearTask
- [流血系统](bleed-system.md) — 1.20.1 三属性(bleed_ratio/time/stack)+分4次16tick结算+流血伤害无视减免+展示Buff；坑=属性须挂载玩家
- [native 对抗 fantasy Unsafe 三源清零](native-health-vault-anti-fantasy.md) — 进行中：随梦骨头三命令攻击链 + NativeHealthVault 堆外方案 + 诊断结论(fantasy 未改元数据、槽值被己方写0疑回退镜像)；新会话接续见 handoff/next-work
- [Goal 隔 tick 陷阱](goal-tick-parity-trap.md) — Mob.serverAiStep 按 (serverTick+entityId)%2 隔 tick 跑 goalSelector.tick()，Goal 用 mob.tickCount % N 节流→约一半实体永不寻路/发呆、攻击间隔翻倍；附动画索引同步与星级 max_health 被 aiStep 打回 1 星两个连带坑
- [传导伤害 5 层减伤链](conduction-damage-reduction-chain.md) — 本模组实体自管 hurt 不走 vanilla 链→护甲/通用防御/法术防御/全减免/格挡全部失效；DamageReductionChain 统一 5 层，YizxianMob 与 QuanshouzheEntity 共用；改前置库必 publishToMavenLocal
- [生物配置组件化](creature-component-system.md) — 对标 1.21 DataComponent 自建 ComponentType/ComponentMap/ComponentPatch；容器 + InstanceEffectState 重构 + 原型/数据包双轨 + 属性·战斗·形态组件 + 技能派发 + 诊断总开关均已落地；仅普界者形态 phases 与存量全量迁移待做
- [铁斗士向前击飞与连续浮空](tiedoushi-launch-and-juggle.md) — 停 tick 式弹道（驱动必须走服务端 tick 事件）+ 顶点固定刷新（防越打越高）+ 弹跳底线（单次击飞不弹跳，第 2 次起才起伏）；姿态必须由服务端三包同步（客户端推不出来）；含自管 hurt 链漏 setLastHurtByMob 致玩家仇恨失效、攻击中不移动、动画提速 1.3 与 E 盘联接构建坑；**另含 09-21 生产回归定论：普通+多空双轨伤害（第三方生物只第 1 次掉血）、停 tick 不可拦调用点、通道 id 撞车（"玩家数据丢失"=id 0 被抢导致登录被踢、getCustomName 崩 CCE）与读侧对象类型守卫、GL_OUT_OF_MEMORY 误判为内存泄漏**
- [通道 id 撞车自愈](synched-data-id-collision.md) — `Duplicate id value for 0!`：第三方硬编码 id 0 的 accessor 与 Entity 共享标志同槽 → 实体构造失败（玩家登录被踢 / 拾取粒子崩客户端）；含 vanilla 字节码事实（define/defineId/DataItem/getItem、Entity.<init> 里 defineSynchedData 在 8 个 define 之后）、define 层冲突消解（原版通道优先）+ 读守卫补条目、真凶定位日志、误诊记录（defineId 重映射打偏、别用 PowerShell 改 Java 源）
- [外部藏血发现缓存](health-discovery-cache.md) — 🔴**已被取代（反面教材）**：用「发现结果落盘 + 按实体类就近发现 + 信任落盘负缓存」缩小发现范围换性能 → 造成「map 生命实体改不动」回归，已回退；性能坑的描述与首击耗时诊断行仍可参考
- [累加器型血量（派生血量）](accumulator-health-inverse-slot.md) — 血量 = 上限 − 累加器字段的实体（原型 `IAnimatedBoss`）：写回必须走那个字段（inverse 槽）、回血对抗按「逻辑血量升高」判定；⚠️`readLocated` 返回的已是逻辑血量，**别再套一次 inverse**（本模组真 bug：禁疗判反 → 自己扣血被回滚 = 「首次能改、之后锁死」）；含反向槽钉回、门控误判止血（容差 5%→1% + 双向复核 + 还原）、上限 b 补齐
- [开发好/生产坏：反射名 + 玩家闸门](dev-prod-reflection-and-player-gate.md) — 反射字符串常量不会被 reobf 重映射（开发 `DATA_HEALTH_ID` / 生产 `f_20961_`）→ 只写 official 名会在生产静默返回 null（表现为「对外血量不刷新」）；规则=双名/类型三级解析 + 全模组一处口径 + 失败必吼；含「改血线对玩家只走普通伤害轨」的公共闸门与 SRG 名查法
- [藏血发现：共享枚举 + 后台快照](health-discovery-background-scan.md) — 现行方案：`HealthDiscovery` 一次全类路径枚举三套判据 + 后台守护线程快照 + 只缓存判据结论/字段句柄（值每次现读）+ 启动预热；`HealthTier` 发现分层＝**常规实体缓存、非常规生命值实体一律现场扫描**（行为判据：写回落地+2 tick 未被拉回才升级常规；被拉回/回读未落地/命中门控即粘性非常规；30s 复验+结构指纹）；含强度 7 条自检清单、首击 1385ms 与每 2.7s 重扫刷屏的日志证据、id 0 撞车 `Byte→Pose` 崩溃归因与 `defaultFor` 漏 POSE 的防线补洞、下游 jar 丢 refmap 的部署坑
