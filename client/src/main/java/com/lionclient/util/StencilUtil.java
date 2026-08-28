package com.lionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;

public final class StencilUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void checkSetup(Framebuffer framebuffer) {
        if (framebuffer != null && framebuffer.depthBuffer > -1) {
            setupColumns(framebuffer);
            framebuffer.depthBuffer = -1;
        }
    }

    public static void setupColumns(Framebuffer framebuffer) {
        EXTFramebufferObject.glDeleteRenderbuffersEXT(framebuffer.depthBuffer);
        int stencilDepthBufferId = EXTFramebufferObject.glGenRenderbuffersEXT();
        EXTFramebufferObject.glBindRenderbufferEXT(36161, stencilDepthBufferId); // GL_RENDERBUFFER_EXT
        EXTFramebufferObject.glRenderbufferStorageEXT(36161, 34041, mc.displayWidth, mc.displayHeight); // GL_DEPTH_STENCIL_TO_DEPTH24_STENCIL8
        EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36128, 36161, stencilDepthBufferId); // GL_DEPTH_ATTACHMENT_EXT
        EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36096, 36161, stencilDepthBufferId); // GL_STENCIL_ATTACHMENT_EXT
    }

    public static void initStencilToWrite() {
        mc.getFramebuffer().bindFramebuffer(false);
        checkSetup(mc.getFramebuffer());
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        GL11.glColorMask(false, false, false, false);
    }

    public static void readStencilBuffer(int ref) {
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, ref, 1);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    public static void uninitStencilBuffer() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }
}
