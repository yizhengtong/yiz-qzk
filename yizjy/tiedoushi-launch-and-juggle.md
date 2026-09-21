---
name: tiedoushi-launch-and-juggle
description: "铁斗士向前击飞（停 tick 式弹道 + 顶点固定刷新 + 弹跳底线 + 双包姿态同步）与配套修复（玩家仇恨失效、攻击中不移动、动画提速 1.3）；含三属性配置、工作方块可编辑项与本次踩到的构建环境坑（E 盘联接 ERROR_UNTRUSTED_MOUNT_POINT）"
metadata:
  type: project
---

# 铁斗士向前击飞与连续浮空控制（1.20.1）

2026-09-21 完成。**改击飞前先读这篇**，里面每个"想当然的做法"都是踩过的坑。

## 一、击飞实现：停 tick 式弹道

`net.minecraft.client.yiz.core.LaunchController`（前置库 yizmodqzk）：

- **不走原版 `knockback()`**：逐 tick 把实体 `move()` 到理想弹道点上（带碰撞），位置/速度完全由控制器给。
- **飞行期间目标自身 tick 被停掉**（`LivingEntityMixin` 在 `tick()` HEAD 取消 + `shouldStopTick`）：
  AI、重力、摩擦、着火/药水、**第三方模组的每 tick 逻辑**全部不跑。
  这比"只拦 AI"彻底得多，但代价是——
  **驱动源不能用实体自身 tick 回调**：必须走服务端 tick 事件（`LaunchTickHandler` → 服务端 `TickEvent.ServerTickEvent` END）。
- **玩家例外**：`ServerPlayer` 的 tick 与连接/同步强绑定，停掉会破坏客户端权威移动；
  玩家走"只发姿态包"分支（见第四节），不接管任何状态。
- 释放即恢复 tick；释放时补 `StatusEffectDispatcher.clearType(KNOCKBACK)`（飞行期间计时器不会自行递减，不清会残留冻 AI）。
- 死亡/被移除/换维度/超时（`totalTicks + 40`）都会释放，不会把实体永久卡在停 tick 状态。

### 三个控制属性（都由攻击者携带）

| 属性 | 含义 | 默认值（未配置时） |
|---|---|---|
| `knockback_attack` | 触发概率 % | 0 |
| `knockback_height` | 击飞高度（格） | 1.0 |
| `knockback_distance` | 击飞水平（格，沿「攻击者→目标」连线） | 1.5 |
| `knockback_time` | 总时长 tick（**上抛 + 下落**；顶点在 2/3 处） | 10（0.5 秒） |

- 触发路径：受击派发（`LivingEntityMixin` → `StatusEffectDispatcher.dispatchToTarget`）按攻击者 `knockback_attack%` 掷骰；
  技能等确定性场景可 `applyDirect`（100%，见 `StatusEffectDispatcher.applyDirect`）。
- **`knockback_damage` 已弃用并移除**（属性注册/编辑器/语言/tizMod 挂载/派发结算全清）；
  老存档加载会打一行 `Ignoring unknown attribute 'yizmodqzk:knockback_damage'`，无害，存档重存即消失。
- **0 语义坑**：`readAttr` 把 0 当"未配置"→回落默认值，所以**把 `knockback_distance` 设 0 做纯垂直上抛是无效的**（会变 1.5）。
  要支持"显式 0"得改成读 `prot_` 修饰符是否存在来判定"配过没有"。

### 手感参数（可调）

- 弹道分两段：**前 2/3 减速上升（顶点速度为零）+ 后 1/3 加速下落**（`APEX_FRACTION = 2/3`，客户端姿态用同一比例，必须一致）。
- **顶点固定**：`apexY` 在一个击飞会话内不变，刷新**绝不抬高** → 这是"能无限连续控制"又不"越打越高"的关键。
- **弹跳底线** `BOUNCE_FLOOR_RATIO = 0.35`：下坠到离地 35% 高度就反弹，**不落地**，形成浮空起伏。
- **单次击飞不弹跳**：`allowBounce` 首次施加为 false（干净的一上一下、正常落地），
  **只有同一会话内被再次命中（refresh）才置 true** —— 用户明确要求"第 1 次不产生上下动画，第 2 次起才产生"。
- 连击刷新：目标已在飞行中又被命中 → 以当前位置为新起点重排一个周期、重置计时与方向；
  距上次刷新 < `REFRESH_MIN_GAP = 4` tick 的命中不刷新（防技能每 tick 一跳把弹道打散）。
