---
name: cross-version-port-plan
description: 跨版本移植大计划的现状盘点：前置库 yizmodqzk + 资源模组 yizxianmod 的架构/资源/系统清单与移植注意点
metadata:
  type: project
---

# 跨版本移植计划（进行中）

> **⚠️ 当前工作版本（2026-08-12）：1.21.1 处于停更状态，先在 1.20.1 工作。** 新开发以 `D:\ZM\yizgzq\1.20.1\{yizmodqzk,yizxianmod}` 为准；1.21.1 前置库/资源模组不再开发。

用户发起跨版本移植大计划，将逐个点名「要移植哪些资源、哪些不要」。**目标版本：1.20.1 Forge 47.4.22**（`D:\ZM\yizgzq\1.20.1\{yizmodqzk,yizxianmod}`，Forge MDK 骨架，FG6+mixingradle，**Java 17**，official mappings）。守则：① 源 1.21.1 两项目**绝不可改**；② 所需图片/模型资源**只能复制**。本条目记录 2026-08-09 盘点现状 + 移植难点。**完整逐项资源清单与难点报告在会话级 note.md**（C 盘 session 目录 `.context/note.md`）。

## 移植标准（用户定，2026-08-09）

**数据持久化分「存档」与「全局」两级，均沿用现有 json 文件逻辑**（不做新存储方案）：
- **全局级**：`config/<modid>/*.json`（如 `config/yizmodqzk/hud_positions.json`、`entity_health_slots.json`、`startup-abolish.json`）。实现惯例：`FMLPaths.CONFIGDIR.get().resolve(...)` 或 `Path.of("config", modid, ...)` + Gson/JsonParser + `Files.readString/write`。放全局偏好/缓存/运行时状态。
- **存档级**：`saves/<存档>/<modid>/*.json`（如 `EquipmentStackPersist` equipment-stacks.json、`ItemStackSizeOverride` stacksize.json/enhance.json）。实现惯例：`Minecraft.getInstance().getSingleplayerServer().getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent()` + `resolve("<modid>")` + Gson FileReader/FileWriter + `ConcurrentHashMap` 内存缓存 + synchronized load/save。天然按存档隔离、删档即数据消失。1.20.1 下 `LevelResource.LEVEL_DATA_FILE` 路径 API 需核对（同包同 API，大概率兼容）。
- **已有参考**：[[hud-position-config]]（全局 hud_positions.json 格式与换算）、`EquipmentStackPersist`/`ItemStackSizeOverride`（存档级范例）。
- **⚠️ 存档根目录 API 差异（已验证 1.20.1 源码）**：`LevelResource` 在 1.20.1 完全存在且路径常量与 1.21.1 一致（`LEVEL_DATA_FILE`="level.dat"、`ROOT`、`PLAYER_DATA_DIR`="playerdata" 等）。但**取存档根的写法推荐用 1.20.1 的 `getWorldPath(LevelResource.ROOT)`**（参考范例，直接得 `saves/<存档>/`），不要照搬 1.21.1 的 `getWorldPath(LEVEL_DATA_FILE).getParent()`（虽也兼容但绕）。存档级 json 落盘统一 `<存档根>/<modid>/*.json`。

## 移植核心难点（1.21.1 NeoForge → 1.20.1 Forge 差异）

