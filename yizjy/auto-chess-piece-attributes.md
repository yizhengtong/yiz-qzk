---
name: auto-chess-piece-attributes
description: 自走棋棋子标准化——可提供给生物实体的属性排查清单 + 挂载机制 + 辖界者基础值参考（2026-09-10 起模组只剩辖界者）。新会话接手"排查哪些属性可提供给实体"必读。
metadata:
  type: project
---

# 自走棋棋子标准化：实体可用属性排查（交接）

> ⚠️ **2026-09-10**：踏虚体/邪狱龙及其星级蛋、渲染器、GeckoLib 依赖已从模组移除，当前只剩辖界者。下文凡涉及这两只的条目（表格列、9 个星级蛋、3 renderer、6 recipe）均为历史记录，实际现役只有辖界者一套（3 星级蛋）。

## 目标
新会话 AI 接手：**排查模组（1.20.1，yizmodqzk 前置库 + yizxianmod 下游）中有哪些属性可以提供给生物实体**（作为自走棋棋子继承的属性池）。用户随后逐一给每个费用的基本标准属性数值。

## 已定标准化定义（勿改）
- **稀有度→费用**：普通1/优秀2/精良3/史诗4/传说5/神话7（无6费）
- **星级**：初始1星，3个同级合成自动升1星，最高3星
- **倍率表**（升星后属性×）：1-3费(×1/×1.5/×2.25)、4费(×1/×1.5/×3)、5费(×1/×2/×6)、7费(×1/×2.5/×9)
- **属性缩放**：B类(百分比概率)随倍率不封顶；C类(施放频率)不随；D类(机制型)部分随

## 棋子实体属性池（核心排查结果）

### ① YizxianMob 统一属性骨架（21 个标准自定义属性，base 0）
文件：`1.20.1\yizxianmod\...\entity\base\YizxianMob.java` `addStandardCustomAttributes`(L190)
ATTACK_STRENGTH, SPELL_POWER, GENERIC_DAMAGE, MELEE_DAMAGE, RANGED_DAMAGE, DAMAGE_REDUCTION, DAMAGE_BLOCK, INVINCIBILITY_MULT, DODGE_CHANCE, LIFE_STEAL, ARMOR, SPELL_DEFENSE, VITALITY_SEVERANCE_RATE, VITALITY_SEVERANCE_TIME, FIRST_DREAM, CONDUCTION_CAP, SECURE_PULSE, ARMOR_PENETRATION, ARMOR_PENETRATION_FLAT, LIFE_REGEN_RATE, LIFE_REGEN_PCT

### ② 原版核心属性（各实体 createAttributes 加）
MAX_HEALTH, ATTACK_DAMAGE, MOVEMENT_SPEED, ARMOR, KNOCKBACK_RESISTANCE, FOLLOW_RANGE

### ③ 测试实体属性基础值表（applyEntityAttributes；踏虚体/邪狱龙列为历史参考）
| 属性 | 辖界者 | 踏虚体 | 邪狱龙 | 难度缩放 |
|---|---|---|---|---|
| 血量 | 400 | 410 | 525 | 是 |
| 攻击 | 50 | 155 | 210 | 是 |
| attack_strength | 60 | 60 | 60 | 是 |
| spell_power | 100 | 100 | 100 | 否 |
| life_steal | 10 | 10 | 10 | 是 |
| damage_block | 1 | 3 | 5 | 是 |
| damage_reduction | 25 | 25 | 45 | 是 |
| invincibility_mult | 16 | 24 | 24 | 否 |
| armor(自定义) | 15 | 30 | 30 | 是 |
| spell_defense | 15 | 30 | 30 | 是 |
| conduction_cap | 25 | 40 | 40 | 否 |
| max_mana/mana_regen | 200/20 | 150/15 | 200/20 | 否 |
| life_regen_rate | 1 | 25 | 25 | 否 |
| first_dream | 攻×20% | 攻×50% | 攻×70% | 派生 |
| movement_speed | 0.30 | 0.25 | 0.25 | 否 |
- 难度缩放：HARD=1.0 / NORMAL=0.75 / EASY=0.5；`scaleDifficulty(v)=max(1,v×mult)`

### ④ EditableAttribute 池（约 100 个可编辑属性，标准化覆盖池）
文件：`1.20.1\yizmodqzk\...\editor\EditableAttribute.java` BUILTIN(L39)
含：9 原版 generic.* + max_durability + 库属性（暴击/吸血/溅射/会心/渴攻/流血bleed/攻防点数/蓝条/减伤反击连击/移动/状态五系/挖掘/绝妄生机/涨跌多空）。支持 registerExtra 扩展。