- 会话存活判定：最近一次命中 ≤ 1 个周期 → 允许弹跳续命；停手后正常落地释放。

## 二、姿态动画：必须由服务端发包，不能靠客户端推

**坑（踩过两次）**：停 tick 后客户端拿不到可信的垂直速度，自身物理又会先于服务端落地（低弹道尤其明显），
靠"看实体位置/速度"反推姿态必然失效。第一版这样写 → 完全不触发；改按插值位置推 → 被上一次击飞的残留高度骗到，1 tick 就复位。

**正解**：`S2CLaunchFxPayload` 三个时机都由服务端发：

| kind | 时机 | 客户端行为 |
|---|---|---|
| `KIND_START`(0) + `holdUntilEnd=true` | 施加 / 刷新 / 弹跳 | 起算时间轴；**已有状态时保留当前角度并直接进入保持态**（不回落重播） |
| `KIND_END`(1) | 释放（落地/死亡/超时） | **按 entityId 清理**（不要求实体还在客户端，否则玩家重生后姿态永远挂着） |

- 姿态时序：前 2/3 从 0°→90°（smoothstep，顶点恰好 90°），后 1/3 保持 90°；落地瞬间回正。
- **朝向**：目标**面朝击飞来源**（`faceToward`，逐 tick 跟随），配合渲染侧局部 X 轴 **`+90`** 后仰
  → 结果是"向后倒下、双腿朝着来源"。
  ⚠️ 符号依据：原版把它放平用的是 `PlayerRenderer` 游泳姿态 `Axis.XP.rotationDegrees(-90)`（那是**俯卧**），向后倒取反号。
  用户实测反馈过"背朝来源倒了"——**朝向公式与符号是两处独立的坑，改之前先用日志确认是哪一处错**。
- 渲染注入：`LivingEntityRendererLaunchTiltMixin` 在 `setupRotations(...)` **TAIL**（此时 poseStack 已按实体朝向旋转到位、原点在脚下，
  绕局部 X 就是"以脚为支点向后倒"）。描述符必须是
  `setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V`。
- 客户端兜底（防姿态挂死）：实体已死/已移除 → 立即回正；`elapsed < 0`（实体重建 tickCount 回绕）→ 回正；超时 → 回正。
- **玩家分支**：只发姿态包 + 一次**显式原版击退** `target.knockback(0.4F, dx, dz)`。
  为什么必须显式补：本模组棋子直接调 `target.hurt()`（不走 `Mob.doHurtTarget`），**原版近战击退本来不会发生**。
  除此外玩家不承担任何额外效果（不接管位置/速度、不加控制计时、不结算伤害）。

## 三、仇恨与移动的两个"自管链副作用"坑

1. **玩家攻击铁斗士不产生仇恨**：`YizxianMob.hurt()` 对受保护实体是**完全自管链路，从不调 `super.hurt()`**
   → 原版 `setLastHurtByMob(攻击者)` 从未执行 → `getLastHurtByMob()` 恒为 null → `TiedoushiRetaliateGoal` 永远不启动。
   怪物之所以还会被打，是 `NearestAttackableTargetGoal(Enemy)` 兜住了；**玩家只有反击 goal 一条路径**，所以只剩玩家不触发。
   修法：自管 hurt 里显式补 `setLastHurtByMob` + 玩家时 `setLastHurtByPlayer`（基类一处，所有棋子受益）。
   👉 **通用教训：任何"自管 hurt/死亡链"都要清点原版副作用清单（仇恨记录、击退、无敌帧、掉落……），漏一个就是一个诡异 bug。**
2. **攻击中原地不动/挥空**：`TiedoushiMeleeGoal.tick()` 原来"进入攻击距离就 `navigation.stop()`"，
   而击飞每周期把目标推开约 1.8 格、攻击距离只有 3 格 → 原地挥空。
   修法：移动判定改为「超出攻击距离 **或** 正在攻击（且距目标 >1 格）→ 持续寻路接近」；
   攻击中重寻路间隔收紧到 5 tick（`ATTACK_REPATH_INTERVAL`），普通 10 tick。
   用户已给攻击动画做了脚步位移，所以边打边压上去没有动画违和。

## 四、节奏与配置

