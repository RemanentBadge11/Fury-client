package com.lionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL11.*;

public final class ShaderDraw {
    private static int blurProgram = -1;
    private static int roundedProgram = -1;
    
    private static Framebuffer horizontalBuffer;

    private static final String VERTEX_SOURCE = 
            "#version 120\n" +
            "void main() {\n" +
            "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}\n";

    private static final String BLUR_FRAGMENT_SOURCE =
            "#version 120\n" +
            "uniform sampler2D texture;\n" +
            "uniform vec2 texelSize;\n" +
            "uniform float radius;\n" +
            "uniform vec2 direction;\n" +
            "void main() {\n" +
            "    vec4 color = vec4(0.0);\n" +
            "    float totalWeight = 0.0;\n" +
            "    for (float f = -radius; f <= radius; f++) {\n" +
            "        float weight = exp(-f * f / (2.0 * radius * radius));\n" +
            "        color += texture2D(texture, gl_TexCoord[0].xy + f * texelSize * direction) * weight;\n" +
            "        totalWeight += weight;\n" +
            "    } \n" +
            "    gl_FragColor = color / totalWeight;\n" +
            "}\n";

    private static final String ROUNDED_FRAGMENT_SOURCE =
            "#version 120\n" +
            "uniform vec2 size;\n" +
            "uniform vec4 color1;\n" +
            "uniform vec4 color2;\n" +
            "uniform float roundness;\n" +
            "uniform float border;\n" +
            "uniform vec4 borderColor;\n" +
            "float sdRoundRect(vec2 p, vec2 b, float r) {\n" +
            "    vec2 d = abs(p) - b + vec2(r);\n" +
            "    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - r;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 halfSize = size * 0.5;\n" +
            "    vec2 p = gl_TexCoord[0].xy * size - halfSize;\n" +
            "    float d = sdRoundRect(p, halfSize, roundness);\n" +
            "    vec4 col = mix(color1, color2, gl_TexCoord[0].y);\n" +
            "    if (border > 0.0) {\n" +
            "        float outer = smoothstep(1.0, 0.0, d);\n" +
            "        float inner = smoothstep(1.0, 0.0, d + border);\n" +
            "        gl_FragColor = mix(vec4(borderColor.rgb, borderColor.a * outer), col, inner);\n" +
            "    } else {\n" +
            "        float alpha = smoothstep(1.0, 0.0, d);\n" +
            "        gl_FragColor = vec4(col.rgb, col.a * alpha);\n" +
            "    }\n" +
            "}\n";

    public static void init() {
        if (blurProgram == -1) {
            blurProgram = compileProgram(VERTEX_SOURCE, BLUR_FRAGMENT_SOURCE);
        }
        if (roundedProgram == -1) {
            roundedProgram = compileProgram(VERTEX_SOURCE, ROUNDED_FRAGMENT_SOURCE);
        }
    }

    private static int compileProgram(String vert, String frag) {
        int program = glCreateProgram();
        int vertShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertShader, vert);
        glCompileShader(vertShader);
        if (glGetShaderi(vertShader, GL_COMPILE_STATUS) == GL_FALSE) {
            return -1;
        }

