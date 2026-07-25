---
name: gui-bg-offset
description: "AbstractContainerScreen 的背景面板贴图必须用 leftPos-1/topPos-1 偏移，否则 slot 点击位置差 1px"
metadata:
  type: feedback
---

**项目惯例**：`AbstractContainerScreen` 的背景整图渲染时统一用 `leftPos - 1, topPos - 1`（如 `GuiGraphics.blit(GUI_TEXTURE, leftPos - 1, topPos - 1, ...)`）。这是项目既有风格，`SkillConfigScreen`、`AttributeEditorScreen` 均如此。

**Why:** 槽位 slot 坐标以 `leftPos + slot.x` 为基准，背景图偏移 -1 后视觉上更加紧凑。

**How to apply（拆整图为拼装面板时）**：
- 背景面板 blit 原点用 `lx = leftPos - 1, ty = topPos - 1`（保持惯例）。
- 从整图拆出的子面板坐标按纹理偏移反推：`panelX = slotGUI_X - 纹理偏移X`。**注意**：推出来的 X/Y 是基于整图（`leftPos` 基准），实际用 `lx=leftPos-1` 时面板整体偏左上 1px。如果面板上的 slot 对齐要求严格（如强化槽 ENHANCE_SLOTS），可能需要给面板坐标 +1 补偿（`panelX+1, panelY+1`）来抵消 -1 的影响。**不能直接改 slot 坐标**（那是交互基准）。
- **不同面板对 -1 的敏感度不同**（纹理偏移量不同），需要逐面板验证对齐，不能一刀切全局加或去掉 -1。

**本次教训**：SkillConfigScreen 6 面板拼装化时，C 面板（升级区）不补偿则强化槽偏 1px；我在 ENHANCE_SLOTS 上 +1 又偏 1px → 累计偏 2px，且其他面板不受影响。最终 C_X+1, C_Y+1 补偿 -1，ENHANCE_SLOTS 保持原坐标解决。