- **攻击/动画整体提速 30%**：`TiedoushiModel.ANIM_SPEED = 1.3F`（用 1.20.1 的
  `HierarchicalModel.animate(state, def, ageInTicks, speed)` 重载，不动 Blockbench 关键帧）；
  实体的伤害关键帧/技能窗口按同一倍率折算（`TiedoushiEntity.SPEED_SCALE`）——
  **这两处必须同步改**，否则伤害会落在错误的动作帧上。
  当前：攻击间隔 39t（原 51）、三段伤害关键帧 12/22/32、技能 15+15、技能动画 40t→≈30.8t。
- **铁斗士当前配置**（可由数据包 `data/yizxianmod/yiz_creature/tiedoushi.json` 覆盖）：
  击飞概率 100、高度 2.0、水平 1.8、时间 24t（1.2 秒）；战斗状态移速 +0.15（0.23→0.38，脱战移除）。
- **战斗移速 modifier 写法**：名字用 `yizxianmod:` 前缀 + 固定 UUID。
  前缀让 20 tick 的 `AttributeStandardizer` 审计把它当"家族自身 modifier"保留（`FAMILY_MODIFIER_PREFIXES`），
  固定 UUID 保证幂等不叠加；且 `MOVEMENT_SPEED` 本身不在审计表里（`applyVanillaDifficultyScale` 只注册 MAX_HEALTH）。
- **工作方块（属性编辑台）**：可编辑项来自 `EditableAttribute.getAll()`（GUI 是滚动列表，增删条目安全）。
  击飞相关现有 6 项：击飞(攻) / 击飞(防) / 击退免疫 / 击飞时间 / 击飞高度 / 击飞水平。
  ⚠️ **改属性显示名或新增属性要同步三处：`EditableAttribute` + `YizAttributes` + 语言文件（zh_cn/en_us）**——
  本次就漏了语言文件，工作方块里显示成原始 key（编辑被"文件未读"拦下后没重试，静默丢失）。

## 五、调试手段（很有用）

- 诊断项 `launch`（`YizDiagnostics.LAUNCH`，默认开，`/yiz diag` 可关）：`施加 / 刷新 / 弹跳 / 刷新间隔过短忽略 / 释放`，
  客户端侧还有 `接收 / 刷新（保持姿态）/ 复位 / 到点回正`。
- **判读要点**：`施加 == 释放`（无泄漏）；`顶点Y` 在一段会话内**必须恒定**（一变就是爬升 bug）；
  `客户端到点回正` 应为 0（一出现就是结束包没到/被漏发）。
- 用**玩家攻击生物**当调试替身：玩家走同一条弹道+刷新+弹跳路径，但不停 tick，改参数后能立刻看到效果。

## 六、构建环境坑（Windows + 本机）

- `C:\Users\Administrator\.gradle` 与 `.m2` 都是**指向 E 盘的目录联接**；跨会话/服务会话遍历会撞
  `ERROR_UNTRUSTED_MOUNT_POINT`（"The path cannot be traversed because it contains an untrusted mount point"），
  表现为 wrapper 建不出 `wrapper/dists/...lck`、`mavenLocal()` 找不到前置库。
- 绕过（不改工程文件）：
  `GRADLE_USER_HOME=E:\.gradle` + `--init-script` 注入 `maven { url 'file:///D:/ZM/yizgzq/1.20.1/local-maven-repo-120' }`
  （脚本见 `临时校验/local-repo-init.gradle`）。用户的交互式终端能穿过联接，所以他自己跑通常不需要这些。
- 流程仍是：前置库 `build publish`（FG 的 `reobfJar` 挂在 assemble 上，`build` 产物即 SRG）→
  清 `E:\.gradle\caches\forge_gradle\deobf_dependencies\net\minecraft\client\yiz\yizmodqzk` → 下游 `build` → 部署。
- **部署别用 `Copy-Item "src" "a\","b\"`**（PowerShell 的 `-Destination` 不收数组，会先删旧件再复制失败，
  造成 mods 目录空窗）——逐个目标 `Copy-Item ... -Destination`。

## 七、生产回归一轮（09-21）：三 bug 根因定论 + 通道 id 撞车防线

