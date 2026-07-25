# Memory Index

- [记忆存放分类守则](00-memory-classification.md) — 先分类再存放：安全限制→settings/hooks；项目规则→CLAUDE.md；任务相关→Auto Memory。受保护，禁删。
- [No magic number hacks](no-magic-number-hacks.md) — 全场/无限制用正确 API，别塞魔法数字
- [属性改名三处同步](attribute-display-name-sync.md) — 改属性显示名必须同步 lang+ItemAttr+Editable 三处，改前 grep
- [HUD 位置持久化](hud-position-config.md) — hud_positions.json 的位置/格式/换算/被清空排查
- [GUI 背景偏移 1px](gui-bg-offset.md) — AbstractContainerScreen 拼装面板必须 leftPos-1/topPos-1，否则 slot 点击差 1px
- [Mixin 1.21.1 踩坑](mixin-gotchas-1-21-1.md) — @ModifyVariable/ExpressionValue 因 refmap 缺失不可用、LiquidBlock 双重陷阱 getShape+createLegacyBlock
