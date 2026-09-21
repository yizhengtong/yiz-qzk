---
name: nuyi-phantom-family-piece
description: 怒翼（nuyi）—— 一费幻翼类飞行棋子：把原版幻翼 AI 搬到 YizxianMob 上的四个硬点（重力在 travel 里/目标搜索盒要高/碰撞伤害节流不能靠无敌帧/导出的部件坐标是世界摆位不要再叠加原版渲染补偿）。加飞行类生物必读。
metadata:
  type: project
---

# 怒翼：原版幻翼 AI 搬到受保护棋子上的落地要点（2026-09-22）

## 结论先行
- 实体 `NuyiEntity extends YizxianMob`（**不是** `extends Phantom`）。选 YizxianMob = 要本模组全套防护（血量权威表、防外部改血/清零、防 TP、防速度注入、防击退、属性标准化、星级与描边）；代价 = 幻翼的飞行与 AI 得自己搬（约 250 行，逐段照抄 1.20.1 官方映射源码）。
- 目标选择：**玩家 + `Enemy` 接口敌对生物**（原版幻翼只打玩家）。本模组棋子都不实现 `Enemy`，所以怒翼不会锁自家棋子、铁斗士的 `NearestAttackableTargetGoal(Enemy)` 也不会打怒翼。
- 数值：一费档；生命模板 12（标准表一费 24，此处按用户指定 12）、攻击模板 4 = 碰撞伤害设计值；碰撞伤害走 `ATTACK_DAMAGE`，节流 1 次/秒/目标。

## 四个硬点（踩过才知道）

### 1. 重力在 `LivingEntity.travel` 里 ⇒ 飞行必须整体覆写 travel，且**不能调 super**
- 1.20.1 的 `d2 -= 0.08` 在 `LivingEntity.travel` 内部，原版 `FlyingMob.travel` 靠**完整覆写**绕开它（不是靠 `setNoGravity`）。
- 我们的 `YizxianMob.travel` 会调 `super.travel`（水面不沉逻辑），所以子类覆写时**不能**调 super，否则怒翼一路坠地。
- 速度来源 = `NuyiMoveControl` 每 tick 直接写 `deltaMovement`（`vec3.add(target.subtract(vec3).scale(0.2))`）；**0.2 这个插值系数是幻翼的"飘"手感，别改成 1.0**。

### 2. 目标搜索盒必须够高：`getBoundingBox().inflate(16, 64, 16)`
- 幻翼在目标上空 20~40 格盘旋；`NearestAttackableTargetGoal`/`Level.getNearestAttackableTarget` 的搜索盒是等边 `inflate(d,d,d)`，d 取 FOLLOW_RANGE ⇒ 高空时目标落在盒外，**永远扫不到人**（这就是为什么原版幻翼自己写了目标 goal 而不用现成的 NearestAttackableTargetGoal）。
- 照抄原版：`getEntitiesOfClass(LivingEntity, inflate(16,64,16), 玩家||Enemy && cond.test(this,e))` + 按 Y **降序**取最高目标。
- `TargetingConditions.DEFAULT == forCombat()`（同一个静态实例）；`test(attacker,target)` 内含 canAttack / canAttackType / isAlliedTo / 视线判定；`range(-1)` = 不判距离。`TargetingConditions` 是可变的（`range()` 改自身），**每个 goal 用独立实例**。

### 3. 碰撞伤害的节流必须自己判 —— 不能指望 `hurt` 的无敌帧
- 基类 `YizxianMob.doHurtTarget` 是**破无敌帧**写入（先 `target.invulnerableTime = 0` 再 `super.doHurtTarget`，供多段攻击用），
- 所以「每 tick 撞到就 doHurtTarget」= 每 tick 4 点（80 点/秒）；
- 现行判据：`target.invulnerableTime > 0` 就跳过 ⇒ **每目标最多 1 次/秒**（原版 `hurt` 命中后会把无敌帧置 20）。
- 目标判定用静态 `TargetingConditions.forCombat()`（不带 range）⇒ 自动排除同队、和平难度、创造/旁观；再叠加「同类不互撞」「不吃自家召唤者（`ChessUnitTable.getOwner`）」。

