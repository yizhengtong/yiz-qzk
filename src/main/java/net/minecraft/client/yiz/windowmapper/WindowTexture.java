package net.minecraft.client.yiz.windowmapper;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/**
 * Manages a single OpenGL texture that receives desktop window frames.
 *
 * <p>The texture accepts BGRA pixel data directly from the native capture
 * layer, avoiding any pixel format conversion overhead.</p>
 */
public class WindowTexture implements AutoCloseable {

    private int glTextureId;
    private int width;
    private int height;
    private long lastUploadNanos;

    public WindowTexture() {
        glTextureId = GL11.glGenTextures();
        width = 1;
        height = 1;

        // Initialize as a blank 1x1 texture
        RenderSystem.bindTexture(glTextureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                width, height, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        setDefaultParams();
    }

    /**
     * Update the texture from a captured frame buffer (BGRA format).
     * Must be called from the render thread.
     */
    public boolean update(ByteBuffer buffer, int bufferWidth, int bufferHeight) {
        if (buffer == null) return false;
        if (bufferWidth <= 0 || bufferHeight <= 0) return false;

        // Defensive: glTexSubImage2D will read W*H*4 bytes from the buffer
        // starting at position(). If native ever returns a buffer smaller
        // than that, OpenGL crashes with EXCEPTION_ACCESS_VIOLATION inside
        // the driver. Drop the frame instead.
        long needed = (long) bufferWidth * bufferHeight * 4L;
        if (buffer.remaining() < needed) return false;

        long now = System.nanoTime();
        lastUploadNanos = now;

        RenderSystem.bindTexture(glTextureId);

        if (bufferWidth != width || bufferHeight != height) {
            // Resize: allocate new texture storage
            width = bufferWidth;
            height = bufferHeight;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                    width, height, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);
        } else {
            // Update sub-region in-place
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                    0, 0, width, height,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);
        }

        return true;
    }

    private void setDefaultParams() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    /** OpenGL texture name. */
    public int getGlTextureId() {
        return glTextureId;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    /** When the last successful upload happened (nanos from {@code System.nanoTime}). */
    public long getLastUploadNanos() { return lastUploadNanos; }

    @Override
    public void close() {
        if (glTextureId != 0) {
            GL11.glDeleteTextures(glTextureId);
            glTextureId = 0;
        }
    }
}
