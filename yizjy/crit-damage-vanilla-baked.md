---
name: crit-damage-vanilla-baked
description: "vanilla 跳劈已把 1.5x 暴击 baked 进 amount，叠加自定义 CRIT_DAMAGE 要换算成 CD/150，否则重复算暴击"
metadata:
  node_type: memory
  type: project
---

`LivingEntityMixin` 处理伤害时，vanilla 跳劈（跳击/暴击）的 1.5x 倍率**已经 baked 进 amount**。此时要叠加自定义 `CRIT_DAMAGE` 属性使最终倍率 = `1.5 + CD/100`，必须换算：

- 目标：`amount_final = amount_vanilla × (1.5 + CD/100) / 1.5 = amount_vanilla × (1 + CD/150)`
- 所以追加系数是 `1 + CD/150`，即 `amount *= (1.0f + (float)(critDmg / 150.0))`。
- 用 `CritTracker.consume(pl)` 判断「本次伤害是否由原版暴击触发」。

**Why:** vanilla 已经把 1.5x 算进去了，再直接乘 `1 + CD/100` 会变成 `1.5 × (1 + CD/100)`，等于把暴击伤害也算了一份 1.5x，重复计算、数值虚高。`CD/150` 是把增量摊回 1.5 基底上的正确换算。

**How to apply:** 在 LivingEntityMixin 伤害钩子里，凡是要在「原版暴击已发生」分支追加 CRIT_DAMAGE，一律用 `/150` 换算，不要用 `/100`。只有玩家自身暴击未触发 vanilla 跳劈的分支才走 `1 + CD/100`。改这块前先确认走的是哪个分支。相关属性定义见 `YizAttributes.CRIT_DAMAGE`。