🔴 **网络层**：20+ Payload（前置12+资源8）`CustomPacketPayload+StreamCodec+IPayloadContext` → 1.20.1 `SimpleChannel+IMessage+NetworkEvent.Context` 全重写
🔴 **DataComponent**（1.20.1 无组件系统）：30+ 文件 `DataComponents.ATTRIBUTE_MODIFIERS/MAX_DAMAGE` → 改 NBT tag；`AttributeModifier` id 参数 1.21 是 ResourceLocation、**1.20.1 是 UUID**，所有构造签名都变
🔴 **附魔**：`ItemEnchantments`（1.20.5+ 才有）→ `EnchantmentHelper`；前置库 3 附魔 json 是数据驱动，1.20.1 改**代码注册 Enchantment 类**
🔴 **JavaAgent/ASM**：LivingHealthTransformer/VTableReplace 字节码目标按 1.20.1 类结构重写；`-XX:-UseCompressedClassPointers` 需保留
🟠 **属性注册**：`Registries.ATTRIBUTE` → `ForgeRegistries.ATTRIBUTES`；`RangedAttribute.setSyncable` 1.20.1 **无此方法**；`EquipmentSlotGroup` → `EquipmentSlot`
🟠 **DeferredRegister**：`create(Registries.X,modid)` → `create(ForgeRegistries.X,modid)`，`DeferredHolder` → `RegistryObject`
🟠 **客户端渲染**：`ItemDisplayContext`（1.20.5+ 才有）→ `ItemTransforms.TransformType`
🟠 **mixin 33 个**：目标类大多存在，方法签名逐条核（renderBackground/getMaxStackSize/playerDestroy 等）
🟡 事件总线 `NeoForge.EVENT_BUS`→`MinecraftForge.EVENT_BUS`、`net.neoforged.*`→`net.minecraftforge.*`、PlayerTickEvent 归 TickEvent 下、`ResourceLocation.parse/fromNamespaceAndPath`→`new ResourceLocation`
🟢 **可复制即用**：全部 png/json/bbmodel/animation/shader/lang/recipe/entity纹理/native DLL/tools 脚本

## 辖界者 1.20.1 完整移植进度（2026-08-09 深夜，进世界验证通过）

**✅ 完整移植并进世界运行**：
- **前置库**（1.20.1，60+ 类）：tizMod + YizAttributes 全量扩展（17→40+ 属性）+ LivingEntityMixin 完整（856行：delta通道/HealthDataBridge/getHealth注入/禁疗/无敌帧/减伤/传导限伤/SecureHealth兜底/onTick）+ 血量扫描体系（EntityHealthLocator/HealthChannelScanner/DirectHealthFallback/EntityActuallyHurt/EntityASMUtil 完整）+ 技能系统（EnhanceTagRegistry + 12 依赖类）+ 各注册表/追踪器 + **AttributeInstanceMixin 防移除（prot_ 属性外部清不掉）** + agent（HealthAgent/LivingHealthTransformer/HotSpotAttachLoader 同进程 self-attach/AgentBridge/AgentLoader + jarjar 嵌入）+ **/yiz remove 指令**（SimpleCommandRegistry + EntityRemovalUtil）
- **资源模组**（1.20.1）：辖界者实体 + 渲染 + 免移除 3 mixin + **实体属性编辑器**（物品/Menu/Screen/SimpleChannel C2S+S2C）+ YizxianMod/Client
- **发布 jar**：`D:\ZM\yizgzq\1.20.1\jar\{yizmodqzk,yizxianmod}-1.0.jar`（reobf SRG，含 agent jarjar）

### ⚠️ 1.20.1 特有踩坑（2026-08-09 补）
1. **ForgeGradle 编译产物默认 SRG**（类引用 `m_21051_`）；dev 环境官方映射 → **跨模组依赖必须 `fg.deobf`**（前置库 publishToMavenLocal + 资源模组 `implementation fg.deobf("...")`）；不要 flatDir 直引、不要复制 SRG jar 到 run/mods。
2. **`GuiEventListener.mouseScrolled` 1.20.1 是 3 参**（1.21 才加第 4 参）；Screen override 用 `(double,double,double)`。
3. **`Player.openMenu` 1.20.1 只有单参**（无带 data 重载）→ 菜单传 targetId 用独立 **S2C 包**（`S2CEntityTargetPayload`）+ Screen 静态缓存。
4. **`AttributeInstance.removeModifier(AttributeModifier)` 是 1.20.1 所有移除路径汇聚点**（removeModifier(UUID)/removeModifiers() 委派它）；防移除 mixin 拦它（返回 void，用 CallbackInfo 非 CIR）。
5. **`AttributeMap.attributes` 1.20.1 是 `Map<Attribute, AttributeInstance>`**（非 Holder）→ 实体属性编辑器 ensureAttribute 注入用 Attribute 直 key。
6. **`ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation)`** 按 id 查属性（替代 BuiltInRegistries.ATTRIBUTE.getHolder）。
7. **Agent self-attach**：JDK 21 需 `-Djdk.attach.allowAttachSelf=true` + `--add-modules=jdk.attach` + `-XX:-UseCompressedClassPointers`（资源模组 runClient jvmArg）；失败降级不影响血量扫描体系（EntityHealthLocator 不依赖 agent）。
8. **发布 jar = reobfJar**（SRG）：`build` 后显式跑 `reobfJar`，jar 引用变 `m_*_`；dev 环境用官方映射 jar。

