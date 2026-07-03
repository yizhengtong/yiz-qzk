# HUD 核心管理器 + 通用突进系统 + 突进 HUD 实现计划

## Context（为什么做这个）

yizxianmod 当前没有任何统一的 HUD 体系：唯一的 `TerraprismaRenderHandler.onRenderGui` 是泰拉棱镜调试 HUD（硬编码左上角 4,4，画 A~G 阶段剑密度），且代码注释已写「待后续统一 HUD 管理器」，属开发期占位，需要移除。

本次要建立**可复用的 HUD 框架**：玩家按 `DEL+ALT` 进入全屏编辑界面，把本模组所有 HUD 元素全部强制显示出来，用鼠标拖动位置、滚轮缩放、右键/中键切换显隐，位置/缩放/开关持久化到客户端本地 JSON。编辑器不暂停游戏，所见即所得。

同时把**突进（boost）能力从心之翅里分离出来**：当前突进数据（`AccessoryBoostData`，次数 0~3 + 恢复 80tick）与按键处理（`HeartWingsBoostHandler`）都硬编码绑死心之翅。心之翅只是当前唯一提供突进的来源，将来会有别的来源，所以现在就抽成通用突进系统。第一个接入新框架的 HUD 元素就是**突进 HUD**，用给定的 7 帧 8×8 菱形增长图（`png/123.png`）表达"下一次推进"的恢复进度。

---

## 已确认的范围决策

1. **范围**：框架 + 通用突进系统 + 突进 HUD（全做），并移除泰拉棱镜调试 HUD。
2. **编辑器**：不暂停游戏（`isPauseScreen()=false`），实时预览。
3. **可调维度**：每个 HUD 存 `{x, y, enabled, scale}`。
4. **7 帧图含义**：恢复进度 7 档量化（80tick → 7 档，菱形 1→7 增长），推进次数另行显示。
5. **突进抽象**：`BoostProvider` 接口 + 通用 `BoostData`，心之翅作为一个 provider。

---

## 整体架构

```
net.minecraft.client.yiz.xian
├── api/
│   ├── BoostData.java            【新】通用突进数据（取代 AccessoryBoostData 的突进部分）
│   ├── BoostProvider.java        【新】突进来源接口
│   └── BoostRegistry.java        【新】provider 注册表 + 活跃来源查询
│   └── AccessoryBoostData.java   【删/迁】逻辑搬入 BoostData（见迁移说明）
├── handler/
│   └── HeartWingsBoostHandler.java → BoostHandler.java  【改名+改造】hasHeartWings 改查 BoostRegistry
├── hud/                          【新包】
│   ├── HudElement.java           【新】抽象基类
│   ├── HudManager.java           【新】注册表 + RenderGuiEvent 分发 + 配置访问 + editMode 开关
│   ├── HudPositionConfig.java    【新】Gson JSON 持久化
│   ├── HudEditorScreen.java      【新】全屏拖拽编辑器（DEL+ALT 打开）
│   ├── HudDragState.java         【新】拖拽/缩放状态机
│   └── BoostHud.java             【新】突进 HUD（首个 HudElement）
├── item/HeartWingsItem.java      【不动】只管 elytra 飞行
├── mixin/MixinHeartWingsProtection.java  【不动】衰落/动能伤害免疫是心之翅特性，不属通用突进
├── render/TerraprismaRenderHandler.java  【改】删除 onRenderGui 调试 HUD 方法 + 残留订阅
└── YizxianModClient.java         【改】注册 HudManager/DEL+ALT/配置加载/BoostHud
```

---

## Part A — HUD 框架（新包 `hud/`）

### A1. `HudElement`（抽象基类）

```java
public abstract class HudElement {
    protected final String id;          // 唯一 id，如 "boost"
    protected final int defaultX, defaultY;
    protected final float defaultScale;

    protected HudElement(String id, int defaultX, int defaultY, float defaultScale) { ... }

    /** 逻辑尺寸（scale=1 时的占地），用于命中测试与选中框。动态尺寸的 HUD 给当前帧上界。 */
    public abstract int getLogicalWidth();
    public abstract int getLogicalHeight();

    /**
     * 绘制内容。调用方已做 PoseStack.translate(x,y)+scale(s)，本方法在 (0,0) 用逻辑尺寸绘制。
     * @param editMode true=编辑器内，必须用示例数据强制画出（无视运行时条件），让玩家能拖
     */
    public abstract void render(GuiGraphics g, boolean editMode);
}
```

