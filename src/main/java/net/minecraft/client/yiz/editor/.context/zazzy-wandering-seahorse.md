# 属性图标 PNG UI — 实现计划

## Context（为什么做）

`tool` 包目前**没有任何 PNG 贴图能力**——唯一的视觉辅助 `tool/helper/ParticleEffectHelper` 全用原版粒子，属性显示（编辑台、HUD、物品 tooltip）只有纯文字，视觉细节粗糙。用户已手绘 21 张属性象征图标（`D:\ZM\yizgzq\方块\强化\属性\*.png`，32×32），要求在**所有"属性名+数值"显示行**的属性名前方渲染对应图标，覆盖：属性编辑台列表、编辑台 HUD 预览、SkillInfoHud（蓝耗/冷却/伤害）、物品 tooltip。

核心挑战：tooltip 是 Component 纯文本流（引擎渲染，无法 `blit`），必须用**字体图集方案**把图标注册成私用区码点字符；Screen/HUD 则直接 `blit`。两条渲染路径**共用同一份「属性→图标」映射源**（放 `tool` 包，符合用户"在 tool 中添加"的原意）。

## 已确认的决策（用户拍板）

- **范围**：全场景含 tooltip（编辑台 + HUD预览 + SkillInfoHud + 物品 tooltip）
- **图标中心**：放 `tool/icon/` 包，独立注册中心
- **恢复溢增、饥饿恢复**：先闲置，不绑定（项目无对应属性）
- **SkillInfoHud 规则**：蓝耗行→**法力消耗**图标；冷却行→**冷却值**图标；伤害行→`damage_type==0`(物理/通用)用**攻击强度**图标，`damage_type≥1`(元素/法术)用**法强**图标
- **sheet 拼图**：由我（Agent）用 Python+PIL 自动拼成 160×128 单 sheet
- 我代为拍板：延距→`attack_range`；tooltip 数值保留现有 BLUE 着色；属性行统一用"满"图标（护盾满/格挡满），"空"图标暂不进 sheet（源文件保留备用）

## 渲染路径与统一映射源

两条路径共用 `AttributeIconRegistry`：
- **blit 路径**（编辑台/SkillInfoHud）：`Icon.texture` + sheet UV → `IconBlitHelper` 用 9-参 `GuiGraphics.blit` 切片
- **字体路径**（tooltip）：`Icon.charStr`（私用区码点）→ `Style.withFont(yizmodqzk:attributes)` 独立 Component 段

## 新建文件（4 个）

### 1. `tool/icon/AttributeIconRegistry.java`
静态注册中心（仿 `tool/SimpleCommandRegistry` 风格）。核心：
```java
public record Icon(ResourceLocation texture, String charStr,
                   int u, int v, int regionW, int regionH, int sheetW, int sheetH) {}

private static final Map<String, Icon> MAP = new HashMap<>();
private static final ResourceLocation SHEET =
    ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/font/attribute_icons.png");
private static final ResourceLocation FONT =
    ResourceLocation.fromNamespaceAndPath("yizmodqzk", "attributes");
private static final int GRID = 32, COLS = 5;  // 每格32px，5列

public static Icon get(String attrId);     // null = 无图标
public static boolean has(String attrId);
public static String charOf(String attrId); // "" = 无图标（字体路径用）
public static ResourceLocation font();      // FONT，给 withFont 用

// register(attrId, sheetIndex)：sheetIndex → u=col*32, v=row*32
```
- key 兼容两种 id 形式：`generic.max_health`（vanilla，带点）和 `spell_power`（yiz，无点），与 `EditableAttribute.id()` 及 holder 反查结果天然统一。
- **同一码点可服务多 attrId**：`life_regen_rate`+`life_regen_pct` 都绑"自然恢复"格；`mana_cost`+`mana_cost_per_sec` 都绑"法力消耗"格。

### 2. `tool/icon/IconBlitHelper.java`
封装 9-参 blit，统一所有 blit 点：
```java
public static void blit(GuiGraphics g, Icon icon, int x, int y, int size) {
    g.blit(icon.texture(), x, y, size, size,
           icon.u(), icon.v(), icon.regionW(), icon.regionH(), icon.sheetW(), icon.sheetH());
}
```

### 3. `assets/yizmodqzk/font/attributes.json`（字体定义）
```json
{
  "providers": [{
    "type": "bitmap",
    "file": "yizmodqzk:font/attribute_icons.png",
    "height": 16, "ascent": 14,
    "chars": [
      "",
      "",
      "D".replace(...),  // 实际写 
      ""
    ]
  }]
}
```
（`height=16, ascent=14` 为起点，需 in-game 微调基线对齐。chars 数组顺序必须与 registry register 顺序、sheet 格序三方一致。）