        int fragShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragShader, frag);
        glCompileShader(fragShader);
        if (glGetShaderi(fragShader, GL_COMPILE_STATUS) == GL_FALSE) {
            return -1;
        }

        glAttachShader(program, vertShader);
        glAttachShader(program, fragShader);
        glLinkProgram(program);
        glDeleteShader(vertShader);
        glDeleteShader(fragShader);
        return program;
    }

    public static void drawRoundedRect(float x, float y, float w, float h, float radius, int color) {
        drawRoundedRect(x, y, w, h, radius, color, color);
    }

    public static void drawRoundedRect(float x, float y, float w, float h, float radius, int color1, int color2) {
        drawRoundedRect(x, y, w, h, radius, 0.0F, 0, color1, color2);
    }

    public static void drawRoundedRect(float x, float y, float w, float h, float radius, float border, int borderColor, int color1, int color2) {
        init();
        if (roundedProgram == -1) {
            // standard opengl fallback
            drawFallbackRounded(x, y, w, h, radius, color1);
            return;
        }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(roundedProgram);

        glUniform2f(glGetUniformLocation(roundedProgram, "size"), w, h);
        glUniform1f(glGetUniformLocation(roundedProgram, "roundness"), radius);
        glUniform1f(glGetUniformLocation(roundedProgram, "border"), border);

        float[] c1 = getColors(color1);
        float[] c2 = getColors(color2);
        float[] bc = getColors(borderColor);

        glUniform4f(glGetUniformLocation(roundedProgram, "color1"), c1[0], c1[1], c1[2], c1[3]);
        glUniform4f(glGetUniformLocation(roundedProgram, "color2"), c2[0], c2[1], c2[2], c2[3]);
        glUniform4f(glGetUniformLocation(roundedProgram, "borderColor"), bc[0], bc[1], bc[2], bc[3]);

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(x, y, 0.0D).tex(0, 0).endVertex();
        worldrenderer.pos(x, y + h, 0.0D).tex(0, 1).endVertex();
        worldrenderer.pos(x + w, y + h, 0.0D).tex(1, 1).endVertex();
        worldrenderer.pos(x + w, y, 0.0D).tex(1, 0).endVertex();
        tessellator.draw();

        glUseProgram(0);
        glDisable(GL_BLEND);
    }

    public static void drawBlur(float x, float y, float w, float h, float radius) {
        init();
        if (blurProgram == -1) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (horizontalBuffer == null || horizontalBuffer.framebufferWidth != mc.displayWidth || horizontalBuffer.framebufferHeight != mc.displayHeight) {
            if (horizontalBuffer != null) {
                horizontalBuffer.deleteFramebuffer();
            }
            horizontalBuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
        }

        horizontalBuffer.framebufferClear();
        horizontalBuffer.bindFramebuffer(true);
        glUseProgram(blurProgram);
        glUniform1f(glGetUniformLocation(blurProgram, "radius"), radius);
        glUniform2f(glGetUniformLocation(blurProgram, "direction"), 1.0F, 0.0F);
        glUniform2f(glGetUniformLocation(blurProgram, "texelSize"), 1.0F / mc.displayWidth, 1.0F / mc.displayHeight);
        glBindTexture(GL_TEXTURE_2D, mc.getFramebuffer().framebufferTexture);
        
        drawQuad(x, y, w, h);

        mc.getFramebuffer().bindFramebuffer(true);
        glUniform2f(glGetUniformLocation(blurProgram, "direction"), 0.0F, 1.0F);
        glBindTexture(GL_TEXTURE_2D, horizontalBuffer.framebufferTexture);
        
        drawQuad(x, y, w, h);

        glUseProgram(0);
    }

    private static void drawQuad(float x, float y, float w, float h) {
        float sw = Minecraft.getMinecraft().displayWidth;
        float sh = Minecraft.getMinecraft().displayHeight;
        
        float minU = x / sw;
        float maxU = (x + w) / sw;
        float minV = (sh - y) / sh;
        float maxV = (sh - (y + h)) / sh;

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(x, y, 0.0D).tex(minU, minV).endVertex();
        worldrenderer.pos(x, y + h, 0.0D).tex(minU, maxV).endVertex();
        worldrenderer.pos(x + w, y + h, 0.0D).tex(maxU, maxV).endVertex();
        worldrenderer.pos(x + w, y, 0.0D).tex(maxU, minV).endVertex();
        tessellator.draw();
    }

    private static float[] getColors(int color) {
        return new float[] {
            ((color >> 16) & 255) / 255.0F,
            ((color >> 8) & 255) / 255.0F,
            (color & 255) / 255.0F,
            ((color >> 24) & 255) / 255.0F
        };
    }

    private static void drawFallbackRounded(float x, float y, float w, float h, float radius, int color) {
        glEnable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4ub((byte)(color >> 16 & 255), (byte)(color >> 8 & 255), (byte)(color & 255), (byte)(color >>> 24 & 255));
        
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        worldrenderer.pos(x + radius, y, 0.0D).endVertex();
        worldrenderer.pos(x + radius, y + h, 0.0D).endVertex();
        worldrenderer.pos(x + w - radius, y + h, 0.0D).endVertex();
        worldrenderer.pos(x + w - radius, y, 0.0D).endVertex();
        
        worldrenderer.pos(x, y + radius, 0.0D).endVertex();
        worldrenderer.pos(x, y + h - radius, 0.0D).endVertex();
        worldrenderer.pos(x + radius, y + h - radius, 0.0D).endVertex();
        worldrenderer.pos(x + radius, y + radius, 0.0D).endVertex();

        worldrenderer.pos(x + w - radius, y + radius, 0.0D).endVertex();
        worldrenderer.pos(x + w - radius, y + h - radius, 0.0D).endVertex();
        worldrenderer.pos(x + w, y + h - radius, 0.0D).endVertex();
        worldrenderer.pos(x + w, y + radius, 0.0D).endVertex();
        tessellator.draw();
        
        // draw 4 corners
        drawCorner(x + radius, y + radius, radius, 180, color);
        drawCorner(x + w - radius, y + radius, radius, 270, color);
        drawCorner(x + radius, y + h - radius, radius, 90, color);
        drawCorner(x + w - radius, y + h - radius, radius, 0, color);
        
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }

    private static void drawCorner(float cx, float cy, float r, int startAngle, int color) {
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL_TRIANGLE_FAN, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        worldrenderer.pos(cx, cy, 0.0D).endVertex();
        for (int i = 0; i <= 8; i++) {
            double angle = Math.toRadians(startAngle + i * 11.25);
            worldrenderer.pos(cx + r * Math.cos(angle), cy + r * Math.sin(angle), 0.0D).endVertex();
        }
        tessellator.draw();
    }
}
