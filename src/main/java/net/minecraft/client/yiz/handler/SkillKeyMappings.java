package net.minecraft.client.yiz.handler;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * 技能释放按键绑定。
 * <pre>
 *   Y = 大装载槽 (big load)
 *   R = 当前选择的小技能槽
 *   V = 切换下一个小技能槽（1→2→3→1）
 * </pre>
 */
public final class SkillKeyMappings {

    public static final String CATEGORY = "key.categories.yizmodqzk";

    public static final KeyMapping BIG    = key("skill_big",    GLFW.GLFW_KEY_Y);
    public static final KeyMapping SKILL  = key("skill_small",  GLFW.GLFW_KEY_R);
    public static final KeyMapping SWITCH = key("skill_switch", GLFW.GLFW_KEY_V);

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.yizmodqzk." + name,
            InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void registerAll(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(BIG);
        event.register(SKILL);
        event.register(SWITCH);
    }
}
