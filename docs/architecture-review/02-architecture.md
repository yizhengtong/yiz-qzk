# 架构总览

## 目录结构

```
src/main/java/net/minecraft/client/yiz/
├── tizMod.java              # 服务端入口 @Mod
├── tizModClient.java        # 客户端入口 @Mod(Dist.CLIENT)
├── api/                     # ① 公开 API — 下游模组唯一入口 (YizModQZKAPI)
├── effect/                  # ② 效果框架（6维系统）
│   ├── AbstractEffect.java        # 基类：构造时自动注册
│   ├── EffectContext.java         # 效果执行上下文 (record)
│   ├── parent/ParentType.java     # 5大父类枚举
│   ├── perception/                # 感知方式（Item/Entity/Container/Custom）
│   ├── activation/                # 生效条件（EntityAttack/ProjectileHit/Passive）
│   ├── unlock/                    # 解锁管理器 + NBT持久化
│   └── rarity/Rarity.java         # 稀有度枚举（5级）
├── core/                    # ③ 核心基础设施
│   ├── registry/ModRegistries.java    # 效果注册表
│   ├── event/EffectEventBus.java     # 效果事件分发
│   ├── data/EffectDataLoader.java    # JSON数据驱动加载
│   ├── AttackTargetLock.java         # 攻击目标锁定
│   ├── PlayerClassSwapper.java       # Unsafe类指针替换
│   ├── ProtectedServerPlayer.java    # 无敌Player子类
│   └── asm/                          # ASM Agent引导
├── tool/damage/              # ④ 伤害系统
├── tool/health/              # ⑤ 健康修改系统（17文件）
├── tool/attribute/           # ⑥ 物品属性读写 (ItemAttributeHandler)
├── tool/SimpleCommandRegistry.java  # ⑦ 简易指令注册器
├── ui/                       # ⑧ 统一UI面板
├── attribute/                # ⑨ 多乘区属性计算 (ModifierStack)
├── bridge/                   # ⑩ 数据桥接接口
├── network/                  # ⑪ 网络同步
└── mixin/                    # ⑫ Mixin（仅3个）

agent/src/                    # 独立 Java Agent（ASM字节码改写）
```

## 模块间依赖关系

```
api/ ────────────────────────── 公开入口
 ├── effect/                  效果框架（注册/查询/解锁）
 ├── tool/health/             健康修改（Delta等）
 ├── tool/damage/             伤害计算（4种特殊伤害）
 ├── tool/attribute/          物品属性（7属性×3操作）
 ├── core/                    事件分发/注册表/数据加载
 ├── ui/                      天赋面板/物品信息
 ├── network/                 网络同步
 └── attribute/               多乘区计算引擎

下游模组 ──→ api/YizModQZKAPI ──→ 内部实现
```

## 效果框架 6 维度

```
AbstractEffect
├── id                    ResourceLocation
├── parentType            5种父类 (ECHO/INSCRIPTION/MANIFESTATION/ORIGIN/ASCENSION)
├── level                 整数等级
├── perceptionModes       OR逻辑 (Item/Entity/Container/Custom)
├── activationCondition   4种生效条件
├── rarity                5级稀有度
└── execute()             执行方法
```

效果调度流程：`getAllEffects() → checkPerception() → isUnlocked() → shouldActivate() → execute()`