### 4. `assets/yizmodqzk/textures/font/attribute_icons.png`（160×128 sprite sheet）
由 Python+PIL 把 17 个源 PNG 按 sheet 布局拼成。**无需 mods.toml/neoforge.client.toml 注册**——`assets/<ns>/font/*.json` 由 vanilla FontManager 自动扫描。

## 图标→属性映射表（17 格进 sheet，5列×4行）

| 码点 | sheet格(col,row) | 源文件 | attrId |
|------|------|--------|--------|
| E000 | (0,0) | 法强.png | `spell_power` |
| E001 | (1,0) | 法术防御.png | `spell_defense` |
| E002 | (2,0) | 攻击强度.png | `attack_strength` |
| E003 | (3,0) | 攻击强度防御.png | `armor` |
| E004 | (4,0) | 护盾满.png | `shield_value` |
| E005 | (0,1) | 格挡满.png | `damage_block` |
| E006 | (1,1) | 冷却值.png | `cooldown_value` |
| E007 | (2,1) | 攻击冷却缩减.png | `cooldown_reduction` |
| E008 | (3,1) | 延距.png | `attack_range` |
| E009 | (4,1) | 移动速度.png | `move_speed` |
| E00A | (0,2) | 最大移动速度.png | `max_run_speed` |
| E00B | (1,2) | 最大法力值.png | `max_mana` |
| E00C | (2,2) | 最大生命.png | `generic.max_health` |
| E00D | (3,2) | 自然恢复.png | `life_regen_rate` + `life_regen_pct` |
| E00E | (4,2) | 暴击概率.png | `crit_rate` |
| E00F | (0,3) | 暴击效果.png | `crit_damage` |
| E010 | (1,3) | 法力消耗.png | `mana_cost` + `mana_cost_per_sec` |

**未进 sheet**：护盾空、格挡空（满态已用，空态源文件备用）、恢复溢增、饥饿恢复（闲置）。

## 改造文件（4 个）

### A. `ui/ItemAttributeDisplay.java`（驱动 tooltip）
1. `AttributeInfo` record 加 `attrId` 字段：`record AttributeInfo(String attrId, String name, String value, int color) {}`
2. `getAvailableAttributes`（行 195-222）：从 holder 反查 attrId：
   ```java
   ResourceLocation loc = mod.attribute().unwrapKey().map(k -> k.location()).orElse(null);
   String attrId = loc == null ? null :
       loc.getNamespace().equals("minecraft") ? loc.toString() : loc.getPath();
   // minecraft:generic.max_health → "generic.max_health"；yizmodqzk:spell_power → "spell_power"
   ```
   耐久值行 attrId 传 null（无图标）。
3. tooltip 着色保留 BLUE，字体图标插入在 `tizModClient` 内联完成（见 D）。`createAttributeComponent` 同步加 attrId 重载（供其他潜在调用点），逻辑同 D。

### B. `editor/AttributeEditorScreen.java`
1. `renderAttributeList`（行 109-141）：drawString 前查 `AttributeIconRegistry.get(attr.id())`，有图标则 `IconBlitHelper.blit(g, icon, textX, rowY+(ROW_H-16)/2, 16)`，`textX += 18` 让位；无图标不位移。
2. `renderHud`（行 186-219）：把 `hudLines: List<String>` 升级为 `List<HudRow(String label, String attrId)>`，渲染时同上 blit + 位移。

### C. `hud/SkillInfoHud.java`（行 57-117 `renderSlotInfo`）
按用户规则给三行加图标（drawString 前 blit，文字 x 右移）：
- **蓝耗行**（行 72-82）：`manaPerSec>0 ? "mana_cost_per_sec" : "mana_cost"` → 法力消耗图标
- **冷却/状态行**（行 85-104）：绑 `"cooldown_value"`（冷却值图标）。注意此行充能技能显示"冷却/可用"，持续技能显示"状态"——图标统一用冷却值即可
- **伤害行**（行 107-113）：
  ```java
  int dmgType = (int) readAttr(item, YizAttributes.DAMAGE_TYPE);  // 0=物理,1-5=元素
  String dmgIconId = (dmgType == 0) ? "attack_strength" : "spell_power";
  var icon = AttributeIconRegistry.get(dmgIconId);  // blit 在 "§7伤害:" 前
  ```
  伤害行仅 `dmg>0` 才绘制（现有逻辑），图标随之。

