# 评分细则

## 评分方法论

每维度 10 分制，加权汇总。权重按库模组特性分配——API 设计和可扩展性是前置库的核心竞争力。

---

## ① API 设计 — 9/10 ⭐

| 项目 | 评价 |
|------|------|
| 入口统一性 | ✅ 唯一入口 `YizModQZKAPI`，全静态方法，零依赖 |
| 命名一致性 | ✅ `damage/trueDamage/armorPiercingDamage` 规律命名 |
| 操作模式 | ✅ `get/set/add` 三件套统一 |
| 可取消性 | ✅ `DamageEvent` 支持 NeoForge EVENT_BUS 取消 |
| 文件大小 | ⚠️ 730 行，有膨胀趋势 |
| 空方法 | ⚠️ `refreshUI()` 为空实现，文档未说明 |

---

## ② 模块化与架构清晰度 — 7/10

| 项目 | 评价 |
|------|------|
| 分包合理性 | ✅ 按功能分包，Mixin 仅 3 个 |
| 核心理念贯彻 | ✅ 统一 UI 入口的设计类比 JEI，意图明确 |
| 神类问题 | ❌ EntityASMUtil 混入 8 项职责 |
| 死代码 | ✅ 已清理 weapon/talent |
| 包命名 | ⚠️ 根包 `client.yiz` 含 `client`，但包含服务端代码 |

---

## ③ 可扩展性 — 8/10

| 项目 | 评价 |
|------|------|
| 效果系统 | ✅ AbstractEffect + JSON 数据驱动 |
| 属性系统 | ✅ DamageAttributeRegistry 开放注册 |
| 指令系统 | ✅ SimpleCommandRegistry 一行注册 |
| 库模组定位 | ✅ 不实现具体效果是设计意图，非缺陷 |
| 武器/天赋 | ✅ 已清理冗余脚手架 |
| JSON 效果 | ⚠️ execute() 为空，数据驱动效果无行为 |

---

## ④ 内聚性与耦合度 — 6/10

| 项目 | 评价 |
|------|------|
| effect/ 包内聚 | ✅ 6 维度各自分包，内聚良好 |
| tool/health/ 管道 | ✅ 17 文件，清晰的三层管线 |
| EntityASMUtil | ❌ 低内聚，揉合 3 层系统 |
| AttackInterceptorMixin | ❌ 3 种不相关责任 |
| 跨包耦合 | ⚠️ EffectContext 贯穿几乎所有包 |

---

## ⑤ 错误处理与健壮性 — 7/10

| 项目 | 评价 |
|------|------|
| 多层防御设计 | ✅ 健康修改 3+1 层，禁疗 3 层，保护态 5 层 |
| Agent 加载 | ✅ 双策略回退（直接 + 子进程） |
| 状态保护 | ✅ finally 块恢复 invulnerableTime |
| 反射脆弱性 | ❌ DirectHealthFallback 依赖字段名启发式 |
| Unsafe 操作 | ❌ PlayerClassSwapper 极度 JVM 特定 |
| 异常吞并 | ⚠️ tryDirectAttach 用 catch(Exception) 吞错误 |

---

## ⑥ 性能与效率 — 7/10

| 项目 | 评价 |
|------|------|
| Delta 系统 | ✅ 零对象分配 |
| ThreadLocal | ✅ HealBan 标记线程安全 |
| 通道缓存 | ✅ HealthChannelScanner 按实体类型缓存 |
| UI 渲染 | ❌ getPlayerTalents() 每帧 O(n) 全表扫描 |
| 反射开销 | ⚠️ DirectHealthFallback 每次调用反射 |

---

## ⑦ 代码风格与一致性 — 8/10

| 项目 | 评价 |
|------|------|
| Javadoc 格式 | ✅ 一致的 @param/@return/@see |
| 文档语言 | ✅ 中文详尽 |
| Section 注释 | ✅ `// ====================` 惯例统一 |
| Record 使用 | ✅ 数据类使用 record (EffectContext, DamageResult) |
| 方法长度 | ⚠️ PlayerTalentUI.drawWindow() 60+ 行 |
| 文档可访问性 | ⚠️ 全中文文档限制非中文贡献者 |

---

## ⑧ 完整性 — 8/10

| 项目 | 评价 |
|------|------|
| 伤害系统 | ✅ 4 种特殊伤害 + 属性绑定 |
| 健康修改 | ✅ 3 层管道 + EntityActuallyHurt 保底 |
| 效果框架 | ✅ 6 维度 + JSON 加载 + NBT 持久化 |
| UI | ✅ PlayerTalentUI + ItemInfoUI |
| 物品属性 | ✅ 7 属性 × 3 操作 |
| 保护态 | ✅ 5 层防御 |
| 禁疗 | ✅ 3 层拦截 |
| 网络同步 | 🔧 正在修复（已实现包，待验证） |
| JSON 效果行为 | ❌ execute() 为空 |

---

## ⑨ 文档 — 8/10

| 项目 | 评价 |
|------|------|
| CLAUDE.md | ✅ 详细全面的架构文档 |
| Javadoc | ✅ 所有公开方法有文档 |
| 架构说明 | ✅ 类级 Javadoc 包含设计意图 |
| 过度文档化 | ⚠️ 简单 getter 也写了完整 Javadoc |
| 示例缺失 | ⚠️ 无下游模组集成示例 |

---

## ⑩ 安全性 — 7/10

| 项目 | 评价 |
|------|------|
| 服务端强制 | ✅ 所有修改有 isClientSide() 检查 |
| 防御深度 | ✅ 多层拦截难以绕过 |
| Unsafe 使用 | ❌ 内存层面操作存在安全隐患 |
| 反射访问 | ❌ 绕过 Java 访问控制 |
| 权限系统 | ❌ 无跨模组权限检查机制 |