**契约**：render 内部不读位置/scale（由调用方 PoseStack 处理），只读业务数据。editMode=true 时用示例值（如推进 2/3、恢复进度 60%）固定画出，且无视"是否在飞/是否有 provider"等运行时条件。

### A2. `HudManager`（注册表 + 渲染分发 + 配置访问）

```java
public final class HudManager {
    private static final Map<String, HudElement> ELEMENTS = new LinkedHashMap<>();
    private static boolean editMode = false;

    public static void register(HudElement e) { ELEMENTS.put(e.id, e); }
    public static Collection<HudElement> all() { return ELEMENTS.values(); }
    public static void setEditMode(boolean v) { editMode = v; }
    public static boolean isEditMode() { return editMode; }

    // 配置访问（未配置时回退到元素默认值）
    public static int getX(HudElement e)       { return HudPositionConfig.get(e.id).x(e); }
    public static int getY(HudElement e)       { return HudPositionConfig.get(e.id).y(e); }
    public static float getScale(HudElement e) { return HudPositionConfig.get(e.id).scale(e); }
    public static boolean isEnabled(HudElement e) { return HudPositionConfig.get(e.id).enabled(e); }

    // 渲染分发（订阅 RenderGuiEvent.Post）
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (editMode) return;                       // 编辑器接管，跳过普通分发，避免重影
        if (Minecraft.getInstance().screen != null) return;  // 有其它 Screen 打开时不画
        for (HudElement e : ELEMENTS.values()) {
            if (!isEnabled(e)) continue;
            drawElement(event.getGuiGraphics(), e, false);
        }
    }

    // 编辑器复用：editMode=true 时元素无视 enabled/条件强制画
    static void drawElement(GuiGraphics g, HudElement e, boolean editMode) { ... PoseStack translate/scale ... }
}
```

### A3. `HudPositionConfig`（Gson JSON 持久化，复用 `AnimConfigData` 模式）

路径 `config/yizxianmod/hud_positions.json`，结构：
```json
{ "_version": 1, "huds": { "boost": {"x":150,"y":200,"enabled":true,"scale":1.5} } }
```

```java
public final class HudPositionConfig {
    private static final Path PATH = Path.of("config", "yizxianmod", "hud_positions.json");
    private static final Map<String, Entry> HUDS = new ConcurrentHashMap<>();
    record Entry(int x, int y, boolean enabled, float scale) {}

    public static Entry get(String id) { return HUDS.getOrDefault(id, null); }
    // Entry 为 null 时，getX/getY/getScale/enabled 在 HudManager 里回退到元素默认值
    public static void put(String id, int x, int y, boolean enabled, float scale) { ... save(); }

    public static void load() { /* Files.readString + JsonParser，复用 AnimConfigData:95 模式，损坏则用空 */ }
    public static void save() { /* Files.writeString + GsonBuilder.setPrettyPrinting，复用 AnimConfigData:63 */ }
}
```

参考：`AnimConfigData.java:63-99`（路径/读写）、`TerraprismaRenderHandler.reloadConfig:364-414`（JSON 解析）。**不需要热重载**，启动加载 + 编辑器 `onClose` 保存即可。

### A4. `HudDragState`（拖拽/缩放状态机）

```java
final class HudDragState {
    enum Mode { NONE, DRAG, SCALE }
    HudElement selected = null;
    Mode mode = Mode.NONE;
    double dragOffX, dragOffY;        // 鼠标 - 元素左上角
    // 缩放手柄命中用四角，参考 PlayerTalentUI.cornerAt:278
    static final int HANDLE_HIT = 6;  // 像素命中半径（屏幕坐标，不随元素 scale）

    boolean hitDrag(HudElement e, double mx, double my) {
        int x = HudManager.getX(e), y = HudManager.getY(e);
        float s = HudManager.getScale(e);
        // 命中 = 缩放后的屏幕矩形
        return mx >= x && mx <= x + e.getLogicalWidth()*s
            && my >= y && my <= y + e.getLogicalHeight()*s;
    }
}
```

### A5. `HudEditorScreen extends Screen`（核心编辑器，DEL+ALT 打开）

