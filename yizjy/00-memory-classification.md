---
name: memory-classification
description: 记忆存放分类守则——遇到需长期保留的内容，先分类再决定放哪里（安全/操作限制→settings/permissions/hooks；每会话遵守的项目规则→CLAUDE.md；任务相关的事实/进度/经验→Auto Memory）。本文件受保护，禁止删除。
metadata:
  node_type: memory
  type: feedback
  protected: true
---

> ⚠️ **受保护文件**：本文件是 yizjy 记忆库的「存放分类守则」，由用户亲自确立。**禁止删除、归档、合并**。`memory-maintenance` 清理时必须跳过本文件。它是记忆库的第一个子文件，确保分类准则始终在场。

# 记忆存放分类守则

遇到需要长期保留的内容，**先分类，再决定放在哪里**：

1. **必须被确定性执行的安全限制或操作限制**
   → 先建议使用 settings、permissions 或 hooks；**未经用户确认不要直接修改**。

2. **每个会话都应该遵守的项目规则、约束和工作习惯**
   → 写进 `CLAUDE.md`（工作区根）或合适的 `.claude/rules` 文件；保持简短，一条一项。

3. **只有相关任务出现时才需要读取的事实、进度、经验和资料**
   → 写进 Auto Memory（D 盘 yizjy），并按主题整理。

## 检查动作（维护时附带执行）

检查现有 `CLAUDE.md`、规则文件和记忆库，把疑似放错位置的内容列出来并给出迁移建议，**用户确认后再修改**。

## 自我维护（确保本准则不被删除）

本文件靠三重保障维持持久：
1. 文件名 `00-` 前缀 + `MEMORY.md` 索引置顶，排序始终第一；
2. frontmatter `protected: true` 标记；
3. `memory-maintenance` skill 明确声明跳过本文件。

若某次维护发现本文件丢失，应立即按本守则重建。