### ⚠️ Forge 1.20.1 关键 API 差异（移植踩坑，已解决）
1. **`AttributeModifier` 构造器**：1.21 是 `(ResourceLocation, double, Operation)`；**1.20.1 是 `(UUID, String name, double, Operation)`**。所有 modifier 用确定性 UUID（`UUID.nameUUIDFromBytes` 由 idKey 派生）+ name 参数。
2. **`AttributeModifier.Operation` 枚举名**：1.21 是 `ADD_VALUE/ADD_MULTIPLIED_BASE/ADD_MULTIPLIED_TOTAL`；**1.20.1 是 `ADDITION/MULTIPLY_BASE/MULTIPLY_TOTAL`**。
3. **`RangedAttribute.setSyncable`**：1.20.1 继承自 `Attribute`（返回 `Attribute`，非 RangedAttribute），链式调用赋 `Supplier<Attribute>` 无碍；但**方法名在运行时是 SRG**（需 fg.deobf）。
4. **`Attributes.STEP_HEIGHT`**：1.21 才有，1.20.1 无（辖界者步高 2 已注释，后续自定义属性补）。
5. **`dropAllDeathLoot` 签名**：1.21 是 `(ServerLevel, DamageSource)`；**1.20.1 是 `(DamageSource)`**。
6. **`ResourceLocation` 构造**：1.21 用 `fromNamespaceAndPath/parse`；1.20.1 用 `new ResourceLocation(ns, path)`（弃用警告但可用）。
7. **`EntityType.Builder.build(id)`**：1.20.1 只接受简单 id 字符串（非 `modid:path`）。
8. **`getRecentPlayerAttacker`** 是辖界者**自有方法**（非 LivingEntity，1.21 才加），RetaliateGoal 用它没问题。
9. **`SynchedEntityData`**：1.21 用 `defineSynchedData(Builder)`；**1.20.1 用无参 `defineSynchedData()` + `this.entityData.define(...)`**。
10. **`VertexConsumer` 方法名 + 顶点提交**：1.21 是 `addVertex()/setColor()/setNormal()` 且 `addVertex` 自动提交前一个顶点；**1.20.1 是 `vertex()/color()/normal()`，必须显式 `.endVertex()` 收尾**（漏了顶点不渲染、屏幕上看不见）。`vertex(Matrix4f, float, float, float)` 用 `org.joml.Matrix4f`（`pose.last().pose()`），`color(int,int,int,int)`（0-255 非 float），`normal(float,float,float)`（3 参无 Pose）。
11. **自定义穿墙 LINES RenderType 需 AccessTransformer**：1.20.1 的 `RenderStateShard` 里几乎所有 StateShard 常量（NO_DEPTH_TEST/NO_CULL/COLOR_WRITE/MAIN_TARGET/NO_LAYERING/NO_TRANSPARENCY/TRANSLUCENT_TRANSPARENCY）都是 **protected**、`LineStateShard` 类也 protected（1.21 才 public）。加 AT 公开（SRG 名：NO_TRANSPARENCY=f_110134_、TRANSLUCENT=f_110139_、NO_LAYERING=f_110117_、MAIN_TARGET=f_110123_、COLOR_WRITE=f_110115_、NO_CULL=f_110110_、NO_DEPTH_TEST=f_110111_）+ build.gradle 取消注释 `accessTransformer`。shader 不靠 AT，用 `new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLinesShader)`（ShaderStateShard 类 Forge 默认 AT 已公开）。
12. **1.20.1 的 `NO_DEPTH_TEST` setup/clear 是空实现**（`DepthTestStateShard("always", 519)` 不主动 disableDepthTest，1.21 才主动）；穿墙渲染要自定义匿名子类 override `setupRenderState`/`clearRenderState` 主动 disable/enable 深度测试，否则「显示了但不穿墙」。
13. **`SpawnEggItem.getType(CompoundTag)`**：1.20.1 接受 NBT 不是 ItemStack → `egg.getType(stack.getTag())`。
14. **`Item.getMaxStackSize()`（无参）**：1.20.1 没有 `getDefaultMaxStackSize()`；取默认堆叠数用无参 `getMaxStackSize()`。
15. **`mouseScrolled` 3 参**：1.20.1 `(double,double,double)`，1.21 才加第 4 参。
16. **堆叠数覆盖（ItemStackSizeOverride）需同时改 3 层**：`ItemStack.getMaxStackSize()`（ItemStackMaxSizeMixin）+ `Slot.getMaxStackSize(ItemStack)`（SlotMaxStackSizeMixin）+ **`Container.getMaxStackSize()`（ContainerMaxStackSizeMixin）**。否则「放入箱子 99 变 64」「拾取合并只能 64」——根因是 `SimpleContainer.setItem`/`Inventory.add` 用 `Container.getMaxStackSize()`（默认 64）硬截断。**`Container` 是接口 default 方法，`@Inject` 报 "Injector in interface is unsupported"，用 `@Overwrite`**（需 `@author` 标签）。`@Redirect` 拦截 `SimpleContainer.setItem` 里的 `getMaxStackSize()` 时，target 要写 `SimpleContainer.getMaxStackSize`（javac 对接口 default 方法的内部调用生成 invokevirtual 到实现类，不是接口）。
17. **手动 `BufferUploader.drawWithShader` 渲染必须给 shader json 加 `blend` 字段**：1.20.1 的 `ShaderInstance.apply()` 会调 `this.blend.apply()`；json 无 `blend` 字段时 BlendMode 默认 **OPAQUE**（`apply()` 内 `opaque=true` → `RenderSystem.disableBlend()`），**覆盖**渲染器手动 `RenderSystem.blendFunc(SRC_ALPHA, ONE)` 的加法混合。闪电 arc/spark 这类靠 `col*intensity`（边缘 intensity=0 → 黑）做透明的 shader 就出「黑边+渐变蓝+无立体感」；surface 不受影响是因为它 fsh 里 `discard` 掉了边缘。修复=给 shader json 加 `"blend":{"func":"add","srcrgb":"srcalpha","dstrgb":"one"}`（加法）或 `"dstrgb":"1-srcalpha"`（半透明），与渲染器手动 blend 一致。改前置库资源后还要**清 fg.deobf 缓存**（`~/.gradle/caches/forge_gradle/deobf_dependencies/.../yizmodqzk`）否则下游 runClient 仍用旧 jar（版本号未变不自动 re-deobf）。