参考 `AbolishPanelScreen`（不继承它）：
- `isPauseScreen()` → `false`（实时预览）
- `renderBackground` → 画半透明黑遮罩 `fill(0,0,width,height, 0x88000000)`，**禁用模糊**
- 打开时 `HudManager.setEditMode(true)`；`onClose()` → `HudManager.setEditMode(false)` + `HudPositionConfig.save()`

```java
public class HudEditorScreen extends Screen {
    private final HudDragState drag = new HudDragState();

    @Override protected void init() {
        // 顶部提示文字 + 一个「重置全部到默认」按钮（clearWidgets/addRenderableWidget）
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);          // 半透明遮罩
        for (HudElement e : HudManager.all()) {   // 全部显示，含 disabled
            HudManager.drawElement(g, e, true);   // editMode 强制画
            drawFrame(g, e, e == drag.selected || drag.hitDrag(e, mx, my), !HudManager.isEnabled(e));
        }
        // 画提示文字 + 重置按钮
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        // btn==2(右键)/中键 → 切换 hit 元素的 enabled
        // btn==0 → 命中四角手柄→SCALE；否则命中矩形→DRAG(记 dragOffX/Y)；从上层往下选第一个命中
        // 都没命中 → selected=null
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (drag.mode == DRAG) {
            int nx = (int)(mx - drag.dragOffX), ny = (int)(my - drag.dragOffY);
            clampToScreen(nx, ny, e);              // 参考 PlayerTalentUI.clampToScreen:325
            HudPositionConfig.put(e.id, nx, ny, enabled, scale);
        } else if (drag.mode == SCALE) {
            // 沿对角线距离换算 scale，clamp [0.5, 3.0]
        }
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) { drag.reset(); }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        // 选中元素 scale += sy*0.1，clamp [0.5,3.0]，写回 HudPositionConfig
    }

    @Override public boolean keyPressed(int k, int sc, int mods) {
        if (k == GLFW.GLFW_KEY_ESCAPE) { this.onClose(); return true; }
        return super.keyPressed(k, sc, mods);
    }
}
```

选中框/手柄：用 `PanelRenderHelper.drawBorder(g,x,y,w,h)`（`net.minecraft.client.yiz.ui.PanelRenderHelper:54`）或 `g.fill` 画四角手柄。`disabled` 元素用半透明/灰显（render 时元素自己降亮度，或编辑器在它上面盖一层半透明黑）。

---

## Part B — 通用突进系统（分离 boost 与心之翅）

### B1. `BoostProvider`（接口）

```java
public interface BoostProvider {
    /** 该玩家当前是否拥有此突进来源（如饰品槽装备了心之翅） */
    boolean isActive(Player player);
    /** 此来源提供的推进上限（覆盖默认 3） */
    default int getMaxBoosts(Player player) { return BoostData.DEFAULT_MAX; }
    /** 此来源的恢复间隔 tick（覆盖默认 80） */
    default int getRegenInterval(Player player) { return BoostData.DEFAULT_REGEN; }
}
```

### B2. `BoostRegistry`

```java
public final class BoostRegistry {
    private static final List<BoostProvider> PROVIDERS = new ArrayList<>();
    public static void register(BoostProvider p) { PROVIDERS.add(p); }
    /** 返回第一个 active 的 provider（多源时取优先级最高/最先注册的）；无则 null */
    public static BoostProvider getActive(Player player) {
        for (BoostProvider p : PROVIDERS) if (p.isActive(player)) return p;
        return null;
    }
}
```

### B3. `BoostData`（取代 `AccessoryBoostData` 的突进部分）

逻辑从 `AccessoryBoostData` 搬过来，去心之翅命名：

```java
public final class BoostData {
    public static final String KEY_BOOST = "yizxianmod:boost";
    public static final String KEY_REGEN = "yizxianmod:boost_regen";
    public static final int DEFAULT_MAX = 3;
    public static final int DEFAULT_REGEN = 80;

    public static void register() {
        PlayerDataAPI.register(KEY_BOOST, Codec.INT, 0);
        PlayerDataAPI.register(KEY_REGEN, Codec.INT, DEFAULT_REGEN);
    }
    public static int getBoosts(Player p) { ... }
    public static void setBoosts(Player p, int v) { /* clamp [0, currentMax] */ }
    public static int getRegenTicks(Player p) { ... }
    public static float getRegenProgress(Player p) { return 1f - (float)getRegenTicks(p)/currentInterval; }
    public static void tickRegen(Player p) { ... }      // 沿用原逻辑
    public static boolean tryConsume(Player p) { ... }  // 沿用
    public static void onTakeoff(Player p) { ... }
    public static void onLand(Player p) { ... }
}
```

