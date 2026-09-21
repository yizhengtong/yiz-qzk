---
name: encrypted-string-health-cipher
description: "通用「加密字符串藏血」反解：血量藏 String DataParameter（加密 int 值 + setHealth 空方法 + getHealth 从串解密）。XOR_ROT 变换 + solveKeyedRotation 公式反推（密钥候选 KeySource 泛化，不再 {0,hash,-hash}）+ verifySscipherFollows 写探针权威确认 + 运行时等式匹配定位。配合涨跌多空(DREAM_PERCENT)百分比真实伤害。"
metadata:
  type: project
---

# 通用加密字符串藏血反解（2026-08-22 落地，2026-08-22 泛化去过拟合）

> 针对血量藏在**加密 String DataParameter**、`setHealth` 空方法、`getHealth` 从加密串解密的实体（如 super-steve）。
> 纯黑箱推断 + 实体身份特征密钥，不认前缀/类名/字段名。

## 模式特征（类型可混淆，行为不变）

- 血量存在一个 **String DataParameter**，加密值是**有符号 int 的十进制文本**（可能带前缀/分隔符，也可能与其它数字无分隔符拼接）；
- `setHealth(float)` 是**空方法**（普通改血直接无效）；
- `getHealth()` 从加密串解密：`h = bits( rotr( enc ^ k2, r ) ^ k1 )`，再 clamp 到 [0, 上限]。

## 反解方案（2026-08-22 泛化后）

1. **`XOR_ROT` 变换**（`EncodedValueCodec.Solution`）：`h = bits( rotr(enc ^ k2, r) ^ k1 )`，k1/k2/rot 是参数；Solution 增加 keySrc1/keySrc2 存「密钥源」，meta 序列化 k1s/k2s。
2. **密钥候选泛化**（`KeySource` 枚举 + `deriveKeyCandidates`）：候选从实体身份特征派生——ZERO/NEG_ONE/UUID_HASH/NEG_UUID_HASH/ENTITY_ID/UUID 四个 int 段/TYPE_HASH(descriptionId)/MAX_HEALTH_BITS(AttributeMap 基值)。不再硬编码 `{0, hash, -hash}`。
3. **`solveKeyedRotation`**（`BlackBoxInverseSolver`）：枚举 r(32)×k1，k2 由公式 `k2 = enc ^ rotl(raw ^ k1, r)` 唯一反推，k2 命中候选集则产出候选解（O(N×32)）。**只软预筛，不确认**。
4. **`verifySscipherFollows` 写探针是唯一权威确认**：写 encode(h+d) 的位型到 token 区间 → 读 getHealth 是否跟随 → 还原。因为「编码往返验证」是数学冗余（等式成立时 encode(h)==enc 恒成立），不能当过滤器。
5. **运行时等式匹配定位**：读写时不按 hash 文本/缓存区间定位加密值，而是 scanIntTokens 后找「解密后 == getHealth 的 rawInt」的 token。`extractTokenAfterHash` 已删。

## 关键坑（每个都踩过，别重犯）

1. **密钥缓存存「源」不存「值」**：XOR_ROT 的 key 每实体不同（若源是 UUID_HASH/ENTITY_ID 等实体相关源）。缓存只存 keySrc，读写时按当前实体 `resolve()` 重推具体 key，只复用旋转量（rot 才是不变参数）。旧缓存无 k1s/k2s 时 fromMeta 自动迁移为 UUID_HASH/NEG_UUID_HASH。
2. **满血坍缩（泛化后新坑）**：候选集含 MAX_HEALTH_BITS 时，满血实体 raw == maxHealth bits，取 k1=raw、r=0 则 k2 坍缩为 enc → 任何 enc∈候选集都假命中、真 maxbits 方案反而漏检。**靠写探针一票压制**（写计数器 token 不带动血量 → 拒绝）。
3. **accessor id 不能缓存**：每只隐藏类实体（自定义 ClassLoader 加载）的 String DataParameter id 不同。按等式匹配（串内含「解密后==当前血量」的 token）运行时重定位 accessor，不缓存 id。
4. **加密值正数无分隔符**：`scanIntTokens` 会把「前缀 + 正数加密值」拼成的超长数字当溢出跳过。已增强为**枚举纯数字后缀**（尾部 ≤10 位，能放进 int），不再依赖「前缀是 UUID hash」。
5. **rebuildToken 参数约定**：定位返回 `[值, start, end]`（3 元素），`rebuildToken` 要 `[start, end]`（2 元素）。传错会把「加密值（负数）」当 start 索引 → 写入静默失效。
6. **隐藏类缓存 key 要脱 `/0x`**（`stableClassName`）：隐藏类实体的 `getClass().getName()` 带 `/0x内存地址`，实例唯一。直接做缓存 key → 每只实例重测 + 地址复用后命中旧槽失效。用 `/` 前部分（稳定父类名）做 key，配合「写失败清缓存 + 负缓存失效」自愈。**这是本次四个实体（灰烬坚守者/湮灭构造者/无名术士/泽林变体）改不动的根因，Mobiaton 三只已恢复。**

## 配合：涨跌多空（DREAM_PERCENT）百分比真实伤害

- 新属性 `dream_percent`，**1 点 = 目标最大生命 1%**，走涨跌多空真实伤害链（绕 hurt、直改权威血量）。
- 指令 `/yiz sx mzdk <值>` 给主手物品加修饰符；近战命中 + 物品属性双触发。
- 对加密藏血实体：先反解加密串读真实血量，按百分比扣，再加密写回 → 能打死「setHealth 空方法 + 回血」的超高血量实体。

## 相关

- [[dynamic-health-accessor]] 差值血量 DataParameter（另一分支）
- [[flashfur-health-hiding]] 外部 Map 藏血（另一分支）
- [[health-map-tamper]] 藏血 Map 检测
- [[entity-presence-hardening-1-20-1]] 移除加固（同一对抗场景）