### ⚠️⚠️ Forge 1.20.1 跨模组依赖（最关键的坑，NoSuchMethodError 根因）
- **ForgeGradle 编译产物（`jar` 任务）默认是 SRG 映射**（类引用 `m_21051_` 等），不是官方名。dev 环境运行时是官方映射 → 直接引用 SRG jar 会 `NoSuchMethodError`。
- **正确链路**：前置库 `maven-publish` 发布 jar 到本地 maven → 资源模组 `implementation fg.deobf("group:artifact:version")` 从 mavenLocal 消费，fg.deobf 把 SRG jar 反映射成 official 缓存 jar（`~/.gradle/caches/forge_gradle/deobf_dependencies/...`）。
- **不要**用 flatDir + 直接 implementation（SRG jar 进 dev classpath → 运行时 NoSuchMethodError）。
- **不要**复制前置库 SRG jar 到 run/mods（dev 环境以官方映射加载 → 出错）。fg.deobf 依赖由 FG 自动以 classpath 提供，无需复制。
- 前置库已加 `maven-publish`，publish 目标 `local-maven-repo-120`（也可 mavenLocal）。改前置库后需 `./gradlew publishToMavenLocal`（或 publish），资源模组再编译。
- **1.20.1 引擎前缀**（鉴权类 ENGINE_PREFIXES）：`net.minecraftforge.` 替代 1.21.1 的 `net.neoforged.`。