`currentMax`/`currentInterval` 取自 `BoostRegistry.getActive(player)`（无 active 时用 DEFAULT）。

### B4. 心之翅 provider（新，可放 `api/` 或 `item/`）

```java
public final class HeartWingsBoostProvider implements BoostProvider {
    public static final HeartWingsBoostProvider INSTANCE = new HeartWingsBoostProvider();
    @Override public boolean isActive(Player player) {
        AccessoryContainer c = AccessoryContainer.get(player);
        for (int i = 0; i < c.getSlotCount(); i++)
            if (c.getItem(i).getItem() instanceof HeartWingsItem) return true;
        return false;
    }
    // max/regen 用默认
}
```
在 `YizxianModClient`（或公共初始化）里 `BoostRegistry.register(HeartWingsBoostProvider.INSTANCE);`

### B5. `BoostHandler`（由 `HeartWingsBoostHandler` 改造）

- 客户端 `onClientTick`：`hasHeartWings(player)` → 改为 `BoostRegistry.getActive(player) != null`。TAB 消耗、G 悬停逻辑不变（用 `BoostData.tryConsume`）。
- 服务端 `onPlayerTick`：`tickRegen`/`onLand` 改用 `BoostData`；入口判断同样改为查 `BoostRegistry.getActive`。
- 类名 `HeartWingsBoostHandler` → `BoostHandler`，更新 `YizxianMod.java` 的 `addListener(HeartWingsBoostHandler::onPlayerTick)` 引用。

### B6. 保留不动的部分
- `HeartWingsItem`：只管 `canElytraFly`/`elytraFlightTick`（飞行），不涉及突进。
- `MixinHeartWingsProtection`：衰落/动能伤害免疫是心之翅特性，**继续查心之翅**，不迁。

---

## Part C — 突进 HUD（首个 `HudElement`）

### C1. 纹理
- 拷贝 `D:\ZM\yizgzq\png\123.png` → `src/main/resources/assets/yizxianmod/textures/hud/boost_glyph.png`（56×8，7 帧 8×8）。

### C2. `BoostHud extends HudElement`

```java
public class BoostHud extends HudElement {
    private static final ResourceLocation TEX =
        ResourceLocation.fromNamespaceAndPath("yizxianmod", "textures/hud/boost_glyph.png");
    private static final int FRAMES = 7, FRAME = 8, ATLAS_W = 56, ATLAS_H = 8;
    private static final int ICON = 16;          // 屏幕上每帧显示尺寸（逻辑，scale=1）

    public BoostHud() { super("boost", 150, 200, 1.5f); }

    @Override public int getLogicalWidth()  { return ICON + 4 + 16; }  // 图标 + 间距 + 数字区
    @Override public int getLogicalHeight() { return ICON; }

    @Override public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        int boosts, interval; float regenProgress;
        if (editMode) { boosts = 2; interval = BoostData.DEFAULT_REGEN; regenProgress = 0.6f; }
        else {
            if (BoostRegistry.getActive(player) == null) return;   // 非编辑模式无来源不画
            boosts = BoostData.getBoosts(player);
            interval = BoostData.DEFAULT_REGEN;                    // 或取 active.getMaxBoosts 等
            regenProgress = BoostData.getRegenProgress(player);
        }
        int frame = Math.min(FRAMES - 1, (int)(regenProgress * FRAMES));   // 0..6
        // blit 取帧（放大 8×8 → 16×16）：九参重载
        g.blit(TEX, 0, 0, ICON, ICON,
               (float)(frame * FRAME), 0f, FRAME, FRAME, ATLAS_W, ATLAS_H);
        // 推进次数：数字（boosts + "/3"）或小圆点，画在图标右侧
        g.drawString(mc.font, boosts + "/" + BoostData.DEFAULT_MAX,
                     ICON + 4, (ICON - mc.font.lineHeight) / 2, 0xFFFFFFFF);
    }
}
```

blit 重载（NeoForge 1.21.1 `GuiGraphics`）：`blit(ResourceLocation atlas, int x, int y, int blitWidth, int blitHeight, float uOffset, float vOffset, int uWidth, int vHeight, int atlasWidth, int atlasHeight)`。**实现时核对签名是否一致**（见末尾"需验证点"）。

