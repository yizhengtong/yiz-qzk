---
name: hud-position-config
description: "HUD 位置持久化文件 hud_positions.json 的位置/格式/换算公式，以及被清空导致 HUD 挤左上角的排查方法"
metadata:
  type: feedback
---

HUD 位置持久化：
- 文件：`run/config/yizmodqzk/hud_positions.json`（下游 yizxian 的 run 目录）
- 格式：`{_guiW, _guiH, huds: {"id": {x, y, enabled, scale}}}`。`_guiW/_guiH` 记录保存时的 GUI 分辨率。
- **拖拽实时写入**：HudEditorScreen 每次拖拽调 `HudManager.setPosition` → `HudPositionConfig.put` → `save()` 写文件（"实时持久化"）。不需要单独点保存按钮。
- 代码默认 960×540 参考：`HudManager.getX` 用 `defaultX × curW/960` 计算实际位置。要将 json 保存值设为代码默认：`defaultX = savedX × 960/_guiW`, `defaultY = savedY × 540/_guiH`，scale 直接用。

**排障**：若所有 HUD 挤在左上角 → 读 `hud_positions.json` 看 `huds` 是否为 `{}`（空对象）。这种情况是游戏启动时某次 save() 覆盖了空数据。**删掉该文件**让 HUD 走代码默认即可恢复。

**Why:** 本次遇到过 json 莫名变空对象，全 HUD 挤左上（debug 耗时）。未来类似问题直接读 json 秒查。

**How to apply:** 给用户设 HUD 默认位置时，用换算公式。HUD 位置异常时先读 hud_positions.json 看 huds 是否被清空。代码默认 960×540 基准。