## 源项目架构（1.21.1，移植参考——完整资源/物品/属性/mixin 清单从源码获取，不在此重复）

- **前置库 yizmodqzk**（`D:\ZM\yizgzq\yiz1.21.1`，289 java）：API/属性90+/伤害/血量/UI框架/技能/武器/WorldGui/自研shader。NeoGradle userdev 7.1.26，内嵌 JavaAgent(ASM)+native DLL。group=`net.minecraft.client.yiz`。
- **资源模组 yizxianmod**（`D:\ZM\yizgzq\yizxian1.21.1`，100 java）：内容模组（物品~50/辖界者/护甲/技能）。group=`net.minecraft.client.yiz.xian`。
- **核心系统（移植最难）**：JavaAgent ASM(HealthAgent/LivingHealthTransformer) + VTableReplace(需 -XX:-UseCompressedClassPointers) + EntityHealthLocator/SecureHealthClosure/EntityAttributeGate + WorldGui 空间GUI。⚠️Photon 已回退勿再用。

## 移植注意要点（待目标版本确认细化）

1. 构建插件：前置库 NeoGradle userdev 7.1.26 vs 资源模组 ModDevGradle 2.0.141，目标版本需换对应插件+Parchment
2. 离线仓库 local-maven-repo 是 21.1.230 的缓存，换版本需重备构件
3. JavaAgent 依赖 asm 9.8，字节码结构随 MC 版本变，LivingHealthTransformer/VTableReplace 需重查 target
4. native DLL 与 Java/MC 版本无关通常可复用
5. model/blockstate/lang/shaders/bbmodel 跨版本通用；enchantment/damage_type/data 组件 schema 需核对
6. API 重构点：DeferredRegister/网络/Menu/Attribute(DataComponent化)/CreativeModeTab/EntityType/Renderer 逐项调整
7. mixin target class/method 随版本改名，逐条核对

## 下一会话接续点（2026-08-09 深夜收尾）

**⚠️ 用户明确要求：任何地方不提到别的模组，只说技术原理**（代码注释/记忆/回复均不点名外部模组）。

**最近完成（进世界验证通过）**：
1. **实体属性编辑器**：物品+Menu+Screen+SimpleChannel(C2S 应用/S2C 目标同步)，data null 容错已修（1.20.1 无带数据 openMenu → S2C 包传 targetId）
2. **/yiz remove 指令**：SimpleCommandRegistry + EntityRemovalUtil（Unsafe/MethodHandle 强制移除）
3. **免移除深度强化**：新增 `ServerChunkCacheRemoveProtectionMixin`（拦 removeEntity，绕过 setRemoved 的外部直移除）+ EntityRemoveProtectionMixin(拦 setRemoved) + ServerLevelAddProtectionMixin + 辖界者 kill/heal/actuallyHurt override
4. **字节码级血量接管通用防御**（见 `bytecode-health-takeover-defense.md`）：外部系统用 Instrumentation 改写 LivingEntity 字节码覆盖 Java override → 辖界者 override baseTick+每tick 强制血量状态（catchSetTrueHealth 校正 vanilla 字段 + HealthChannelScanner 校正所有 Float 血量通道回表值）+ kill/heal/actuallyHurt override。**零依赖不针对任何外部系统**。
5. **强化参数**（可选，已改）：INVINCIBILITY_MULT 16→30、CONDUCTION_CAP 25→12（但用户指出非数值问题，字节码对抗才是关键）

