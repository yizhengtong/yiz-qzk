# yiz 工作区项目指令（dsh / WSL 环境）

> 本文件供 dsh（运行于 WSL）使用，路径为 WSL 风格：`/mnt/d/...` = Windows 的 `D:\...`。
> 与 Proma 侧 `CLAUDE.md` 内容等价，仅路径风格不同。

## 记忆库（双库 + 主题路由）

本工作区有**两个记忆库**，按会话主题**二选一**，不要同时读：

- **模组记忆库**（默认）：`/mnt/d/ZM/yizgzq/yiz1.21.1/yizjy/`，主索引 `MEMORY.md`
- **股票记忆库**：`/mnt/d/ZM/stock-memory/`，主索引 `MEMORY.md`

**路由规则**：
1. 每次会话开始（或用户切换话题时）判断当前主题。
2. **默认读模组记忆**（无脑选模组工作）。
3. **仅当用户明确提及股票/量化相关**（命中下面任一关键词），切换读股票记忆库，**且不读模组记忆**。
4. 读对应库的 `MEMORY.md` 恢复上下文。

**股票主题关键词**（命中任意一个即切股票记忆）：
股票、量化、炒股、行情、K线、涨停、跌停、持仓、仓位、买入、卖出、交易、A股、港股、美股、期货、基金、大盘、指数、开盘、收盘、涨幅、跌幅、市值、市盈率、技术分析、均线、MACD、KDJ、RSI、回测、策略、收益、止损、止盈、仓位管理

- 用户偏好：记忆 md 不放 C 盘系统盘，统一放 D 盘。
- 维护记忆（写/改/清理/分类/加索引）时，遵循 `memory-hygiene` skill 的 8 条纪律，**写入时写进当前主题对应的记忆库**（模组→yizjy，股票→stock-memory）。
- Proma 侧 `.claude\memory\MEMORY.md` 仅是 SDK 自动加载的指针，指向 D 盘主库，不放记忆正文（dsh 无此机制，直接读 D 盘主库即可）。

## yiz mod 构建与验证

> ⚠️ 以下规则原在 Windows 环境验证。dsh 运行于 WSL，首次构建前需先验证 JDK、gradle wrapper、本地 maven repo 在 WSL 下的适配（见下「WSL 构建注意」）。

- yiz mod（库 `yiz1.21.1`）改动并 `./gradlew build` 编译通过后，**直接**启动下游 `/mnt/d/ZM/yizgzq/yizxian1.21.1` 的 `./gradlew runClient` 验证（后台运行），**不要每次问"要启动吗"**。启动后等约 90 秒查日志，确认进主菜单没崩溃再告诉用户可验证什么。仅当用户明确说"不要启动"或"等我"时才不启动。
- **改前置库 `yiz1.21.1` 的资源（assets 下的 json/png 等）后，必须重新 `./gradlew build` 前置库**——下游 runClient 加载的是前置库 jar 里的资源，源目录改动不进 jar 就不生效（F3+T 热重载也读 jar，无效）。只改下游 yizxian 自己的资源则只需 runClient。
- **Photon 特效库（已回退，勿再用）**：2026-08-04 试接入后因深改环境下 HDR 管线过曝不可控（任何颜色洗白），**已从下游 build.gradle 移除、代码回退自研 shader**。不要轻易为特效再引入 Photon；特效优先自研/vanilla 粒子。经验与坑见 `yizjy/photon-code-particle-api.md`。
- **bbmodel 加生物**：用 `/mnt/d/ZM/yizgzq/yizxian1.21.1/tools/quanshouzhe_convert.py` 把 Blockbench bbmodel 转原版 ModelPart（图集+代码）。关键坑（group origin 世界坐标、scale(-1,-1,1) 取反、box_uv 重排、up/down V 翻转）见 `yizjy/bbmodel-to-modelpart-convert.md`。原全首者 Boss 已改为「辖界者」（同人坚守者）：模型完全照抄原版 WardenModel 骨骼层级 + warden.png 纹理，动画套用原版 Warden（AnimationState+Pose.ROARING+实体事件+WardenAnimation 关键帧），**纯近战**（咆哮/音爆已移除），AI 中立（被攻击瞬间反击、跳创造/无敌、追击），狂暴计时（血≤600或战斗5秒，持续6秒+刷新），属性 800血/85攻。完整状态与方案见 `yizjy/warden-animation-reuse.md`（新窗口接续必读）。
- **maven.neoforged.net 当前被 TLS 阻断（2026-08-04 起）**：任何 `./gradlew` 构建/启动都依赖本地离线配置（`/mnt/d/ZM/yizgzq/local-maven-repo` + build.gradle 本地仓库段 + gradle/neoformruntime 缓存）。改库编译前先确认这些配置还在，**勿删 `local-maven-repo` 和 `dl-deps`**。完整方案与坑见 `yizjy/neoforge-maven-offline-build.md`；网络恢复后可移除 build.gradle 的本地仓库段。

## WSL 构建注意

- 本环境在 WSL Ubuntu 内，gradle 命令用 `./gradlew`（Linux 脚本），**不是** Windows 的 `./gradlew.bat`。
- 首次构建前确认：WSL 内 Java（`java -version`）可用；若 WSL 未装 JDK，可用 `/mnt/c/Program Files/Java/jdk-21` 的 Windows JDK，或 `apt install openjdk-21-jdk`。
- 本地 maven repo 在 `/mnt/d/ZM/yizgzq/local-maven-repo`（DrvFs 挂载盘），gradle 读它跨文件系统可能偏慢但可用；若构建异常，先对比 Windows 侧结果定位是 WSL 环境差异还是代码问题。
