# 问题清单与改进建议

## 🔴 P0 - 必须修复

### 1. 网络同步缺失

**现状**: `UnlockManager` 的解锁数据仅存储在服务端 `HashMap<UUID, Set<ResourceLocation>>` 中。客户端 `PlayerTalentUI` 查询 `UnlockManager.isUnlocked()` 但数据不存在。

**后果**: 下游模组调用 `YizModQZKAPI.unlockEffect()` 后，天赋面板**永远看不到任何已解锁天赋**。

**已完成修复** (2026-05-19):
- 创建 `network/SyncUnlocksPayload.java` — S2C 解锁同步包
- 创建 `network/NetworkHandler.java` — 网络注册 + 发送工具
- `UnlockManager` 新增 `clearPlayer(UUID)` 方法
- `YizModQZKAPI.unlockEffect()` 自动发送同步包
- `tizMod` 注册玩家登录和死亡重生同步事件

---

## 🟡 P1 - 建议尽早修复

### 2. JSON 效果的 execute() 为空实现

**位置**: `core/data/EffectDataLoader.java:148-152`

```java
new AbstractEffect(...) {
    @Override
    public void execute(EffectContext context) {
        // JSON 加载的效果默认无自定义逻辑
    }
};
```

**后果**: 数据驱动方式注册的效果在调度链路完全通过后，执行环节什么都不做。

**建议方案**:
1. 在 JSON 中增加 `effect_type` 字段（如 `damage` / `heal_ban` / `command`）
2. EffectDataLoader 根据 type 生成对应的 execute() 行为
3. 或提供 `EffectExecutor` 接口，允许外部注册 JSON 效果的行为处理器

---

### 3. EntityASMUtil 神类拆分

**现状**: `EntityASMUtil.java` (345行) 混合了以下职责：

| 职责 | 行数估计 | 建议拆分 |
|------|---------|---------|
| Delta 管理 (add/get/set/remove) | ~60 行 | `DeltaManager` |
| 禁疗 ASM 注入 (applyHealBan/consumeFlag) | ~40 行 | `HealBanInjector` |
| 伤害效果开关 | ~20 行 | 保留在公共类 |
| ASM 回调方法 (specialGetHealth等) | ~80 行 | `HealthCalculator` |
| Agent 状态标记 | ~25 行 | `AgentStatus` |
| 统计/调试 | ~15 行 | 保留或删除 |
| 保护态 health clamp | ~10 行 | 移入 `PlayerClassSwapper` |

**建议**: 拆分为 3-4 个职责单一的类。

---

### 4. PlayerTalentUI 每帧全表扫描

**位置**: `ui/PlayerTalentUI.java:330-345`

```java
for (AbstractEffect effect : ModRegistries.getAllEffects()) {
    // 每帧遍历全部效果做 instanceof + UnlockManager 查询
}
```

**影响**: 200 个效果注册 × 60fps = 12,000 次迭代/秒。虽在 Java 上影响有限，但属于不必要的浪费。

**建议**: 在 `ModRegistries` 中维护一个 EntityPerception 效果的缓存列表，或只在解锁状态变化时重新构建 UI 数据。

---

## 🟢 P2 - 可选改进

### 5. 缺少 Debug 诊断指令

没有内置命令检查 Agent 加载状态、Delta 状态、HealBan 状态。建议增加 `/yizmodqzk debug` 指令。

### 6. 效果执行缺少前后事件钩子

`EffectEventBus.dispatchContext()` 没有执行前/后的事件回调，下游模组难以插入自定义逻辑。

### 7. AttackInterceptorMixin 职责过多

一个 Mixin 类混入了三种不相关职责：
- 攻击目标锁定
- 强制执行标签检查 (TRUE_DAMAGE/ARMOR_PIERCING)
- 属性绑定伤害/禁疗后处理

建议拆分或至少用不同的 `@Inject` 方法分离关注点。

### 8. HealBanConfig 的 Lock 机制

`HealBanConfig` 使用 `synchronized` 但未覆盖所有读写路径，存在竞态条件可能。

---

## 🗑️ 已清理（本次审查）

### 武器/天赋死代码

删除内容：
- `weapon/AbstractBaseWeapon.java` (205行)
- `weapon/MeleeWeapon.java` (81行)
- `talent/AbstractTalent.java` (157行)
- `attribute/WeaponStats.java` (77行)
- `ModRegistries` 中的 WEAPON_REGISTRY / TALENT_REGISTRY

原因：初始提交的自动生成脚手架，无任何功能代码引用。效果系统（`EntityPerception`）已完全覆盖天赋概念。