### ⑤ YizAttributes 注册全量（2026-08-29 复核：114 属性，6 组，非"17 个"旧注释）
文件：`1.20.1\yizmodqzk\...\attribute\YizAttributes.java`
- 组1 辖界者需求 18：攻强/法强/全伤/近战/远程/减伤/格挡/无敌帧/闪避/吸血/护甲/法防/绝妄率/绝妄时/涨跌多空/灭在多空(dream_percent)/传导限伤(conduction_cap)/血量隐匿(secure_pulse)
- 组2 LivingEntityMixin 扩展 20：暴击率/暴击伤害/precision/连击/反击(率+值)/步高/击退免疫/投射物免疫/不死/蓝耗降/法伤加成/熔岩(时间+flat+减伤+flat)/水下(时间+flat)/破甲%+flat
- 组3 技能系统 11：冷却值/充能数/技能范围/技能间隔/攻速加成(冷却缩减)/连击(值+次)/伤害基础/法强系数/治疗基础/血量系数
- 组A 死属性 4：shield_value/heal_atk_coeff/heal_spell_coeff/max_sentries（已挂载未接线，可保留占位）
- 组B 玩家向 24：移速%/疾跑%/空中移速/跳跃力度/跌落(减免+减伤)/挖掘7/蓝条(最大法力/回蓝/百分比回蓝)/定量回血(life_regen_rate)/百分比回血(life_regen_pct)/破时(poshi)/破限(poxian)/弹射反射/无碰撞/攻击距离/自动攻击
- 组C 状态五系 23：stun/slow/freeze/shock/knockback ×(attack/defense概率+time+dmg) + shock_range/interval/count
- 组D 下游系统 14：溅射(半径/伤害%/衰减%)/会心(huixin)/渴攻(kegong)/流血(比例/时间/叠加)/多段跳(次数/高度)/召唤(上限/伤害%)/蓝耗(mana_cost/每秒)

### ⑥ addManaAttributes 法力 6（YizxianMob 骨架挂载，棋子技能用）
MAX_MANA/MANA_REGEN/MANA_REGEN_PCT/MANA_COST/MANA_COST_PER_SEC/MANA_COST_REDUCTION

## 属性挂载机制（棋子属性写入路径）
- `EntityAttributeGate.set(entity, attr, idKey, value)`：受保护写入口（调用栈鉴权，modid 白名单 yizmodqzk/yizxianmod），确定性 UUID `"yizmodqzk:prot_"+idKey`，value=0 即移除
- `AttributeStandardizer.registerStandard(entity, attr, idKey, protValue)`：守护，每20tick审计恢复被外部改的属性
- `NbtAttributeHelper`（物品 NBT `yizmodqzk:attrs`，attrId→double）+ `NbtAttributeAggregator.aggregate(Player)`（onPlayerTick 穿戴6槽聚合到实体）
- `InstanceEffectState`（每实例每玩家隔离）：owner 归属 + 效果开关 + `/yiz eff owner` 招聘绑定 → 棋子单位状态模型基础

## 自走棋/棋子代码现状
- **无 cost/rarity/star/UnitDefinition 数据字段**（未实现）
- 物品星级描边 `OutlineMarker`（NBT `yizmodqzk:outline` 0-5）可作稀有度显示参考
- YizxianMob 注释明确"统一属性骨架含稀有度模板会用到的全部战斗属性"

## 标准表（2026-08-29 已收齐，全部 13 项）✅
完整表见工作区 `workspace-files/.context/auto-chess-standards.md`。摘要：
- **★ 随星级倍率(3)**：max_health 24/40/80/150/200/400；attack_damage 4/8/12/25/45/90；life_regen_rate 0.1/0.2/0.5/1/2/6
- **每费固定**：attack_strength 全0；damage_block 0/0/0.4/0.5/1/3；damage_reduction 0/0/10/15/20/25；armor 4/6/8/12/16/20；spell_defense 4/6/8/12/16/20；conduction_cap 100/100/90/60/40/30；attack_range 攻击距离 2.5/2.5/3/4/4.5/5.5
- **仇恨距离（派生）**：`max(24, 攻击距离×1.6)` 最少24；当前 攻击距离×1.6=4~8.8 全<24 → 恒为24。攻击距离映射 attack_range、仇恨距离映射原版 FOLLOW_RANGE
- **统一默认**：movement_speed=0.23(原版僵尸移速)、max_mana=80、mana_regen=4、spell_power=100
- **下一步**：写棋子计划书（cost/rarity/star 数据字段 + EntityAttributeGate.set 挂载路径 + UnitDefinition 设计）

