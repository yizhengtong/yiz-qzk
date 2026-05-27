package net.minecraft.client.yiz.client.render;

import org.lwjgl.glfw.GLFW;

/** GLFW key code → Windows VK code. 0 表示不支持/未映射，调用方应丢弃。 */
final class GlfwToVk {
    private GlfwToVk() {}

    static int translate(int glfw) {
        if (glfw >= GLFW.GLFW_KEY_A && glfw <= GLFW.GLFW_KEY_Z) {
            return 'A' + (glfw - GLFW.GLFW_KEY_A);
        }
        if (glfw >= GLFW.GLFW_KEY_0 && glfw <= GLFW.GLFW_KEY_9) {
            return '0' + (glfw - GLFW.GLFW_KEY_0);
        }
        if (glfw >= GLFW.GLFW_KEY_F1 && glfw <= GLFW.GLFW_KEY_F12) {
            return 0x70 + (glfw - GLFW.GLFW_KEY_F1);
        }
        return switch (glfw) {
            case GLFW.GLFW_KEY_SPACE         -> 0x20;
            case GLFW.GLFW_KEY_ENTER         -> 0x0D;
            case GLFW.GLFW_KEY_KP_ENTER      -> 0x0D;
            case GLFW.GLFW_KEY_TAB           -> 0x09;
            case GLFW.GLFW_KEY_BACKSPACE     -> 0x08;
            case GLFW.GLFW_KEY_DELETE        -> 0x2E;
            case GLFW.GLFW_KEY_INSERT        -> 0x2D;
            case GLFW.GLFW_KEY_ESCAPE        -> 0x1B;
            case GLFW.GLFW_KEY_LEFT          -> 0x25;
            case GLFW.GLFW_KEY_UP            -> 0x26;
            case GLFW.GLFW_KEY_RIGHT         -> 0x27;
            case GLFW.GLFW_KEY_DOWN          -> 0x28;
            case GLFW.GLFW_KEY_HOME          -> 0x24;
            case GLFW.GLFW_KEY_END           -> 0x23;
            case GLFW.GLFW_KEY_PAGE_UP       -> 0x21;
            case GLFW.GLFW_KEY_PAGE_DOWN     -> 0x22;
            case GLFW.GLFW_KEY_LEFT_SHIFT,
                 GLFW.GLFW_KEY_RIGHT_SHIFT   -> 0x10;
            case GLFW.GLFW_KEY_LEFT_CONTROL,
                 GLFW.GLFW_KEY_RIGHT_CONTROL -> 0x11;
            case GLFW.GLFW_KEY_LEFT_ALT,
                 GLFW.GLFW_KEY_RIGHT_ALT     -> 0x12;
            case GLFW.GLFW_KEY_COMMA         -> 0xBC;
            case GLFW.GLFW_KEY_PERIOD        -> 0xBE;
            case GLFW.GLFW_KEY_SLASH         -> 0xBF;
            case GLFW.GLFW_KEY_SEMICOLON     -> 0xBA;
            case GLFW.GLFW_KEY_APOSTROPHE    -> 0xDE;
            case GLFW.GLFW_KEY_LEFT_BRACKET  -> 0xDB;
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> 0xDD;
            case GLFW.GLFW_KEY_BACKSLASH     -> 0xDC;
            case GLFW.GLFW_KEY_MINUS         -> 0xBD;
            case GLFW.GLFW_KEY_EQUAL         -> 0xBB;
            case GLFW.GLFW_KEY_GRAVE_ACCENT  -> 0xC0;
            default -> 0;
        };
    }
}