---

## Part D — 移除泰拉棱镜调试 HUD

`render/TerraprismaRenderHandler.java`：
- 删除 `onRenderGui(RenderGuiEvent.Post)` 方法（约 1132-1170 行）。
- 删除该方法用到的、仅服务 HUD 的局部 import；**保留** `BLADES`/`BladeState` 等核心字段（世界剑渲染 `RenderLevelStageEvent` 仍用）。
- 清理 `YizxianModClient` 里「HUD 已移除，待后续统一 HUD 管理器」相关注释与（若有）`RenderGuiEvent` 对它的订阅。

---

## Part E — 集成（`YizxianModClient.java`）

构造器内追加：
```java
// 启动加载
HudPositionConfig.load();
BoostData.register();
BoostRegistry.register(HeartWingsBoostProvider.INSTANCE);
HudManager.register(new BoostHud());

// 渲染分发
NeoForge.EVENT_BUS.addListener(HudManager::onRenderGui);
// DEL+ALT 边沿触发（见关键算法）
NeoForge.EVENT_BUS.addListener(YizxianModClient::onClientTickKey);
```
保留 `modEventBus.addListener(HeartWingsKeyMappings::register)`。`YizxianMod.java` 的 `addListener(HeartWingsBoostHandler::onPlayerTick)` 改为 `BoostHandler::onPlayerTick`。

---

## 关键算法骨架

### 1. 缩放下的命中测试（统一方案）
元素占地用 scale 后的屏幕矩形；render 用 PoseStack 统一缩放，所有内容（blit + drawString）在逻辑坐标 (0,0) 绘制：
```java
PoseStack pose = g.pose();
pose.pushPose();
pose.translate(x, y, 0);
pose.scale(scale, scale, 1);
e.render(g, editMode);      // 元素在 (0,0) 用逻辑尺寸画
pose.popPose();
// 命中（屏幕坐标）：mx>=x && mx<=x+logicalW*scale && my>=y && my<=y+logicalH*scale
```

### 2. DEL+ALT 边沿触发（`onClientTickKey`，ClientTickEvent.Post）
```java
private static boolean delAltWasDown = false;
static void onClientTickKey(ClientTickEvent.Post e) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;
    long win = mc.getWindow().getWindow();
    boolean alt  = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
    boolean del  = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_DELETE);
    boolean both = alt && del;
    if (both && !delAltWasDown && mc.screen == null) {
        mc.setScreen(new HudEditorScreen());
    }
    delAltWasDown = both;
}
```
编辑器内用 ESC 退出（标准 `Screen.onClose` → 保存）。`mc.screen==null` 判断防止编辑器内重复打开。

### 3. 编辑器接管、规避重影
`HudEditorScreen` 打开时 `HudManager.setEditMode(true)`，`HudManager.onRenderGui` 在 editMode 下直接 return（不在 vanilla HUD 层重画一遍）。编辑器自己在 `render()` 里遍历元素 + 选中框绘制。关闭时 `setEditMode(false)` 恢复正常分发。**需验证**：1.21.1 里 `Screen` 打开时 `RenderGuiEvent.Post` 是否确实仍触发（若是，本方案必要；若 vanilla 已跳过，则 editMode 开关冗余但无害）。

### 4. 拖拽状态机
`mouseClicked`：从元素列表**逆序**（上层优先）找首个 `hitDrag` 命中；命中四角手柄 → `SCALE`，命中矩形 → `DRAG`（记 `dragOffX=mx-x, dragOffY=my-y`）；右键/中键命中 → 切换 `enabled` 并 `HudPositionConfig.put`。`mouseDragged`：DRAG 改 x/y（clampToScreen），SCALE 改 scale。`mouseReleased`：reset。每次变更即 `HudPositionConfig.put`（实时持久化，`onClose` 再 save 一次确保）。

---

## 数据迁移与兼容

- PlayerDataAPI key：旧 `yizxianmod:boost_heart_wings` / `yizxianmod:boost_heart_wings_regen` → 新 `yizxianmod:boost` / `yizxianmod:boost_regen`。**开发期直接换**，老存档突进数据丢失（可接受；若需保留，可在 `BoostData.register` 后做一次性迁移读旧 key，本次不做）。
- `AccessoryBoostData.java`：内容迁入 `BoostData` 后**删除该文件**，全局 grep 替换调用方（`HeartWingsBoostHandler`→`BoostHandler`、HUD 等）。注意 `AccessoryBoostData` 当前可能还被别处引用——实现前先 `grep -r AccessoryBoostData` 全量确认调用点。