**已新增（2026-08-09 本会话）——涨跌多空扫描广度扩展（A/B/C）**：
- 根因：1.20.1 血量在 DATA_HEALTH_ID DataParameter（无 health 字段）+ `SynchedEntityData.itemsById` 是 Int2ObjectMap 非数组（DirectHealthFallback 数组强转静默失效）→ 涨跌多空打不动血量存通道的实体（如梦幻终焉 UomWither）。
- A：DirectHealthFallback 改 `allDataItems()` 兼容 Map/数组 → 保底层恢复。
- B：`damageVanillaHealth/damageFloatChannel` 绕过 `SynchedEntityData.set()` 限伤直改 DataItem；`EntityASMUtil.addDelta` 第 3 步接入。
- C：`EntityHealthLocator.detectViaDataAccessor` 仿梦幻终焉 isHealthField 谓词扫 DataParameter 血量通道（kind="accessor"）。
- 编译+发布+runClient 进世界验证通过。详见 [[120-blood-dataparameter-port]]。

**已新增（2026-08-09 本会话）——黎玄纪元穿透分析与辖界者防御补强**：
- 分析：黎玄纪元（MCreator 模组，AT 公开 LivingEntity*+DATA_HEALTH_ID）高频多段 AoE（hurt+setHealth+entityData.set 直写通道）磨穿辖界者 400 血外部表；根因=辖界者 getHealth 表移除后回退 vanilla 通道被直写接管。
- 防御两项：①堵 getHealth 回退（服务端强读表，未注册=0）②拦截直写通道（onSyncedDataUpdated 检测 DATA_HEALTH_ID 直写校正回表值）。
- ⚠️ 发布坑：前置库 publish 到 local-maven-repo-120 下游不消费，必须 `publishToMavenLocal`（见 [[120-blood-dataparameter-port]]）。
- 新 jar 已部署正式环境 `D:\桌面\.minecraft\...\mods\`（旧 jar 备份 .bak-0308），待用户实测黎玄纪元是否仍能穿透。

**已锁定最终真相（2026-08-09）——辖界者被秒杀的真凶是 yizheng**：
- QZK-MYSTERY 日志铁证：`表值异常下降 391->321 (drop=70) 调用栈: LivingEntity.tick(m_8119_)`，每 ~15 tick drop 70。
- **yizheng-1.0.jar（com.minecraftyiz，另一 yiz 系 mod）** 的 `FallbackHealthTransformer` 注入 `UniversalHealthInterceptor` 到 vanilla `LivingEntity.tick()`，通过 `HealthManager.setExactHealth→MapHealthDetector.forceWriteMapHealth` 强制写辖界者血量进它自己的 EXTERNAL_HEALTH_MAP。
- **黎玄纪元/梦幻终焉均无关**（黎玄纪元被辖界者 override+CD 正常拦截每轮只扣 14；梦幻终焉已禁用）。
- 用户已禁用 yizheng（.jar.disabled），04:34 运行未复现。详见 [[120-blood-dataparameter-port]]。

**⚠️ 防御教训（2026-08-10）——改防御时删了"动态传导限伤"导致梦幻终焉穿透**：
- 用户自己改防御时把 hurt() 的动态传导限伤（`dynamicCap = max(3, baseCap×(0.3+0.7×ratio))` 残血收窄到 3 点）删掉换成**固定 cap**，且 CONDUCTION_CAP 25→12% 回退、INVINCIBILITY_MULT 16→30 回退 → 梦幻终焉能穿透。
- **"无敌帧 + 伤害上限被突破" = 防御数值/机制被弱化，不是攻击方变强**。梦幻终焉走的是目标 `hurt()`（`living.hurt(source, 10+random×5)`），防御链（getHealth 强读表/setHealth 黑洞/onSyncedDataUpdated 拦截/enforceSecureHealthState 清 isDead）都没坏。
- **教训：动态传导限伤（残血收窄）是防"高频磨血磨穿残血"的核心，不能删。** 固定 cap 残血时仍放行大额伤害。
- 已恢复三处（commit f4b4a22）：①hurt() 动态传导限伤 ②CONDUCTION_CAP 25→12 ③INVINCIBILITY_MULT 16→30。**恢复防御时注意 AttributeStandardizer.registerStandard 的 prot 值也要同步改回**（16/30、12/25 两处都出现）。

**待办（下会话）**：
- 用户完整实测：辖界者 vs 梦幻终焉（UomWither），确认不再被打穿
- 若需保留 yizheng：辖界者在 vanilla 层对抗其拦截器（override 层无效）
- 游戏内实测涨跌多空对梦幻终焉（UomWither）是否已能扣血
- 剩余 mixin 移植（AttackInterceptorMixin 等）
- 物品/护甲/武器/技能系统完整移植
- agent 完整链路验证

## 下一会话接续点（2026-08-15）

**方向转变**：用户决定**暂时停止提升「代码强度」**（对抗性改血/藏血检测），接下来做**体验化修改**（游戏体验，非对抗性）。

**已落地的通用改血四分支**（涨跌多空 `applyProportionalDreamDamage` 优先级）：
1. 藏血 Map（HealthMapRegistry，[[health-map-tamper]]）→ 2. 差值血量 DataAccessor（DynamicHealthAccessor，[[dynamic-health-accessor]]）→ 3. 强制判死标记（DeathMarkerAccessor，[[death-marker-accessor]]）→ 4. 字段级/数据层/delta 软压。

**三个待修复 Bug（均已记录）**：
1. 梦幻终焉 coremod 截断 getHealth → 差值检测失效（已放弃兼容，测差值实体需禁用梦幻终焉）；
2. ~~某未知模组 → 藏血 Map 检测失效~~ → **已修**（根因=全局一次性扫描缓存，见 [[health-map-tamper]] 的「已修复」节）；
3. 多模组提前触发 HotSpotVirtualMachine → agent self-attach 失败（attach 判空已加，根治走 loadAgent0）。

## 下一会话接续点（2026-08-16）

**已新增 3 只实体**（都继承 YizxianMob，全套血量保护；都主动攻击所有实体、创造玩家豁免、按攻击者累积格挡可选）：

1. **邪狱龙 `xieyulong`**（GeckoLib，飞行三技能火球/陨石/毒云）：525血/210攻/限伤4%/双抗30/减免45%/格挡5/无敌帧24/涨跌多空70%。
2. **踏虚体 `taxuti`**（GeckoLib，地面近战+orb球+黑色影子+传送）：410血/155攻/限伤4%/双抗30/减免25%/格挡3/无敌帧24/涨跌多空50%。
3. **山林首者 `shanlinshouzhe`**（**原版 ModelPart+AnimationDefinition，非 GeckoLib**，抡锤子 Warden 同人）：见 [[shanlinshouzhe-1-20-1]]，**进行中**（动画微调 + 战斗系统刚落地待实测）。

详见 [[geckolib-integration-1-20-1]]（两只 GeckoLib Boss）与 [[shanlinshouzhe-1-20-1]]（山林首者）。

## 下一会话接续点（2026-08-19）

**辅助物品全部移植完成（进世界验证通过）**：

1. **光明末影之眼 `bright_ender_eye`**：纯物品 + `EndPortalGlowRenderer`（手持时给周围末地传送门框画穿墙发光轮廓）+ 合成配方（荧石粉×8 围末影之眼）。
2. **光明指南针 `bright_compass`**：Shift+右键开配置 GUI（搜索物品/管理工作槽），右键扫描周围 128 格高亮工作槽物品对应的方块/生物/掉落物。全套 = BrightCompassItem + LightCompassMenu/Screen + CompassHighlightTarget + LightCompassHighlightRenderer + C2S/S2C 网络包。**1.20.1 无 PlayerDataAPI（AttachmentType），工作槽改 `player.getPersistentData()` 存档持久化 + S2C 包同步客户端缓存（LightCompassData）**。
3. **堆叠核心 `stack_core`**：铁砧左槽放目标物品、右槽放堆叠核心，取出后该物品 ID 最大堆叠数 ×2（封顶 99，最多 2 次）。= StackCoreAnvilHandler（AnvilUpdateEvent/AnvilRepairEvent）+ 前置库 ItemStackSizeOverride/ItemStackMaxSizeMixin。

**坑**：渲染/堆叠的 1.20.1 API 差异见上文「Forge 1.20.1 关键 API 差异」第 10-16 条（VertexConsumer endVertex、RenderStateShard AT、NO_DEPTH_TEST 空实现、Container 64 截断需 @Overwrite）。
