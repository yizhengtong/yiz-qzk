---
name: anti-tamper-test-not-dev
description: 免移除/改血对抗测试不要在 dev 环境（runClient）启动——测不了外部对抗模组，改完直接构建 jar 让用户部署生产环境测
metadata:
  type: feedback
---

# 免移除/改血对抗测试不进 dev 环境

做辖界者「免移除」「改血对抗」（对抗 omnimobs 清除工具、终极骷髅灭神模式、Metapotent Flashfur、超级史蒂夫等外部模组）时，**不要启动 runClient 验证**——dev 环境（runClient）加载不了这些外部对抗模组，测了也白测。

**Why:** 用户是在生产环境（PCL + 正式 mods 目录）实测这些外部模组对辖界者的移除效果。dev 环境只有本模组，测不出对抗效果。

**How to apply:** 改完免移除/改血代码 → `./gradlew build` 构建 reobf jar → 复制到 `D:\ZM\yizgzq\1.20.1\jar` → 让用户自己部署到生产环境测。仅当改动涉及「进主菜单是否崩溃」这类纯本模组的构建验证时才 runClient（见 [[cross-version-port-plan]] 的构建验证守则）。
