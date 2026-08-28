package com.lionclient.util;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public final class TrueTypeFontRenderer {
    private final Font font;
    private final Map<Character, Glyph> glyphMap = new HashMap<Character, Glyph>();
    private int textureId = -1;
    private int textureWidth = 512;
    private int textureHeight = 512;
    
    public TrueTypeFontRenderer(Font font) {
        this.font = font;
        generateGlyphs();
    }
    
    private void generateGlyphs() {
        BufferedImage img = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setFont(font);
        g.setColor(new Color(255, 255, 255, 255));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        
        FontMetrics metrics = g.getFontMetrics();
        int x = 2;
        int y = 2;
        int rowHeight = metrics.getHeight();
        
        for (int i = 32; i < 256; i++) {
            if (i == 127) continue;
            char c = (char) i;
            int charWidth = metrics.charWidth(c);
            if (charWidth <= 0) charWidth = 1;
            
            if (x + charWidth >= textureWidth) {
                x = 2;
                y += rowHeight + 2;
            }
            
            g.drawString(String.valueOf(c), x, y + metrics.getAscent());
            
            Glyph glyph = new Glyph(
                (float) x / textureWidth,
                (float) y / textureHeight,
                (float) charWidth / textureWidth,
                (float) rowHeight / textureHeight,
                charWidth,
                rowHeight
            );
            glyphMap.put(c, glyph);
            x += charWidth + 2;
        }
        g.dispose();
        
        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        
        int[] pixels = new int[textureWidth * textureHeight];
        img.getRGB(0, 0, textureWidth, textureHeight, pixels, 0, textureWidth);
        
        IntBuffer buffer = java.nio.ByteBuffer.allocateDirect(pixels.length * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asIntBuffer();
        buffer.put(pixels);
        ((java.nio.Buffer) buffer).flip();
        
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, textureWidth, textureHeight, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
    }
    
    public void drawString(String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;
        
        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        float a = ((color >> 24) & 255) / 255.0F;
        if (a == 0.0F) a = 1.0F;
        
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glColor4f(r, g, b, a);
        
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        
        float currentX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Glyph glyph = glyphMap.get(c);
            if (glyph == null) glyph = glyphMap.get('?');
            if (glyph != null) {
                worldrenderer.pos(currentX, y, 0.0D).tex(glyph.u, glyph.v).endVertex();
                worldrenderer.pos(currentX, y + glyph.height, 0.0D).tex(glyph.u, glyph.v + glyph.h).endVertex();
                worldrenderer.pos(currentX + glyph.width, y + glyph.height, 0.0D).tex(glyph.u + glyph.w, glyph.v + glyph.h).endVertex();
                worldrenderer.pos(currentX + glyph.width, y, 0.0D).tex(glyph.u + glyph.w, glyph.v).endVertex();
                
                currentX += glyph.width - 2.0F;
            }
        }
        tessellator.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
    }
    
    public int getStringWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Glyph glyph = glyphMap.get(c);
            if (glyph == null) glyph = glyphMap.get('?');
            if (glyph != null) {
                width += glyph.width - 2;
            }
        }
        return width;
    }
    
    public int getFontHeight() {
        Glyph glyph = glyphMap.get('A');
        return glyph != null ? glyph.height : 14;
    }
    
    private static final class Glyph {
        private final float u;
        private final float v;
        private final float w;
        private final float h;
        private final int width;
        private final int height;
        
        private Glyph(float u, float v, float w, float h, int width, int height) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.width = width;
            this.height = height;
        }
    }
}