## 星级显示方案（2026-08-29 用户确认 + 修订）
- **体型不变**：原方案体型 1:1.2:1.4 已按用户要求移除（2026-08-29 实测后去掉，星级只靠描边区分，renderer 不叠加 scale）
- **描边**：`EntityOutline.register(Provider)` 在 `YizxianModClient.onClientSetup` 注册，按星级返回 白(1,1,1,1)/蓝(0.15,0.45,1,1)/金(1,0.84,0,1) **RGBA 4元素**（FillColorConsumer 访问 color[3]，3元素数组会 ArrayIndexOutOfBounds 崩溃）
- **描边无条件渲染**：LockOutlineRenderer 调度已去掉渴攻 gate（AFTER_SKY 无条件清 FBO + AFTER_ENTITIES 用 LockOutlineBufferSource.consumeHasOutline() 判断），星级实体存在即描边，不依赖锁定/充能

## MVP 实现完成（2026-08-29，3 星全做）✅
**前置库 yizmodqzk**：
- `tool/chess/ChessUnitTable.java`（新）：外部表（UUID→Entry{cost,star,owner,baseHealth,baseAttack,baseRegen}）+ 倍率表 multiplier(cost,star)（1-3费/4/5/7 四档）+ init（不快照）+ applyStar（首次快照 1 星基准，放大 3 项：MAX_HEALTH/ATTACK_DAMAGE base×mult、LIFE_REGEN_RATE 走 EntityAttributeGate.set idKey="life_regen_rate" 覆盖不叠加）+ writeState/readState NBT yiz_chess
- `mixin/ItemRendererStarMixin.colorFor` 新增 level 6=金(1,0.84,0)（0白/4蓝/6金 星级蛋描边）
**下游 yizxianmod**：
- `YizxianMob`：实现 OutlineEntity；DATA_CHESS_STAR/DATA_CHESS_COST DataParameter（defineSynchedData，COST 默认0=非棋子不描边）；getChessStarForRender/CostForRender（客户端渲染读）；getChessStar/Cost（服务端外部表读）；syncChessToClient；applyChessStarIfNeeded（aiStep applyEntityAttributes 后首次 tick 应用）；持久化 writeState/readState+sync
- `item/ChessSpawnEggItem.java`（新）：通用星级蛋（参数 entityType/cost/star），use() 生成实体→ChessUnitTable.init+syncChessToClient；onCraftedBy 写描边 NBT；outlineLevel() 1→0/2→4/3→6
- `YizxianMod`：星级蛋注册（2026-09-10 起只剩辖界者 3 个，cost=5），CreativeTab 输出带描边 NBT
- 3 renderer：QuanshouzheRenderer.scale 叠加星级；Taxuti/XieyulongRenderer override render() pose.scale
- lang 9 条 + 9 模型 json（复用现有蛋贴图）+ 6 合成 recipe（data/yizxianmod/recipes：3×星1→星2、3×星2→星3，每实体 2 个）
- **构建通过**：前置库 build+publishToMavenLocal → 下游 build；runClient 无崩溃进主菜单（修复过 FillColorConsumer RGBA 3元素越界）
- **修复（2026-08-29 用户实测反馈）**：
  1. **星级属性被还原**三处根因+修复：
     - `AttributeStandardizer` 每 20 tick 审计把星级值当「外部篡改」还原 → applyStar 后对 MAX_HEALTH/LIFE_REGEN_RATE **registerStandard 重注册标准为星级值**
     - `SecureHealthClosure.getMaxHealth` 优先读权威表，只改 vanilla base 血条不更新 → applyStar 后 **setMaxHealth 同步权威表**
     - `YizxianMob.enforceSecureHealthState` 每 tick 把 MAX_HEALTH 强制回 templateMaxHealth×难度（防篡改）覆盖星级值 → 星级实体权威 maxHealth 改用 `baseHealth×multiplier(cost,star)`
     - 修复后日志：3星辖界者血量 425/2400 稳定，AttributeStandardizer 还原 0 次
  2. **描边改 EntityOutline 门面 API**：移除 YizxianMob 的 OutlineEntity 接口实现，改在 `YizxianModClient.onClientSetup` 注册 `EntityOutline.register(Provider)` 按星级返回白(1,1,1,1)/蓝(0.15,0.45,1,1)/金(1,0.84,0,1)
- 待验证：游戏内星级蛋效果（描边/体型/属性放大/合成）；辖界者阶段变化 applyFormPhase 改 ATTACK_DAMAGE base 可能覆盖星级（MVP 暂不处理）

