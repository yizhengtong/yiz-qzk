package net.minecraft.client.yiz.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkillConfigScreen extends AbstractContainerScreen<SkillConfigMenu> {

    public static final int GUI_WIDTH = 630, GUI_HEIGHT = 300;
    private static final ResourceLocation TEX_A = ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/skill/panel_a.png");
    private static final ResourceLocation TEX_B = ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/skill/panel_b.png");
    private static final ResourceLocation TEX_C = ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/skill/panel_c.png");
    private static final ResourceLocation TEX_D = ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/skill/panel_d.png");
    private static final ResourceLocation TEX_E = ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/skill/panel_e.png");
    private static final ResourceLocation TEX_F = ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/skill/panel_f.png");

    private final Map<String, Panel> panels = new LinkedHashMap<>();
    {
        panels.put("A", new Panel(26, 17, 168, 228));
        panels.put("B", new Panel(233, 160, 168, 82));
        panels.put("C", new Panel(235, 58, 166, 73));
        panels.put("D", new Panel(443, 80, 96, 165));
        panels.put("E", new Panel(540, 80, 24, 114));
        panels.put("F", new Panel(576, 48, 14, 14));
    }
    private static class Panel {
        int x, y, w, h;
        final int dx, dy;
        Panel(int x, int y, int w, int h) { this.x=x;this.y=y;this.w=w;this.h=h;this.dx=x;this.dy=y; }
        int offX() { return x - dx; }
        int offY() { return y - dy; }
        void reset() { x = dx; y = dy; }
    }

    // 反射打破 Slot.x/y final — 每帧覆写坐标
    private static final Field SLOT_X, SLOT_Y;
    static {
        Field fx = null, fy = null;
        try { fx = Slot.class.getDeclaredField("x"); fx.setAccessible(true); } catch (Exception ignored) {}
        try { fy = Slot.class.getDeclaredField("y"); fy.setAccessible(true); } catch (Exception ignored) {}
        SLOT_X = fx; SLOT_Y = fy;
    }

    private static final int XP_COST_PER_LEVEL = 100;
    private static final int UPGRADE_SLOT_X = 262, UPGRADE_SLOT_Y = 84;
    private static final int[][] ENHANCE_SLOTS = {{302,67},{338,67},{378,67},{302,105},{338,105},{378,105}};
    private static final int ENHANCE_SIZE = 14;
    private static final int PREVIEW_X=51,PREVIEW_Y=45,PREVIEW_W=124,PREVIEW_H=178;
    private static final int INFO_X=450,INFO_Y=85;

    private boolean editMode;
    private boolean delHeld;
    private String draggingPanel;
    private int btnResX, btnResY, btnSaveX, btnSaveY;

    private int selectedEnhance = -1;
    private int previewScroll = 0;
    private boolean upgradeSlotSelected;
    private int[] enhanceLevels = new int[6];
    private List<EnhanceEntry> enhanceEntries = List.of();
    private String skillRegName = "";

    public SkillConfigScreen(SkillConfigMenu m, Inventory inv, Component t) {
        super(m, inv, t); this.imageWidth = GUI_WIDTH; this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = this.width / 2 - 316;
        loadPanels();
    }

    // ═══ 反射覆写所有 Container slot 坐标 = 基准值 + 面板偏移 ═══

    private void syncSlotPositions() {
        if (SLOT_X == null) return;
        try {
            int cox=panels.get("C").offX(), coy=panels.get("C").offY(),
                box=panels.get("B").offX(), boy=panels.get("B").offY(),
                dox=panels.get("D").offX(), doy=panels.get("D").offY(),
                eox=panels.get("E").offX(), eoy=panels.get("E").offY();
            List<Slot> sl = this.menu.slots;
            // slot 0: 升级槽 → C
            setSlot(sl.get(0), 262+cox, 84+coy);
            // slot 1-27: 背包9×3 → B
            for (int i=0;i<27;i++) setSlot(sl.get(1+i), 236+(i%9)*18+box, 163+(i/9)*18+boy);
            // slot 28-36: 快捷栏9×1 → B
            for (int i=0;i<9;i++) setSlot(sl.get(28+i), 236+i*18+box, 221+boy);
            // slot 37: 大槽 → D
            setSlot(sl.get(37), 455+dox, 138+doy);
            // slot 38-40: 技能×3 → D
            for (int i=0;i<3;i++) setSlot(sl.get(38+i), 482+i*18+dox, 129+doy);
            // slot 41-43: 被动×3 → D
            for (int i=0;i<3;i++) setSlot(sl.get(41+i), 482+i*18+dox, 147+doy);
            // slot 44-63: 技能库5×4 → D
            for (int i=0;i<20;i++) setSlot(sl.get(44+i), 446+(i%5)*18+dox, 170+(i/5)*18+doy);
            // slot 64-69: 装备×6 → E
            for (int i=0;i<6;i++) setSlot(sl.get(64+i), 543+eox, 83+i*18+eoy);
        } catch (Exception ignored) {}
    }
    private static void setSlot(Slot s, int x, int y) { try { SLOT_X.setInt(s, x); SLOT_Y.setInt(s, y); } catch (Exception ignored) {} }

    private String getSkillRegName() {
        ItemStack item = getUpgradeItem();
        if (item.isEmpty()) return "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    }
    private void loadEnhanceLevels() {
        ItemStack item = getUpgradeItem();
        enhanceLevels = SkillConfigStorage.getEnhanceLevels(item);
        skillRegName = getSkillRegName();
    }
    private void refreshEnhanceEntries() {
        ItemStack item = getUpgradeItem();
        var mc = Minecraft.getInstance();
        if (item.isEmpty() || !(item.getItem() instanceof net.minecraft.client.yiz.api.IEnhanceable e))
            enhanceEntries = List.of();
        else enhanceEntries = e.getEnhanceEntries(item, mc.player);
    }

    // ═══ DEL+ALT 编辑 + 按钮 + JSON ═══

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // DEL(261) + ALT 双向：先按DEL再按ALT或反过来都支持
        if (keyCode == 261) { delHeld = true; if (hasAltDown()) { toggleEdit(); return true; } }
        else if ((keyCode == 342 || keyCode == 344) && delHeld) { toggleEdit(); return true; }
        else if (keyCode == 256 && editMode) { toggleEdit(); return true; } // ESC 关闭编辑模式
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 261) delHeld = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    private void toggleEdit() {
        editMode = !editMode;
        if (!editMode) { syncSlotPositions(); savePanels(); }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (editMode) { if (btn != 0) return true; // 编辑模式吃所有按键,禁止 GUI 操作
            if (mx >= btnResX && mx < btnResX + 40 && my >= btnResY && my < btnResY + 10) {
                for (Panel p : panels.values()) p.reset();
                syncSlotPositions(); savePanels(); return true;
            }
            if (mx >= btnSaveX && mx < btnSaveX + 40 && my >= btnSaveY && my < btnSaveY + 10) {
                syncSlotPositions(); savePanels(); return true;
            }
            for (var e : panels.entrySet()) {
                Panel p = e.getValue();
                int px = leftPos - 1 + p.x, py = topPos - 1 + p.y;
                if (mx >= px && mx < px + p.w && my >= py && my < py + p.h) {
                    draggingPanel = e.getKey(); return true;
                }
            }
            return true;
        }
        if (isClose(mx, my)) { this.onClose(); return true; }
        for (int i = 0; i < ENHANCE_SLOTS.length; i++) {
            if (isEnh(mx, my, i)) {
                if (i >= enhanceEntries.size()) return true;
                var entry = enhanceEntries.get(i);
                if (btn == 0) {
                    if (hasShiftDown()) {
                        if (entry instanceof EnhanceEntry.Tag) deactivateTag(i); else resetEnhance(i);
                    } else {
                        if (entry instanceof EnhanceEntry.Tag) activateTag(i); else onPlus(i);
                    }
                } else if (btn == 1) { selectedEnhance = i; upgradeSlotSelected = false; }
                return true;
            }
        }
        if (btn == 0 && isUpgrade(mx, my)) { upgradeSlotSelected = true; selectedEnhance = -1; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dragX, double dragY) {
        if (draggingPanel != null) {
            Panel p = panels.get(draggingPanel);
            p.x += Math.round(dragX); p.y += Math.round(dragY);
            if (p.x < -p.w + 10) p.x = -p.w + 10;
            if (p.y < -p.h + 10) p.y = -p.h + 10;
            syncSlotPositions();
            return true;
        }
        return super.mouseDragged(mx, my, btn, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (draggingPanel != null) { draggingPanel = null; savePanels(); return true; }
        return super.mouseReleased(mx, my, btn);
    }

    private void loadPanels() {
        try {
            Path p = Path.of("").toAbsolutePath().resolve("config/yizmodqzk/skill_panels.json");
            if (!Files.exists(p)) return;
            JsonObject r = new Gson().fromJson(Files.readString(p), JsonObject.class);
            JsonObject objs = r.getAsJsonObject("panels");
            for (String k : objs.keySet()) {
                Panel pn = panels.get(k); if (pn == null) continue;
                JsonObject o = objs.getAsJsonObject(k);
                pn.x = o.get("x").getAsInt(); pn.y = o.get("y").getAsInt();
            }
        } catch (Exception ignored) {}
    }
    private void savePanels() {
        try {
            Path p = Path.of("").toAbsolutePath().resolve("config/yizmodqzk/skill_panels.json");
            Files.createDirectories(p.getParent());
            JsonObject root = new JsonObject(), objs = new JsonObject();
            for (var e : panels.entrySet()) { JsonObject o = new JsonObject(); o.addProperty("x", e.getValue().x); o.addProperty("y", e.getValue().y); objs.add(e.getKey(), o); }
            root.add("panels", objs);
            Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ignored) {}
    }

    // ═══ 渲染 ═══

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        syncSlotPositions();
        int lx = leftPos - 1, ty = topPos - 1;
        blit(g, TEX_A, lx + panels.get("A").x, ty + panels.get("A").y, panels.get("A"));
        blit(g, TEX_B, lx + panels.get("B").x, ty + panels.get("B").y, panels.get("B"));
        blit(g, TEX_C, lx + panels.get("C").x, ty + panels.get("C").y, panels.get("C"));
        blit(g, TEX_D, lx + panels.get("D").x, ty + panels.get("D").y, panels.get("D"));
        blit(g, TEX_E, lx + panels.get("E").x, ty + panels.get("E").y, panels.get("E"));
        blit(g, TEX_F, lx + panels.get("F").x, ty + panels.get("F").y, panels.get("F"));

        loadEnhanceLevels(); refreshEnhanceEntries();
        renderEnhanceSlots(g, mx, my);
        renderPreview(g); renderInfoPanel(g);

        if (editMode) {
            for (Panel p : panels.values()) {
                int px = lx + p.x, py = ty + p.y;
                g.fill(px, py, px + p.w, py + p.h, 0x2200FF00);
                g.fill(px, py, px + p.w, py + 1, 0x8800FF00);
                g.fill(px, py + p.h - 1, px + p.w, py + p.h, 0x8800FF00);
                g.fill(px, py, px + 1, py + p.h, 0x8800FF00);
                g.fill(px + p.w - 1, py, px + p.w, py + p.h, 0x8800FF00);
            }
            int bX = leftPos + GUI_WIDTH - 110, bY = topPos + GUI_HEIGHT - 22;
            btnResX = bX; btnResY = bY;
            g.drawString(font, "§a[ §f重置 §a]", btnResX, btnResY, 0xFFFFFF);
            btnSaveX = bX + 50; btnSaveY = bY;
            g.drawString(font, "§a[ §f保存 §a]", btnSaveX, btnSaveY, 0xFFFFFF);
        }
    }

    private int cOffX() { return panels.get("C").offX(); }
    private int cOffY() { return panels.get("C").offY(); }
    private int aOffX() { return panels.get("A").offX(); }
    private int aOffY() { return panels.get("A").offY(); }

    private static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, Panel p) {
        g.blit(tex, x, y, 0, 0, p.w, p.h, p.w, p.h);
    }

    private void renderEnhanceSlots(GuiGraphics g, int mx, int my) {
        int ox = cOffX(), oy = cOffY();
        for (int i = 0; i < ENHANCE_SLOTS.length; i++) {
            int x = leftPos + ENHANCE_SLOTS[i][0] + ox, y = topPos + ENHANCE_SLOTS[i][1] + oy;
            boolean h = mx >= x && mx < x + ENHANCE_SIZE && my >= y && my < y + ENHANCE_SIZE;
            boolean sel = selectedEnhance == i;
            boolean isTag = i < enhanceEntries.size() && enhanceEntries.get(i) instanceof EnhanceEntry.Tag;
            int bg = sel ? 0x44FFFFFF : (h ? 0x33FFFFFF : 0x18FFFFFF);
            if (isTag && enhanceLevels[i] > 0) bg = sel ? 0x66FFAA00 : 0x44FFAA00;
            g.fill(x, y, x + ENHANCE_SIZE, y + ENHANCE_SIZE, bg);
            if (i < enhanceEntries.size()) {
                var e = enhanceEntries.get(i);
                if (e instanceof EnhanceEntry.Tag tg) {
                    ItemStack ic = SkillEnhanceConfig.getItemFor(tg.key());
                    if (!ic.isEmpty()) g.renderItem(ic, x - 1, y - 1);
                    if (enhanceLevels[i] > 0) { g.fill(x, y, x + ENHANCE_SIZE, y + ENHANCE_SIZE, 0x44FFAA00); g.drawString(font, "✓", x + ENHANCE_SIZE - 6, y + 1, 0xFF55FF55); }
                } else {
                    String n = e.displayName(); if (n.length() > 2) n = n.substring(0, 2);
                    g.drawString(font, n, x + 1, y + 1, 0xCCFFFFFF);
                    if (enhanceLevels[i] > 0) { String lv = String.valueOf(enhanceLevels[i]); g.drawString(font, lv, x + ENHANCE_SIZE - font.width(lv) - 1, y + ENHANCE_SIZE - 10, 0xFF55FF55); }
                }
            }
        }
    }

    private static final int COLOR_WHITE=0xFFFFFFFF, COLOR_BLUE=0xFF5555FF, COLOR_GRAY=0xFF888888;

    private void renderPreview(GuiGraphics g) {
        int ox = aOffX(), oy = aOffY();
        int x = leftPos + PREVIEW_X + ox, y = topPos + PREVIEW_Y + oy;
        g.fill(x, y, x + PREVIEW_W, y + PREVIEW_H, 0x33000000);
        ItemStack up = getUpgradeItem();
        if (up.isEmpty()) { g.drawString(font, "放入技能物品", x + 4, y + 4, COLOR_GRAY); return; }
        g.enableScissor(x, y, x + PREVIEW_W, y + PREVIEW_H);
        int cy = y+4-previewScroll, lh=11, mw=PREVIEW_W-4;
        g.drawString(font, font.plainSubstrByWidth(up.getHoverName().getString(), mw), x+2, cy, COLOR_WHITE); cy+=lh+2;
        var mc = Minecraft.getInstance();
        List<Component> tt = up.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        for (int i=1;i<tt.size();i++) { String t=tt.get(i).getString(); if(t.isBlank()||t.startsWith("在装备时")||t.startsWith("已觉醒")||t.startsWith(" ")) continue;
            for (var ln:font.split(Component.literal(t),mw)) { if(cy>y+PREVIEW_H-lh) break; g.drawString(font,ln,x+2,cy,COLOR_WHITE); cy+=lh; } }
        cy+=2; boolean has=false; for(int i=0;i<enhanceEntries.size()&&i<enhanceLevels.length;i++){if(enhanceLevels[i]>0){has=true;break;}}
        if(!enhanceEntries.isEmpty()&&has){ g.drawString(font,"已装载:",x+2,cy,COLOR_GRAY); cy+=lh;
            for(int i=0;i<enhanceEntries.size()&&i<enhanceLevels.length;i++) if(enhanceLevels[i]>0){if(cy>y+PREVIEW_H-lh)break; g.drawString(font,font.plainSubstrByWidth(enhanceEntries.get(i).displayName(),mw-4),x+4,cy,COLOR_BLUE); cy+=lh; } cy+=1; }
        cy+=2; g.fill(x+2,cy,x+PREVIEW_W-2,cy+1,0x44FFFFFF); cy+=4;
        g.drawString(font,"预览",x+2,cy,COLOR_GRAY); cy+=lh+1;
        if(selectedEnhance>=0&&selectedEnhance<enhanceEntries.size()){ var e=enhanceEntries.get(selectedEnhance);
            if(e instanceof EnhanceEntry.Tag tg){ g.drawString(font,font.plainSubstrByWidth(tg.displayName(),mw),x+2,cy,COLOR_BLUE); cy+=lh;
                for(var ln:font.split(Component.literal(tg.description()),mw)){if(cy>y+PREVIEW_H-lh)break; g.drawString(font,ln,x+2,cy,COLOR_WHITE); cy+=lh;} }
            else { int lv=selectedEnhance<enhanceLevels.length?enhanceLevels[selectedEnhance]:0; g.drawString(font,font.plainSubstrByWidth(e.displayName()+" Lv."+lv,mw),x+2,cy,COLOR_BLUE); } }
        g.disableScissor();
    }

    private void renderInfoPanel(GuiGraphics g) {
        var m = Minecraft.getInstance(); if(m.player==null) return;
        int t = C2SSkillEnhancePayload.getTotalXp(m.player);
        g.drawString(font, "经验: "+t+" ("+(t/XP_COST_PER_LEVEL)+"次)", leftPos+INFO_X+4, topPos+INFO_Y+8, 0xFF55FF55);
    }

    private void activateTag(int i){if(enhanceLevels[i]>0)return;var m=Minecraft.getInstance();if(m.player==null)return;if(C2SSkillEnhancePayload.getTotalXp(m.player)<XP_COST_PER_LEVEL)return;enhanceLevels[i]=1;C2SSkillEnhancePayload.send(skillRegName,i,1);}
    private void deactivateTag(int i){if(enhanceLevels[i]<=0)return;enhanceLevels[i]=0;C2SSkillEnhancePayload.send(skillRegName,i,0);}
    private void onPlus(int i){if(i<0||i>=enhanceEntries.size()||!(enhanceEntries.get(i) instanceof EnhanceEntry.Attribute))return;var m=Minecraft.getInstance();if(m.player==null)return;if(C2SSkillEnhancePayload.getTotalXp(m.player)<XP_COST_PER_LEVEL)return;enhanceLevels[i]++;C2SSkillEnhancePayload.send(skillRegName,i,enhanceLevels[i]);}
    private void resetEnhance(int i){if(i<0||i>=enhanceEntries.size()||enhanceLevels[i]<=0)return;enhanceLevels[i]=0;C2SSkillEnhancePayload.send(skillRegName,i,0);}

    private boolean isUpgrade(double mx, double my) { Slot s = this.menu.slots.get(0); return mx>=leftPos+s.x&&mx<leftPos+s.x+18&&my>=topPos+s.y&&my<topPos+s.y+18; }
    private boolean isEnh(double mx, double my, int i) { int ox=cOffX(),oy=cOffY(); int x=leftPos+ENHANCE_SLOTS[i][0]+ox, y=topPos+ENHANCE_SLOTS[i][1]+oy; return mx>=x&&mx<x+ENHANCE_SIZE&&my>=y&&my<y+ENHANCE_SIZE; }
    private boolean isClose(double mx, double my) { Panel p=panels.get("F"); int px=leftPos-1+p.x,py=topPos-1+p.y; return mx>=px&&mx<px+p.w&&my>=py&&my<py+p.h; }

    private ItemStack getUpgradeItem() { return this.menu.slots.get(0).getItem(); }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int ox=aOffX(),oy=aOffY(),px=leftPos+PREVIEW_X+ox,py=topPos+PREVIEW_Y+oy;
        if(mx>=px&&mx<px+PREVIEW_W&&my>=py&&my<py+PREVIEW_H){previewScroll=Math.max(0,previewScroll-(int)Math.signum(sy)*10);return true;}
        return false;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt); this.renderTooltip(g, mx, my);
        int ox=cOffX(),oy=cOffY();
        for(int i=0;i<ENHANCE_SLOTS.length;i++) if(isEnh(mx,my,i)&&i<enhanceEntries.size()){
            var e=enhanceEntries.get(i);
            if(e instanceof EnhanceEntry.Tag tg) g.renderTooltip(font,Component.literal(tg.displayName()+(enhanceLevels[i]>0?" [已激活]":" [未激活]")+"\n"+tg.description()+"\n左键激活/停用 | 右键预览 | Shift+左键取消"),mx,my);
            else g.renderTooltip(font,Component.literal(e.displayName()+" Lv."+enhanceLevels[i]+"\n左键+1 | 右键预览 | Shift+左键归零"),mx,my);
            break;
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}
}