1. **第三方生物「只有第 1 次造成伤害」**：不是伤害被吞，而是**双通道口径不一致**——本模组的多空伤害走
   `TotalHealthOverride` **直写血槽**，铁斗士近战走**自管 `hurt` 链**；对 `EntityHealthLocator` 定位不到槽的第三方实体
   （日志 `[EHL] 定位失败(无槽)`，例 `net.mcreator.colossus.entity.ColossusEntity`）直写通道静默失效，
   于是只有走 hurt 的那一次生效。**定论：铁斗士改「普通伤害 + 多空属性」双轨**（同辖界者）——
   普通伤害 = 面板值走 hurt，多空 = 面板值 ×40% 走 `applyDreamDamage`；玩家例外：**只吃普通伤害**（技能也不额外叠多空），
   击飞对玩家只给原版 `knockback(0.4F)` + 倒下动画。
2. **飞行途中血量显示不同步**：`YizxianMob.enforceSecureHealthState()` 是 tick 驱动，停 tick 后不再跑。
   修法：抽出 `EntityTickMaintenance`（每 tick 必跑的维护清单，停 tick 时由弹道手动调用）+ `LaunchTickBridge`（弹道每 tick 回调实体自身维护）。
3. **「部分生物保护 tick」停不住**：试过拦 `ServerLevel.tickNonPassenger` 调用点（`ServerLevelTickStopMixin`），**必须回滚**——
   第三方实体自带的每 tick 状态机被冻住后不再结算伤害，复现成"只有第 1 次造成伤害"。
   👉 **停 tick 只能停「实体自身的 tick」，不能停任何调用点。**
4. **「玩家数据丢失」实为登录被踢**：`Duplicate id value for 0!`（`SynchedEntityData.define` → `Entity.<init>`）——
   某模组在 vanilla `Entity.<clinit>` 前抢占通道 id 0，之后任何实体构造定义 `DATA_SHARED_FLAGS_ID`(id 0) 即抛
   → 玩家实体建不出来 → `lost connection: 无效的玩家数据`（极易误判成存档损坏）。
   修法：`SynchedEntityDataDefineIdMixin` 对非 `Entity` 类拿到 id 0 时按**类名哈希**确定性地改分配到 200+ 保留段
   （必须确定性，否则端间通道错位）。⚠️ **必须排除 `entityClass == Entity.class`**——第一版没排除，把原版共享标志通道
   也挪走了 → `SynchedEntityData.get` 拿不到 DataItem → `baseTick` NPE 崩客户端。
5. **渲染崩 `ClassCastException: Float cannot be cast to Optional`（`Entity.getCustomName()`）**：非 Entity 类拿到 id 1/2
   = 与 `DATA_AIR_SUPPLY`/`DATA_CUSTOM_NAME` **同槽**；该通道 id 在客户端/服务端分配不一致时，同步包会把 Float 写进
   `OPTIONAL_COMPONENT` 的 DataItem（`assignValues` 是无检查强转）。修法双保险：①`SynchedEntityDataMixin` 读守卫从
   "数值类型"扩到**对象类型**（OPTIONAL_* → `Optional.empty()`，另含 COMPONENT/ITEM_STACK/BLOCK_STATE/BLOCK_POS/DIRECTION/COMPOUND_TAG）；
   ②`defineId` 对 id<3 的非 Entity 类打 WARN **点名类/模组**（只记录不改动：1/2 重映射会挪动原版槽位语义）。
   👉 **通用认知：1.20.1 的通道 id 是「类池 + 加载顺序」决定的全局可变状态，跨端漂移必然导致同槽类型错乱。**
6. **「30G 内存不足崩溃」不是 Java 堆泄漏**：崩溃报告 `Memory: 1192 MiB / 2336 MiB up to 30720 MiB used`，
   真错是 `GL_OUT_OF_MEMORY ... Failed to allocate memory for buffer object`（显存/驱动侧），叠加 `-Xmx30720m` 巨堆。
   建议 `-Xmx` 收到 8–10G 再观察。
7. **血槽缓存自愈**：`EntityHealthLocator` 增 `invalidate(LivingEntity)`；`TotalHealthOverride` 记 `READBACK_FAILS`，
   连续 2 次回读失败即作废该实体槽位缓存并 WARN（原来是永久错槽）。
8. 玩家侧铁斗士数值：**怒击**（对同一目标每次命中攻速 +4%、上限 40%、换目标归零，`RAGE_STACKS` 通道 + 模型动画同步提速）；
   近战 = `pierceInvulnerabilityDamage` 直伤 + 多空 40%，技能起手对玩家跳过。

相关：[[goal-tick-parity-trap]] [[entity-attribute-gate]] [[attribute-display-name-sync]] [[deploy-mod-delete-then-copy]]