## 生产部署大坑：@Shadow 生产 SRG 崩溃（2026-08-29 修）
- **根因**：Mixin AP 的 refmap **只生成 @Inject/@Modify* 目标方法映射，不生成 @Shadow 成员映射**。生产环境（PCL/SRG 命名）@Shadow 按开发名找目标 → `InvalidMixinException @Shadow method X was not located` FATAL。旧 jar 全部 mixin 都规避 @Shadow（仅 SynchedEntityDataMixin 注释留证），新加的（流血/自动攻击/物品描边/流体破坏）首次进生产全崩。
- **修复**：新建 `yizmodqzk/mixin/MixinAccess.java`（反射按字段类型/方法签名定位，**不依赖 SRG 名**，dev/prod 通用）。public 成员用强转（reobf 字节码映射，最可靠）；protected/private 成员用 MixinAccess：
  - BleedMixin.getHealth → 强转 `((LivingEntity)(Object)this).getHealth()`
  - AutoAttackMixin.player(Minecraft.player public) → 强转；startAttack(private) → MixinAccess 反射
  - ServerDestroyBlockMixin.player/level(protected 字段) → MixinAccess.field 按类型
  - ItemRendererStar/GlowMixin.renderModelLists(protected/private 方法) → MixinAccess.invoke 按签名
- **验证**：生产还报 `youkaishomecoming requires farmersdelight`（缺依赖，非本模组）

## 生产部署致命坑②：build 产物是 official，必须显式 reobfJar（2026-08-29 修）
- **根因**：yizmodqzk/yizxianmod build.gradle **移除了 `finalizedBy 'reobfJar'`**（L270-272：jar 保持 official 供下游 dev 经 fg.deobf 引用，reobf 会覆盖导致下游 dev NoSuchFieldError）。所以 `./gradlew build` 产出 **official jar**（部署生产 SRG 环境 → `NoSuchFieldError: FLOAT`/`NoSuchMethodError`）。生产 jar 必须**显式 `./gradlew reobfJar`**。
- **正确发布流程**（两个项目都要）：
  1. `./gradlew build -x test --offline`（official，供 publish）
  2. `./gradlew publishToMavenLocal --offline`（official 到 mavenLocal，下游 dev 用）
  3. `./gradlew reobfJar --offline`（SRG，覆盖 build/libs，供生产部署）
  4. 复制 build/libs/*.jar 到部署目录
- **验证**：javap jar 看 vanilla 字段引用（SRG 应 `f_135029_` 而非 `FLOAT`）
- **⚠️ MixinAccess 必须放 mixin 包外**（`net.minecraft.client.yiz.util.MixinAccess`）：放 `net.minecraft.client.yiz.mixin` 包会被 Mixin 框架判为「defined mixin package」→ `IllegalClassLoadError ... cannot be referenced directly`。工具类一律放 util/tool 包。

## 2026-08-29 补充：缩放规则 v2 + 攻强/法强排查结论
- **缩放规则 v3（用户确认 2026-08-29，覆盖 v2）**：
  - **随星级倍率（仅 3 项）**：生命值 max_health、攻击力 attack_damage、每秒回血 life_regen_rate（升星按倍率表 ×1/×1.5/×2.25 …）
  - **其余全部每费固定·星级不变**：攻强/格挡/伤害减免/护甲/法防/移速/最大法力/法力回复/法强(默认100) 等；未指定=默认 0/默认值
  - 传导限伤 conduction_cap：用户**单独给标准化**（不走统一模板）
  - ⚠️ 上轮用户说"回血不受升级影响"，本轮更正：**每秒回血(life_regen_rate)随星级**；life_regen_pct(百分比回血)未指定，仍默认固定
- **攻强 vs 法强不是重复属性**（代码证据）：
  - ATTACK_STRENGTH 攻击强度（默认0）：`LivingEntityMixin:360-361` 所有攻击(普攻/近战)直接 ×(1+值/100) 伤害增幅；`QuanshouzheEntity:459` 技能物理直伤=atkStr。作用=物理普攻增幅%
  - SPELL_POWER 法术强度（默认100）：`PostSkillAttackTracker:51` 技能伤害=damage_base+spell_power×damage_spell_coeff/100；`QuanshouzheEntity:460` 涨跌多空百分比真伤强度=spellPower/100×系数×目标maxHP；`YizAttributes:562 getEffectiveSpellPower` ×(1+magic_damage/100)。作用=法术伤害基数+百分比真伤强度
  - 注意 EditableAttribute 显示单位不同：attack_strength="攻击加成 %"、spell_power="法术强度 点"