### D. `tizModClient.java`（行 256-277 `onItemTooltip`）
属性行构建改为内联字体图标（保留 BLUE）：
```java
for (var attr : attrs) {
    MutableComponent line = Component.literal("  ");
    String ic = AttributeIconRegistry.charOf(attr.attrId());
    if (!ic.isEmpty()) {
        line.append(Component.literal(ic).withStyle(Style.EMPTY.withFont(AttributeIconRegistry.font())));
        line.append(Component.literal(" "));  // 图标后留 1 空格
    }
    line.append(Component.literal(attr.name() + "："));
    line.append(Component.literal(attr.value()).withStyle(ChatFormatting.BLUE));  // 保留 BLUE
    lines.add(insertAt++, line);
}
```
**关键**：图标字符必须**独立成一个 `Component.literal().withStyle(withFont)`**，不能与中文混在一个 literal——否则整段中文都走 attributes 字体显示豆腐块。`cleanTooltip`（行 280-302）的属性行匹配逻辑要确认仍能识别带图标前缀的行（按 `name+"："` startsWith，图标段在最前不影响）。

## 客户端隔离
所有改动类都在 `net.minecraft.client.*`（client 包），天然客户端加载，无需 `@OnlyIn`。registry 只持 `ResourceLocation`/`String`，不调渲染 API，被 client 类引用安全。`EditableAttribute`/`AttributeInfo` record 签名最小改动（attrId 是 String，服务端安全）——渲染层查 registry，record 不直接调 registry。

## 分步实施（按依赖顺序）
1. 用 Python+PIL 把 17 张源 PNG 按映射表顺序拼成 `assets/yizmodqzk/textures/font/attribute_icons.png`（160×128，每格 32×32，按 col,row 布局）。
2. 新建 `tool/icon/AttributeIconRegistry.java`：17 条 `register(attrId, sheetIndex)` + 多 attrId 共享码点（自然恢复、法力消耗）。
3. 新建 `tool/icon/IconBlitHelper.java`。
4. 新建 `assets/yizmodqzk/font/attributes.json`（chars 顺序与 registry 一致）。
5. 改 `ItemAttributeDisplay`：AttributeInfo 加 attrId + getAvailableAttributes 反查。
6. 改 `tizModClient.onItemTooltip`：内联字体图标（验证 cleanTooltip 兼容）。
7. 改 `AttributeEditorScreen`：renderAttributeList + renderHud blit。
8. 改 `SkillInfoHud.renderSlotInfo`：三行按规则 blit。
9. `cd D:\ZM\yizgzq\yiz1.21.1 && ./gradlew build` 编译。
10. 启动下游 `D:\ZM\yizgzq\yizxian1.21.1 && ./gradlew.bat runClient`（后台），等 90s 看日志进主菜单。

## 验证（in-game）
- **编译**：前置库 `gradlew build` SUCCESS；下游 compileJava SUCCESS（自动检测前置 jar 变化重编）。
- **tooltip**：手持带属性物品悬停，每条属性行名前应显示对应图标（法强/攻击/暴击…），数值保持蓝色。无图标属性（如"吸血"）不显示图标、文字不前移。
- **编辑台**：打开属性编辑台，列表每行名前有图标；HUD 预览区同。
- **SkillInfoHud**：技能槽详情——蓝耗行前法力消耗图标，冷却行前冷却值图标，伤害行前攻击强度(物理技能)或法强(法术技能)图标。
- **字体基线**：若图标偏高/偏低/被裁，调 `font/attributes.json` 的 `height`/`ascent`（F3+T 热重载资源即时看效果，无需重启）。
- **豆腐块排查**：若图标位显示空白方块，检查 `withFont` 是否独立成段、chars 顺序是否与 registry/sheet 三方一致。

## 风险点
1. **字体三方对齐**：sheet 格序、font.json chars 顺序、registry register 顺序必须一致，任一错位则图标张冠李戴——这是最易错点，实施时逐格核对。
2. **withFont 独立段**：图标字符混入中文 literal 会导致整段中文变豆腐块。
3. **基线/advance**：`height/ascent` 需 in-game 微调；图标后手动加空格绕过 advance 精调。
4. **cleanTooltip 兼容**：tooltip 清理逻辑要能识别带图标前缀的新行（按 `name+"："` 匹配应仍成立，需验证）。
5. **9-参 blit UV 是像素**：sheet 160×128，每格 32×32，`u=col*32, v=row*32, regionW=regionH=32, sheetW=160, sheetH=128`。