---

## 验证步骤（runClient 端到端）

前置库改动（若有，本计划均在主项目 yizxian1.21.1）→ 主项目 `./gradlew runClient`：

1. **编译**：`./gradlew compileJava` BUILD SUCCESSFUL（先过编译再跑）。
2. **HUD 正常显示**：进世界，装备心之翅起飞，确认突进 HUD 在默认位置出现（菱形图标 + `boosts/3`），TAB 推进时图标随恢复进度逐帧增长（1→7 档循环）。
3. **进入编辑器**：按 `DEL+ALT`，进入半透明全屏编辑器，突进 HUD 出现且可拖；游戏不暂停（背景怪能动）。
4. **拖动**：鼠标按住拖到任意位置，松手后位置保留；拖出屏幕被 clamp。
5. **缩放**：滚轮缩放选中 HUD（0.5~3.0），视觉与命中范围同步。
6. **显隐开关**：右键/中键切换，灰显态可拖但非编辑模式下不画。
7. **持久化**：ESC 退出 → `config/yizxianmod/hud_positions.json` 写入新位置/scale/enabled；重进游戏位置保留。
8. **重置按钮**：编辑器内点「重置全部到默认」，所有 HUD 回默认位。
9. **通用突进**：确认 TAB/G 仍工作（`BoostHandler` 改造后）；心之翅起飞免伤仍有效（`MixinHeartWingsProtection` 未动）。
10. **泰拉棱镜**：确认调试 HUD（左上角 A~G 统计）已消失；泰拉棱镜世界剑渲染正常（`RenderLevelStageEvent` 未受影响）。

---

## 实现时需验证的技术点（诚实标注）

1. **`GuiGraphics.blit` 九参重载签名**：1.21.1 是否就是 `blit(RL, int x, int y, int blitW, int blitH, float uOff, float vOff, int uW, int uH, int atlasW, int atlasH)`。实现时打开 `GuiGraphics` 源码核对，不一致则换等价重载。
2. **`RenderGuiEvent.Post` 在 Screen 打开时是否触发**：决定 editMode 开关是否冗余（不冗余则必加，冗余则无害保留）。
3. **`AccessoryBoostData` 全量调用点**：实现前 `grep -r AccessoryBoostData src/` 确认所有引用都已迁到 `BoostData`，避免编译断链。
4. **`HeartWingsBoostHandler` 改名 `BoostHandler`**：确认 `YizxianMod.java` 的 `addListener(HeartWingsBoostHandler::onPlayerTick)` 同步更新；grep 确认无其它引用。
5. **纹理透明度**：`123.png` 背景是否真透明（分析显示黑底）——若黑底不透明，HUD 会带黑框；实现时确认 PNG 的 alpha 通道，必要时在图片层面处理或 blit 时用 `RenderType` 支持透明。
6. **`InputConstants.isKeyDown` 与窗口失焦**：编辑器 Screen 打开后鼠标焦点归 Screen，DEL+ALT 重复触发的边界由 `mc.screen==null` 守护，已覆盖。

---

## 文件清单总览

| 动作 | 文件 |
|---|---|
| 新建 | `api/BoostProvider.java`、`api/BoostRegistry.java`、`api/BoostData.java`、`api/HeartWingsBoostProvider.java` |
| 新建 | `hud/HudElement.java`、`hud/HudManager.java`、`hud/HudPositionConfig.java`、`hud/HudDragState.java`、`hud/HudEditorScreen.java`、`hud/BoostHud.java` |
| 新建资源 | `assets/yizxianmod/textures/hud/boost_glyph.png`（拷自 `png/123.png`） |
| 删除 | `api/AccessoryBoostData.java`（逻辑迁入 `BoostData`） |
| 改名+改造 | `handler/HeartWingsBoostHandler.java` → `handler/BoostHandler.java` |
| 改造 | `render/TerraprismaRenderHandler.java`（删 `onRenderGui`）、`YizxianModClient.java`（注册+DEL+ALT）、`YizxianMod.java`（`BoostHandler` 引用） |
| 不动 | `item/HeartWingsItem.java`、`mixin/MixinHeartWingsProtection.java`、其它 Mixin |