### 4. Blockbench 导出的部件坐标是"世界摆位" ⇒ 渲染器不要再叠加原版渲染补偿
- 导出件（`gui/实体项目/怒翼.java`）的部件全部挂在根节点、y≈17~20（标准模型空间里脚底 = y24），**即已经浮在碰撞箱里了**；
- 原版 `PhantomRenderer.scale` 的 `translate(0, 1.3125, 0.1875)` 是给原版 `PhantomModel`（部件摆在 y≈0）补偿管线里的 `scale(-1,-1,1)` + `translate(0,-1.501)` 用的；套到本模型上会把怒翼整体压到碰撞箱下方约 0.9 格；
- 但 `setupRotations` 里的 `mulPose(Axis.XP.rotationDegrees(entity.getXRot()))` **要保留**：俯仰角由 MoveControl 每 tick 写进 xRot，俯冲/爬升的机体姿态全靠这一句。

## 其它落地细节
- 扇翅公式 `f = (id*3 + ageInTicks) * 7.448451F * π/180`，翼 `zRot = ±cos(f)*16°`、尾 `xRot = -(5+cos(2f)*5)°`；`TICKS_PER_FLAP = ceil(24.166098)`。模型（`NuyiModel.setupAnim`）与客户端扇翅声（`NuyiEntity.tick`）**共用同一公式**，改一个必须改另一个。
- 姿态照抄：`createBodyControl()` 返回 `yHeadRot = yBodyRot; yBodyRot = getYRot()`；`LookControl.tick()` 空实现 —— 否则机体朝向会与原版转身插值打架。
- 原版"怕猫"彩蛋默认**关**（`SCARED_OF_CATS = false`，代码保留，一只猫不该废掉棋子的俯冲）。
- 无摔落伤害：`checkFallDamage(...)` 空实现（同 `FlyingMob`）；`onClimbable()` 恒 false。
- 锚点 NBT 键用模组前缀 `YizNuyiAnchor`（原版幻翼用 `AX/AY/AZ/"Size"`，直抄会与第三方撞键），读档校验坐标范围后再采纳。
- 碰撞箱 0.9×0.5（照幻翼，翼展不进碰撞箱），`MobCategory.MISC` + `.clientTrackingRange(8)`；`finalizeSpawn` 里把锚点设在出生点上空 5 格（否则默认 `BlockPos.ZERO`，出生就往世界原点飞）。
- 蛋图标：拿原版 `assets/minecraft/textures/item/spawn_egg.png`（16×16 灰度模板，浅灰=蛋身主色 / 深灰=斑点副色）映射到怒翼暗绿 `#2C491B` + 亮绿 `#64B64A` 脚本生成；走 `item/generated` 单纹理（不用原版 layer0/layer1 染色）。**注意本项目既有生物蛋图标其实是"生物头脸图"（铁斗士那张），怒翼这张是占位，用户可自行替换。**

## 数值口径（用户确认 2026-09-22）
- 模板 12 血 / 4 攻；难度 HARD 1.0 / NORMAL 0.75 / EASY 0.5 ⇒ 12/9/6 血、4/3/2 攻；
- 星级（1-3 费档）倍率 ×1/×1.5/×2.25 ⇒ 2 星 18 血 6 攻（普通难度 13.5/4.5）、3 星 27 血 9 攻（普通 20.25/6.75）；
- `applyVanillaDifficultyScale()` **必须调**：它同时登记 `SecureHealthClosure` 权威最大生命值与 `AttributeStandardizer` 标准，是防护链的一环，不能因为"想固定 12 血"就跳过。
- 一费其它固定项（取自 `auto-chess-standards`）：armor 4、spell_defense 4、damage_block 0、damage_reduction 0、conduction_cap 100、attack_strength 0、spell_power 100、life_regen_rate 0.1（随星级）、movement_speed 0.23、max_mana 80、mana_regen 4、follow_range 24（仇恨距离）、invincibility_mult 16。

## 待办 / 可选项
- 怒翼暂无生物特性组件（铁斗士有 `knockback_immunity`）：要"免疫外部动量"就在 `YizxianMod.registerTiedoushiProfile` 旁加同 id 原型 + 数据包 JSON。
- 碰撞伤害目前只走普通伤害轨，**不做**涨跌多空百分比真伤（要加就照铁斗士 `dealAttackDamage` 的双轨写法）。
- 星2→星3/星1→星2 合成配方已加（`data/yizxianmod/recipes/nuyi_star*`）；铁斗士目前**没有**配方，若要求一致就删掉这两个 json。

## 相关
[[bbmodel-to-modelpart-convert]] [[auto-chess-piece-attributes]] [[client-animation-server-sync]] [[warden-animation-reuse]] [[conduction-health-protection]]
