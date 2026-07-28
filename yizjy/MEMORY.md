# Memory Index

- [记忆存放分类守则](00-memory-classification.md) — 先分类再存放：安全限制→settings/hooks；项目规则→CLAUDE.md；任务相关→Auto Memory。受保护，禁删。
- [No magic number hacks](no-magic-number-hacks.md) — 全场/无限制用正确 API，别塞魔法数字
- [属性改名三处同步](attribute-display-name-sync.md) — 改属性显示名必须同步 lang+ItemAttr+Editable 三处，改前 grep
- [HUD 位置持久化](hud-position-config.md) — hud_positions.json 的位置/格式/换算/被清空排查
- [GUI 背景偏移 1px](gui-bg-offset.md) — AbstractContainerScreen 拼装面板必须 leftPos-1/topPos-1，否则 slot 点击差 1px
- [Mixin 1.21.1 踩坑](mixin-gotchas-1-21-1.md) — @ModifyVariable/ExpressionValue 因 refmap 缺失不可用、LiquidBlock 双重陷阱 getShape+createLegacyBlock
- [自定义容器 Menu 的坑](container-menu-pitfalls.md) — ItemStack.CODEC 不能编码 EMPTY、客户端容器不反向同步、虚拟槽用绝对坐标、EditBox 失焦
- [穿墙轮廓共面合并](outline-render-coplanar-merge.md) — 相邻方块中间分割棱消失要靠"共面接缝判定"，不是去重也不是渲染配置；附 EdgeKey 碰撞避坑
- [vanilla 暴击 baked 换算](crit-damage-vanilla-baked.md) — 跳劈已把 1.5x 算进 amount，叠加 CRIT_DAMAGE 要用 /150 换算不是 /100；且 consume 必须在伤害块开头取出，否则标记残留误加暴击
- [热数据别走 PlayerDataAPI](hot-data-no-persistent-api.md) — 每 tick 写的数据会让 set 全量解析+全量 S2C 同步整个 root；退出即弃的数据改纯内存+事件驱动下发
