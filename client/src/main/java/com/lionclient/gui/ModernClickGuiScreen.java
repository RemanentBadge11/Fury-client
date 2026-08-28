package com.lionclient.gui;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
import com.lionclient.feature.module.impl.ClickGuiModule;
import com.lionclient.feature.setting.ActionSetting;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.IntRangeSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.feature.setting.Setting;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import com.lionclient.session.SessionManager;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Recoded ClickGUI matching OpenMyau-Plus-2.0-4's RiseClickGUI.
 * 
 * Includes:
 * - Tabbed navigation (Search, Combat, Movement, Player, Render, Client, Misc, Themes)
 * - Draggable centered panel
 * - RiseColors color scheme
 * - RiseModuleCard & RiseValueEditor elements
 * - Keybind editing
 * - Text search filter
 * - Smooth scroll & fade animations
 * - Scaled rendering using vanilla fontRendererObj (eliminating TTF-related crashes)
 * - Stencil-free scissor masking
 */
public final class ModernClickGuiScreen extends GuiScreen {

    private enum Tab {
        SEARCH("Search", "Find any module", false),
        COMBAT("Combat", "Aggressive & ghost combat tools", true),
        MOVEMENT("Movement", "Movement and traversal", true),
        PLAYER("Player", "Inventory and automation", true),
        RENDER("Render", "Visuals and interface", true),
        CLIENT("Client", "Client core settings", true),
        MISC("Misc", "Everything else", true),
        THEMES("Appearance", "Personalize the interface", false),
        CONFIGS("Configs", "Profiles & presets", false);

        private final String label;
        private final String subtitle;
        private final boolean moduleTab;

        Tab(String label, String subtitle, boolean moduleTab) {
            this.label = label;
            this.subtitle = subtitle;
            this.moduleTab = moduleTab;
        }
    }

    // ======================== COLOR SCHEME ========================
    private static final class RiseColors {
        static final Color BACKDROP = new Color(4, 6, 10, 172);
        static final Color SURFACE_SOFT = new Color(255, 255, 255, 9);
        static final Color BORDER = new Color(255, 255, 255, 22);
        static final Color BORDER_STRONG = new Color(255, 255, 255, 40);
        static final Color TEXT = new Color(255, 255, 255, 255);
        static final Color TEXT_SECONDARY = new Color(218, 224, 235, 220);
        static final Color TEXT_TRINARY = new Color(151, 161, 179, 185);

        static Color getBackground() {
            ClickGuiModule.ThemePalette palette = ClickGuiModule.getThemePalette();
            if (palette != null) {
                return new Color(palette.getWindowColor() | 0xFF000000);
            }
            return new Color(14, 17, 23, 252);
        }

        static Color getSecondary() {
            ClickGuiModule.ThemePalette palette = ClickGuiModule.getThemePalette();
            if (palette != null) {
                return new Color(palette.getSidebarColor() | 0xFF000000);
            }
            return new Color(10, 12, 17, 255);
        }

        static Color getSurface() {
            ClickGuiModule.ThemePalette palette = ClickGuiModule.getThemePalette();
            if (palette != null) {
                return new Color(palette.getPanelColor() | 0xFF000000);
            }
            return new Color(21, 25, 33, 244);
        }

        static Color getSurfaceHigh() {
            ClickGuiModule.ThemePalette palette = ClickGuiModule.getThemePalette();
            if (palette != null) {
                return new Color(palette.getPanelAltColor() | 0xFF000000);
            }
            return new Color(27, 32, 42, 248);
        }

        static Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
        }

        static int withAlphaRGB(Color color, int alpha) {
            return withAlpha(color, alpha).getRGB();
        }
    }

    private static final long ANIMATION_DURATION = 260L;

    private final ModuleManager moduleManager;
    private final Map<Tab, List<RiseModuleCard>> moduleCards = new EnumMap<Tab, List<RiseModuleCard>>(Tab.class);
    private final List<RiseModuleCard> allCards = new ArrayList<RiseModuleCard>();
    private final List<RiseModuleCard> searchResults = new ArrayList<RiseModuleCard>();
    private final float[] sidebarAnimations = new float[Tab.values().length];

    private float windowX = -1f;
    private float windowY = -1f;
    private float windowWidth = 520f;
    private float windowHeight = 350f;
    private float sidebarWidth = 120f;
    private float topbarHeight = 46f;
    private float cornerRadius = 8f;

    private boolean firstOpen = true;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean closing;
    private float scaleAnimation;
    private float opacityAnimation;
    private float transitionAlpha;
    private long animationStart;
    private long openedAt;
    private long lastFrameTime;

    private Tab selectedTab = Tab.SEARCH;
    private String searchText = "";
    private float scrollOffset;
    private float targetScroll;

    private boolean editingNick = false;
    private String editingNickText = "";
    private long lastNickSwapTime = 0L;
    private String nickFeedbackMessage = "";

    private final List<ConfigCard> userConfigs = new ArrayList<ConfigCard>();
    private boolean configsNeedRefresh = true;
    private boolean creatingNewConfig = false;
    private String newConfigName = "";
    private float configScrollOffset = 0f;
    private float configTargetScroll = 0f;

    public ModernClickGuiScreen(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        rebuildModuleCache();
    }

    // ======================== LIFECYCLE ========================

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        closing = false;
        animationStart = System.currentTimeMillis();
        openedAt = animationStart;
        lastFrameTime = System.nanoTime();

        ScaledResolution resolution = new ScaledResolution(mc);
        if (firstOpen || windowX < 8 || windowY < 8 || windowX + windowWidth > resolution.getScaledWidth() - 8
                || windowY + windowHeight > resolution.getScaledHeight() - 8) {
            windowX = resolution.getScaledWidth() / 2f - windowWidth / 2f;
            windowY = resolution.getScaledHeight() / 2f - windowHeight / 2f;
            firstOpen = false;
        }

        rebuildModuleCache();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        dragging = false;
        for (RiseModuleCard card : allCards) {
            card.released();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ======================== DRAWING ========================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        float deltaTime = frameDelta();
        updateOpenCloseAnimation();
        if (closing && scaleAnimation <= 0.001f) {
            LionClient client = LionClient.getInstance();
            if (client != null) {
                client.toggleClickGui();
            } else {
                mc.displayGuiScreen(null);
            }
            return;
        }

        if (dragging) {
            windowX = mouseX + dragOffsetX;
            windowY = mouseY + dragOffsetY;
            clampWindow();
        }

        updateScroll(deltaTime);
        drawBackdrop();

        GlStateManager.pushMatrix();
        if (scaleAnimation < 0.999f) {
            float centerX = windowX + windowWidth / 2f;
            float centerY = windowY + windowHeight / 2f;
            GlStateManager.translate(centerX * (1f - scaleAnimation), centerY * (1f - scaleAnimation), 0);
            GlStateManager.scale(scaleAnimation, scaleAnimation, 1);
        }

        // Draw window shadow
        drawRoundedRect(windowX + 2, windowY + 2, windowWidth, windowHeight, cornerRadius,
                new Color(0, 0, 0, (int) (60 * opacityAnimation)).getRGB(), true, true, true, true);

        // Draw main body background
        drawRoundedRect(windowX, windowY, windowWidth, windowHeight, cornerRadius,
                RiseColors.withAlpha(RiseColors.getBackground(), (int) (252 * opacityAnimation)).getRGB(),
                true, true, true, true);

        drawWindowChrome();
        drawAccentGlow();
        drawSidebar(mouseX, mouseY, deltaTime);
        drawTopbar(mouseX, mouseY);

        float contentX = contentX();
        float contentY = contentY();
        float contentWidth = contentWidth();
        float contentHeight = contentHeight();

        // Scissor clip content area
        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (contentX * sf), (int) ((sr.getScaledHeight() - (contentY + contentHeight)) * sf),
                (int) (contentWidth * sf), (int) (contentHeight * sf));

        drawSelectedScreen(contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, partialTicks, deltaTime);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        drawTransition(deltaTime);
        GlStateManager.popMatrix();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float deltaTime = Math.min((now - lastFrameTime) / 1_000_000_000.0f, 0.05f);
        lastFrameTime = now;
        return deltaTime;
    }

    private void updateOpenCloseAnimation() {
        float progress = Math.min(1f, (System.currentTimeMillis() - animationStart) / (float) ANIMATION_DURATION);
        if (closing) {
            scaleAnimation = 1f - easeOutExpo(progress);
            opacityAnimation = 1f - progress;
        } else {
            scaleAnimation = easeOutExpo(progress);
            opacityAnimation = Math.min(1f, progress * 1.7f);
        }
    }

    private void drawBackdrop() {
        drawRect(0, 0, width, height, RiseColors.BACKDROP.getRGB());
        int accent = ClickGuiModule.getModernAccentColor();
        Color accentColor = new Color(accent);
        int glow = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (10 * opacityAnimation)).getRGB();
        drawRoundedRect(windowX - 60, windowY - 60, 180, 180, 90, glow, true, true, true, true);
    }

    private void drawWindowChrome() {
        drawRoundedRectOutline(windowX + 0.5f, windowY + 0.5f, windowWidth - 1f, windowHeight - 1f,
                cornerRadius, 1f, RiseColors.withAlpha(RiseColors.BORDER_STRONG, (int) (70 * opacityAnimation)).getRGB(),
                true, true, true, true);
        drawRect((int) (windowX + sidebarWidth), (int) (windowY + 1f), (int) (windowX + sidebarWidth + 1f),
                (int) (windowY + windowHeight - 1f), RiseColors.BORDER.getRGB());
        drawRect((int) (windowX + sidebarWidth + 1f), (int) (windowY + topbarHeight),
                (int) (windowX + windowWidth - 1f), (int) (windowY + topbarHeight + 1f), RiseColors.BORDER.getRGB());
    }

    private void drawAccentGlow() {
        Color accent = new Color(ClickGuiModule.getModernAccentColor());
        for (int i = 0; i < 4; i++) {
            float size = 30f + i * 18f;
            drawRoundedRect(windowX + sidebarWidth - size / 2f, windowY + 28f - size / 2f,
                    size, size, size / 2f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.max(1, 6 - i)).getRGB(),
                    true, true, true, true);
        }
    }

    private void drawSidebar(int mouseX, int mouseY, float deltaTime) {
        drawRoundedRect(windowX, windowY, sidebarWidth, windowHeight, cornerRadius,
                RiseColors.withAlpha(RiseColors.getSecondary(), (int) (255 * opacityAnimation)).getRGB(),
                true, false, true, false);

        int accent = ClickGuiModule.getModernAccentColor();
        Color accentColor = new Color(accent);
        drawRoundedRect(windowX + 10, windowY + 10, 22, 22, 6,
                new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 230).getRGB(),
                true, true, true, true);

        drawStringScaled("F", windowX + 17, windowY + 14, Color.WHITE.getRGB(), 1.2f);
        drawStringScaled("Fury Client", windowX + 38, windowY + 12, RiseColors.TEXT.getRGB(), 0.95f);
        drawStringScaled("PREMIUM INJECT", windowX + 39, windowY + 24, RiseColors.withAlpha(accentColor, 210).getRGB(), 0.6f);

        float navigationY = windowY + 45f;
        drawStringScaled("NAVIGATION", windowX + 10, navigationY, RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
        navigationY += 10f;

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            float itemX = windowX + 6f;
            float itemY = navigationY + i * tabStep();
            float itemWidth = sidebarWidth - 12f;
            float itemHeight = tabItemHeight();
            boolean selected = selectedTab == tab;
            boolean hovered = over(itemX, itemY, itemWidth, itemHeight, mouseX, mouseY);
            sidebarAnimations[i] = animateSmooth(selected ? 1f : 0f, sidebarAnimations[i], 12f, deltaTime);

            if (hovered || sidebarAnimations[i] > 0.01f) {
                int alpha = (int) (hovered ? 22 : 10);
                drawRoundedRect(itemX, itemY, itemWidth, itemHeight, 4,
                        new Color(255, 255, 255, alpha).getRGB(), true, true, true, true);
            }
            if (sidebarAnimations[i] > 0.01f) {
                drawRoundedRect(itemX, itemY, 2f, itemHeight, 1f,
                        new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                                (int) (255 * sidebarAnimations[i])).getRGB(),
                        true, true, true, true);
            }

            drawStringScaled(tab.label, itemX + 8, itemY + 4,
                    selected ? RiseColors.TEXT.getRGB() : RiseColors.TEXT_SECONDARY.getRGB(), 0.8f);

            if (tab.moduleTab) {
                String count = String.valueOf(moduleCards.get(tab).size());
                float countWidth = fontRendererObj.getStringWidth(count) * 0.65f;
                drawStringScaled(count, itemX + itemWidth - countWidth - 6, itemY + 5,
                        selected ? RiseColors.withAlpha(accentColor, 230).getRGB() : RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
            }
        }

        if (windowHeight >= 300f) {
            drawSidebarFooter();
        }
    }

    private void drawSidebarFooter() {
        int enabled = enabledCount();
        float footerY = windowY + windowHeight - 40f;
        drawRoundedRect(windowX + 6, footerY, sidebarWidth - 12, 32, 5,
                RiseColors.SURFACE_SOFT.getRGB(), true, true, true, true);

        drawStringScaled("ACTIVE MODULES", windowX + 11, footerY + 6, RiseColors.TEXT_TRINARY.getRGB(), 0.62f);
        drawStringScaled(enabled + " / " + allCards.size(), windowX + 11, footerY + 18, RiseColors.TEXT_SECONDARY.getRGB(), 0.65f);

        String ver = "v" + LionClient.VERSION;
        float verW = fontRendererObj.getStringWidth(ver) * 0.65f;
        drawStringScaled(ver, windowX + sidebarWidth - verW - 11, footerY + 18,
                RiseColors.withAlpha(new Color(ClickGuiModule.getModernAccentColor()), 220).getRGB(), 0.65f);
    }

    private void submitNickEdit() {
        if (!editingNick) return;
        editingNick = false;
        if (!editingNickText.trim().isEmpty()) {
            boolean ok = SessionManager.getInstance().setOfflineSession(editingNickText.trim());
            if (ok) {
                lastNickSwapTime = System.currentTimeMillis();
                nickFeedbackMessage = "Swapped session to " + SessionManager.getInstance().getCurrentUsername();
            }
        }
    }

    private void drawTopbar(int mouseX, int mouseY) {
        float x = windowX + sidebarWidth + 12f;
        float y = windowY + 8f;
        drawStringScaled(selectedTab.label, x, y, RiseColors.TEXT.getRGB(), 1.2f);
        drawStringScaled(selectedTab.subtitle, x, y + 20f, RiseColors.TEXT_TRINARY.getRGB(), 0.65f);

        float searchWidth = Math.min(150f, Math.max(100f, windowWidth * 0.25f));
        float searchX = windowX + windowWidth - searchWidth - 12f;
        float searchY = windowY + 9f;

        float nickWidth = 135f;
        float nickX = searchX - nickWidth - 8f;
        float nickY = windowY + 9f;

        // Draw Nick Swapper Box
        boolean nickHovered = over(nickX, nickY, nickWidth, 24f, mouseX, mouseY);
        drawRoundedRect(nickX, nickY, nickWidth, 24f, 6,
                nickHovered || editingNick ? RiseColors.getSurfaceHigh().getRGB() : RiseColors.getSurface().getRGB(),
                true, true, true, true);
        drawRoundedRectOutline(nickX, nickY, nickWidth, 24f, 6, 1f,
                editingNick ? RiseColors.withAlpha(new Color(ClickGuiModule.getModernAccentColor()), 180).getRGB() : RiseColors.BORDER.getRGB(),
                true, true, true, true);

        String currentIgn = SessionManager.getInstance().getCurrentUsername();
        String nickDisp = editingNick ? editingNickText + "_" : currentIgn;
        int nickCol = editingNick ? RiseColors.TEXT.getRGB() : RiseColors.TEXT_SECONDARY.getRGB();
        drawStringScaled("NICK:", nickX + 7, nickY + 6, RiseColors.TEXT_TRINARY.getRGB(), 0.62f);
        drawStringScaled(nickDisp, nickX + 34, nickY + 6, nickCol, 0.78f);

        // Draw Search Bar
        boolean hovered = over(searchX, searchY, searchWidth, 24f, mouseX, mouseY);
        drawRoundedRect(searchX, searchY, searchWidth, 24f, 6,
                hovered || selectedTab == Tab.SEARCH ? RiseColors.getSurfaceHigh().getRGB() : RiseColors.getSurface().getRGB(),
                true, true, true, true);
        drawRoundedRectOutline(searchX, searchY, searchWidth, 24f, 6, 1f,
                selectedTab == Tab.SEARCH ? RiseColors.withAlpha(new Color(ClickGuiModule.getModernAccentColor()), 90).getRGB() : RiseColors.BORDER.getRGB(),
                true, true, true, true);

        String text = searchText.isEmpty() ? "Search..." : searchText;
        int txtCol = searchText.isEmpty() ? RiseColors.TEXT_TRINARY.getRGB() : RiseColors.TEXT.getRGB();
        drawStringScaled(text, searchX + 8, searchY + 6, txtCol, 0.8f);

        if (searchText.isEmpty()) {
            String shortcut = "TYPE";
            float shortcutWidth = fontRendererObj.getStringWidth(shortcut) * 0.6f;
            drawStringScaled(shortcut, searchX + searchWidth - shortcutWidth - 8, searchY + 8, RiseColors.TEXT_TRINARY.getRGB(), 0.6f);
        }

        if (System.currentTimeMillis() - lastNickSwapTime < 2800L && !nickFeedbackMessage.isEmpty()) {
            drawStringScaled(nickFeedbackMessage, x + 120f, y + 20f, new Color(46, 204, 113).getRGB(), 0.65f);
        }
    }

    private void drawSelectedScreen(float x, float y, float w, float h, int mouseX, int mouseY,
            float partialTicks, float deltaTime) {
        if (selectedTab == Tab.THEMES) {
            drawThemeScreen(x, y, w, h, mouseX, mouseY);
            return;
        }
        if (selectedTab == Tab.CONFIGS) {
            drawConfigScreen(x, y, w, h, mouseX, mouseY, deltaTime);
            return;
        }

        List<RiseModuleCard> cards = currentCards();
        drawModuleList(cards, x, y, w, h, mouseX, mouseY, partialTicks, deltaTime, selectedTab == Tab.SEARCH);
        drawScrollBar(x + w - 2, y, 2, h, scrollOffset, getMaxScroll());
    }

    private void drawModuleList(List<RiseModuleCard> cards, float x, float y, float w, float h, int mouseX, int mouseY,
            float partialTicks, float deltaTime, boolean searchMode) {
        if (cards == null || cards.isEmpty()) {
            drawEmptyState(x, y, w, h);
            return;
        }

        float cardY = y + 2f - scrollOffset;
        for (RiseModuleCard card : cards) {
            card.setX(x);
            card.setY(cardY);
            card.setCardWidth(w - 6f);
            card.draw(mouseX, mouseY, partialTicks, deltaTime, x, y, w, h, searchMode);
            cardY += card.getTotalHeight() + 6f;
        }
    }

    private void drawEmptyState(float x, float y, float w, float h) {
        float panelWidth = Math.min(220f, w - 20f);
        float panelHeight = 60f;
        float panelX = x + w / 2f - panelWidth / 2f;
        float panelY = y + h / 2f - panelHeight / 2f;
        drawRoundedRect(panelX, panelY, panelWidth, panelHeight, 6, RiseColors.getSurface().getRGB(), true, true, true, true);
        drawCenteredStringScaled("No modules found", x + w / 2f, panelY + 14, RiseColors.TEXT_SECONDARY.getRGB(), 1.0f);
        drawCenteredStringScaled("Try a different search phrase", x + w / 2f, panelY + 36, RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
    }

    private void drawThemeScreen(float x, float y, float w, float h, int mouseX, int mouseY) {
        ClickGuiModule clickGui = ClickGuiModule.getInstance();
        if (clickGui == null) return;

        drawStringScaled("Client Theme Presets", x + 3, y + 2, RiseColors.TEXT.getRGB(), 1.0f);
        drawStringScaled("Choose the client visual theme for interface elements and highlights.", x + 3, y + 16, RiseColors.TEXT_TRINARY.getRGB(), 0.65f);

        ClickGuiModule.ThemePreset[] presets = ClickGuiModule.ThemePreset.values();
        int cols = 3;
        float gap = 6f;
        float cardWidth = (w - gap * (cols + 1)) / cols;
        float cardHeight = 48f;

        ClickGuiModule.ThemePreset activePreset;
        try {
            activePreset = ClickGuiModule.ThemePreset.valueOf(ClickGuiModule.getThemeName().toUpperCase().replace(" ", "_"));
        } catch (Throwable t) {
            activePreset = ClickGuiModule.ThemePreset.DEEP_OCEAN;
        }

        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            ClickGuiModule.ThemePreset preset = presets[i];
            if (preset == ClickGuiModule.ThemePreset.CUSTOM) continue;

            int column = idx % cols;
            int row = idx / cols;
            float cardX = x + gap + column * (cardWidth + gap);
            float cardY = y + 36 + row * (cardHeight + gap);
            idx++;

            int color;
            switch (preset) {
                case CYBERPUNK: color = 0xC738FF; break;
                case AMBER_GOLD: color = 0xFFB000; break;
                case TOXIC_LIME: color = 0x84CC16; break;
                case BLOOD_RED: color = 0xEF4444; break;
                case ROYAL_INDIGO: color = 0x6366F1; break;
                case SUNSET_VIOLET: color = 0xE040FB; break;
                case STEEL_TEAL: color = 0x14B8A6; break;
                case CARBON_WHITE: color = 0xF3F4F6; break;
                case CRIMSON_BLACK: color = 0x990000; break;
                case RAINBOW: color = ClickGuiModule.getRainbowColor(); break;
                case RGB: color = ClickGuiModule.getTriRgbColor(); break;
                case DEEP_OCEAN:
                default: color = 0x0EA5E9; break;
            }
            boolean selected = activePreset == preset;
            boolean hovered = over(cardX, cardY, cardWidth, cardHeight, mouseX, mouseY);
            int background = hovered || selected ? RiseColors.getSurfaceHigh().getRGB() : RiseColors.getSurface().getRGB();

            drawRoundedRect(cardX, cardY, cardWidth, cardHeight, 6, background, true, true, true, true);
            drawRoundedRectOutline(cardX, cardY, cardWidth, cardHeight, 6, 1f,
                    selected ? (0xFF000000 | color) : RiseColors.BORDER.getRGB(), true, true, true, true);

            // Color swatch
            if (preset == ClickGuiModule.ThemePreset.RGB) {
                drawRoundedRect(cardX + 8, cardY + 12, 8, 24, 4, 0xFF000000 | ClickGuiModule.TRI_RED, true, false, false, true);
                drawRoundedRect(cardX + 16, cardY + 12, 8, 24, 0, 0xFF000000 | ClickGuiModule.TRI_GREEN, false, false, false, false);
                drawRoundedRect(cardX + 24, cardY + 12, 8, 24, 4, 0xFF000000 | ClickGuiModule.TRI_BLUE, false, true, true, false);
            } else {
                drawRoundedRect(cardX + 8, cardY + 12, 24, 24, 5, 0xFF000000 | color, true, true, true, true);
            }

            // For Rainbow, draw a secondary hue-shifted swatch overlapping for visual flair
            if (preset == ClickGuiModule.ThemePreset.RAINBOW) {
                int color2 = java.awt.Color.HSBtoRGB(((System.currentTimeMillis() + 1200L) % 5000L) / 5000.0F, 0.85F, 1.0F) & 0xFFFFFF;
                drawRoundedRect(cardX + 18, cardY + 12, 14, 24, 5, 0xFF000000 | color2, true, true, true, true);
            }

            drawStringScaled(preset.toString(), cardX + 40, cardY + 12, RiseColors.TEXT.getRGB(), 0.85f);
            drawStringScaled(selected ? "Active" : "Apply", cardX + 40, cardY + 26,
                    selected ? (0xFF000000 | color) : RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
        }
    }

    private void drawScrollBar(float x, float y, float w, float h, float scroll, float maxScroll) {
        if (maxScroll <= 0) return;
        float ratio = Math.max(0f, Math.min(1f, scroll / maxScroll));
        float barHeight = Math.max(20f, h * (h / (h + maxScroll)));
        float barY = y + ratio * (h - barHeight);
        drawRoundedRect(x, barY, w, barHeight, 1, new Color(ClickGuiModule.getModernAccentColor()).getRGB(), true, true, true, true);
    }

    private void drawTransition(float deltaTime) {
        if (transitionAlpha <= 0.01f) return;
        transitionAlpha = animateSmooth(0f, transitionAlpha, 10f, deltaTime);
        drawRoundedRect(contentX(), contentY(), contentWidth(), contentHeight(), 5,
                RiseColors.withAlpha(RiseColors.getBackground(), (int) (transitionAlpha * 170)).getRGB(),
                true, true, true, true);
    }

    // ======================== INPUT ========================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (closing) return;

        float searchWidth = Math.min(150f, Math.max(100f, windowWidth * 0.25f));
        float searchX = windowX + windowWidth - searchWidth - 12f;
        float nickWidth = 135f;
        float nickX = searchX - nickWidth - 8f;
        float nickY = windowY + 9f;

        // Click Nick Box
        if (over(nickX, nickY, nickWidth, 24f, mouseX, mouseY) && mouseButton == 0) {
            editingNick = !editingNick;
            if (editingNick) {
                editingNickText = SessionManager.getInstance().getCurrentUsername();
            } else {
                submitNickEdit();
            }
            return;
        } else if (editingNick && !over(nickX, nickY, nickWidth, 24f, mouseX, mouseY)) {
            submitNickEdit();
        }

        // Dragging top panel
        if (mouseButton == 0 && over(windowX + sidebarWidth, windowY, windowWidth - sidebarWidth, topbarHeight, mouseX, mouseY)
                && !overSearch(mouseX, mouseY) && !over(nickX, nickY, nickWidth, 24f, mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = windowX - mouseX;
            dragOffsetY = windowY - mouseY;
            return;
        }

        // Clicking search bar
        if (overSearch(mouseX, mouseY) && mouseButton == 0) {
            switchTab(Tab.SEARCH);
            return;
        }

        // Sidebar clicks
        float navigationY = windowY + 55f;
        for (int i = 0; i < Tab.values().length; i++) {
            if (over(windowX + 6f, navigationY + i * tabStep(), sidebarWidth - 12f, tabItemHeight(), mouseX, mouseY)) {
                switchTab(Tab.values()[i]);
                return;
            }
        }

        if (!over(contentX(), contentY(), contentWidth(), contentHeight(), mouseX, mouseY)) return;

        if (selectedTab == Tab.THEMES && clickTheme(mouseX, mouseY, mouseButton)) return;
        if (selectedTab == Tab.CONFIGS && clickConfigScreen(contentX(), contentY(), contentWidth(), contentHeight(), mouseX, mouseY, mouseButton)) return;

        List<RiseModuleCard> cards = currentCards();
        if (cards != null) {
            for (RiseModuleCard card : cards) {
                if (card.click(mouseX, mouseY, mouseButton)) return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        for (RiseModuleCard card : allCards) {
            card.released();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (closing) return;

        if (selectedTab == Tab.CONFIGS && keyConfigScreen(typedChar, keyCode)) {
            return;
        }

        if (editingNick) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                editingNick = false;
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                submitNickEdit();
                return;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (!editingNickText.isEmpty()) {
                    editingNickText = editingNickText.substring(0, editingNickText.length() - 1);
                }
                return;
            }
            if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && editingNickText.length() < 16) {
                editingNickText += typedChar;
            }
            return;
        }

        for (RiseModuleCard card : allCards) {
            if (card.isBindingKey()) {
                card.key(typedChar, keyCode);
                return;
            }
        }

        if (activeEditor()) {
            for (RiseModuleCard card : allCards) {
                card.key(typedChar, keyCode);
            }
            return;
        }

        if (selectedTab == Tab.SEARCH && searchText.startsWith(".") && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
            LionClient client = LionClient.getInstance();
            if (client != null && client.getCommandManager() != null) {
                client.getCommandManager().handleCommand(searchText);
            }
            searchText = "";
            updateSearchResults();
            resetModuleScroll();
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE || isGuiKey(keyCode)) {
            if (keyCode != Keyboard.KEY_ESCAPE && System.currentTimeMillis() - openedAt < 250L) return;
            if (selectedTab == Tab.SEARCH && !searchText.isEmpty()) {
                searchText = "";
                updateSearchResults();
                resetModuleScroll();
            } else {
                close();
            }
            return;
        }

        if (keyCode == Keyboard.KEY_BACK && selectedTab == Tab.SEARCH && !searchText.isEmpty()) {
            searchText = searchText.substring(0, searchText.length() - 1);
            updateSearchResults();
            resetModuleScroll();
            return;
        }

        if (isSearchCharacter(typedChar)) {
            if (selectedTab != Tab.SEARCH) switchTab(Tab.SEARCH);
            searchText += typedChar;
            updateSearchResults();
            resetModuleScroll();
            return;
        }

        List<RiseModuleCard> cards = currentCards();
        if (cards != null) {
            for (RiseModuleCard card : cards) {
                card.key(typedChar, keyCode);
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        if (selectedTab == Tab.SEARCH || selectedTab.moduleTab) {
            targetScroll += wheel > 0 ? -22f : 22f;
            targetScroll = Math.max(0, Math.min(targetScroll, getMaxScroll()));
        }
    }

    private boolean clickTheme(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        ClickGuiModule clickGui = ClickGuiModule.getInstance();
        if (clickGui == null) return false;

        float x = contentX();
        float y = contentY();
        float w = contentWidth();
        int cols = 3;
        float gap = 6f;
        float cardWidth = (w - gap * (cols + 1)) / cols;
        float cardHeight = 48f;

        ClickGuiModule.ThemePreset[] presets = ClickGuiModule.ThemePreset.values();
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == ClickGuiModule.ThemePreset.CUSTOM) continue;
            int column = idx % cols;
            int row = idx / cols;
            float cardX = x + gap + column * (cardWidth + gap);
            float cardY = y + 36 + row * (cardHeight + gap);
            idx++;
            if (over(cardX, cardY, cardWidth, cardHeight, mouseX, mouseY)) {
                Setting themeSet = clickGui.getSettings().get(1);
                if (themeSet instanceof EnumSetting) {
                    ((EnumSetting) themeSet).setIndex(i);
                }
                return true;
            }
        }
        return false;
    }

    private void switchTab(Tab tab) {
        if (selectedTab == tab) return;
        selectedTab = tab;
        transitionAlpha = 1f;
        resetModuleScroll();
    }

    private List<RiseModuleCard> currentCards() {
        if (selectedTab == Tab.SEARCH) return searchText.isEmpty() ? allCards : searchResults;
        if (selectedTab.moduleTab) return moduleCards.get(selectedTab);
        return null;
    }

    private void updateSearchResults() {
        searchResults.clear();
        String query = searchText.trim().toLowerCase();
        if (query.isEmpty()) return;
        for (RiseModuleCard card : allCards) {
            Module module = card.getModule();
            String haystack = (module.getName() + " " + module.getDescription() + " " + getModuleCategoryName(module)).toLowerCase();
            if (haystack.contains(query)) {
                searchResults.add(card);
            }
        }
    }

    private boolean activeEditor() {
        for (RiseModuleCard card : allCards) {
            if (card.isTyping()) return true;
        }
        return false;
    }

    private boolean isGuiKey(int keyCode) {
        ClickGuiModule clickGui = ClickGuiModule.getInstance();
        return clickGui != null && clickGui.getKeyCode() == keyCode;
    }

    private boolean isSearchCharacter(char typedChar) {
        return typedChar >= ' ' && typedChar <= '~';
    }

    private void close() {
        closing = true;
        animationStart = System.currentTimeMillis();
    }

    private void resetModuleScroll() {
        scrollOffset = 0;
        targetScroll = 0;
    }

    private void updateScroll(float deltaTime) {
        targetScroll = Math.max(0, Math.min(targetScroll, getMaxScroll()));
        scrollOffset = animateSmooth(targetScroll, scrollOffset, 14f, deltaTime);
    }

    private float getMaxScroll() {
        List<RiseModuleCard> cards = currentCards();
        if (cards == null || cards.isEmpty()) return 0;
        float total = 2f;
        for (RiseModuleCard card : cards) {
            total += card.getTotalHeight() + 6f;
        }
        return Math.max(0, total - contentHeight());
    }

    private int enabledCount() {
        int enabled = 0;
        for (RiseModuleCard card : allCards) {
            if (card.getModule().isEnabled()) enabled++;
        }
        return enabled;
    }

    private float tabStep() {
        return Math.max(14f, Math.min(22f, (windowHeight - (windowHeight >= 300f ? 110f : 70f)) / Tab.values().length));
    }

    private float tabItemHeight() {
        return Math.max(12f, Math.min(18f, tabStep() - 3f));
    }

    private float contentX() {
        return windowX + sidebarWidth + 10f;
    }

    private float contentY() {
        return windowY + topbarHeight + 10f;
    }

    private float contentWidth() {
        return windowWidth - sidebarWidth - 20f;
    }

    private float contentHeight() {
        return windowHeight - topbarHeight - 20f;
    }

    private boolean overSearch(int mouseX, int mouseY) {
        float searchWidth = Math.min(160f, Math.max(110f, windowWidth * 0.28f));
        return over(windowX + windowWidth - searchWidth - 12f, windowY + 9f, searchWidth, 24f, mouseX, mouseY);
    }

    private boolean over(float x, float y, float w, float h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void clampWindow() {
        ScaledResolution resolution = new ScaledResolution(mc);
        windowX = Math.max(4f, Math.min(windowX, resolution.getScaledWidth() - windowWidth - 4f));
        windowY = Math.max(4f, Math.min(windowY, resolution.getScaledHeight() - windowHeight - 4f));
    }

    private float easeOutExpo(float value) {
        return value >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * value);
    }

    public static String getModuleCategoryName(Module module) {
        Category cat = module.getCategory();
        return cat.name().charAt(0) + cat.name().substring(1).toLowerCase();
    }

    private void rebuildModuleCache() {
        allCards.clear();
        moduleCards.clear();
        for (Tab tab : Tab.values()) {
            if (tab.moduleTab) {
                moduleCards.put(tab, new ArrayList<RiseModuleCard>());
            }
        }

        List<Module> modules = new ArrayList<Module>(moduleManager.getModules());
        Collections.sort(modules, new Comparator<Module>() {
            @Override
            public int compare(Module first, Module second) {
                return first.getName().compareToIgnoreCase(second.getName());
            }
        });

        for (Module module : modules) {
            RiseModuleCard card = new RiseModuleCard(module);
            allCards.add(card);
            Tab tab = mapCategoryToTab(module.getCategory());
            List<RiseModuleCard> cards = moduleCards.get(tab);
            if (cards != null) {
                cards.add(card);
            }
        }
        updateSearchResults();
    }

    private Tab mapCategoryToTab(Category category) {
        switch (category) {
            case COMBAT: return Tab.COMBAT;
            case MOVEMENT: return Tab.MOVEMENT;
            case PLAYER: return Tab.PLAYER;
            case RENDER: return Tab.RENDER;
            case CLIENT: return Tab.CLIENT;
            case MISC:
            default:
                return Tab.MISC;
        }
    }

    // ======================== DRAW UTILS (STENCIL-FREE GL11) ========================

    private static void enableRenderState() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
    }

    private static void disableRenderState() {
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void setColor(int argb) {
        float f = (float) (argb >> 24 & 0xFF) / 255.0f;
        float f2 = (float) (argb >> 16 & 0xFF) / 255.0f;
        float f3 = (float) (argb >> 8 & 0xFF) / 255.0f;
        float f4 = (float) (argb & 0xFF) / 255.0f;
        GlStateManager.color(f2, f3, f4, f);
    }

    private static void drawQuadNoState(double x1, double y1, double x2, double y2) {
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        worldrenderer.pos(x1, y1, 0.0D).endVertex();
        worldrenderer.pos(x2, y1, 0.0D).endVertex();
        worldrenderer.pos(x2, y2, 0.0D).endVertex();
        worldrenderer.pos(x1, y2, 0.0D).endVertex();
        tessellator.draw();
    }

    private static void drawCornerFan(double centerX, double centerY, double radius, double start, double end) {
        int segments = Math.max(8, (int) Math.ceil(radius * 2.0D));
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_TRIANGLE_FAN, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        worldrenderer.pos(centerX, centerY, 0.0D).endVertex();
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(start + (end - start) * i / segments);
            worldrenderer.pos(centerX + Math.cos(angle) * radius, centerY + Math.sin(angle) * radius, 0.0D).endVertex();
        }
        tessellator.draw();
    }

    public static void drawRoundedRect(double x, double y, double width, double height, double radius, int color,
            boolean roundTopLeft, boolean roundTopRight, boolean roundBottomLeft, boolean roundBottomRight) {
        if (width <= 0.0D || height <= 0.0D || (color >>> 24) == 0) return;

        radius = Math.max(0.0D, Math.min(radius, Math.min(width, height) / 2.0D));
        if (radius <= 0.0D || !(roundTopLeft || roundTopRight || roundBottomLeft || roundBottomRight)) {
            drawRect((int) x, (int) y, (int) (x + width), (int) (y + height), color);
            return;
        }

        enableRenderState();
        setColor(color);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);

        drawQuadNoState(x + radius, y, x + width - radius, y + height);
        drawQuadNoState(x, y + radius, x + radius, y + height - radius);
        drawQuadNoState(x + width - radius, y + radius, x + width, y + height - radius);

        if (roundTopLeft) drawCornerFan(x + radius, y + radius, radius, 180.0D, 270.0D);
        else drawQuadNoState(x, y, x + radius, y + radius);

        if (roundTopRight) drawCornerFan(x + width - radius, y + radius, radius, 270.0D, 360.0D);
        else drawQuadNoState(x + width - radius, y, x + width, y + radius);

        if (roundBottomRight) drawCornerFan(x + width - radius, y + height - radius, radius, 0.0D, 90.0D);
        else drawQuadNoState(x + width - radius, y + height - radius, x + width, y + height);

        if (roundBottomLeft) drawCornerFan(x + radius, y + height - radius, radius, 90.0D, 180.0D);
        else drawQuadNoState(x, y + height - radius, x + radius, y + height);

        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        disableRenderState();
    }

    public static void drawRoundedRectOutline(float x, float y, float width, float height, float radius, float lineWidth, int color,
            boolean topLeft, boolean topRight, boolean bottomLeft, boolean bottomRight) {
        radius = Math.min(radius, Math.min(width, height) / 2.0f);
        float f = (float) (color >> 24 & 255) / 255.0F;
        float f1 = (float) (color >> 16 & 255) / 255.0F;
        float f2 = (float) (color >> 8 & 255) / 255.0F;
        float f3 = (float) (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(f1, f2, f3, f);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_LINE_LOOP, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);

        if (topLeft) {
            for (int i = 180; i <= 270; i += 3) {
                double rad = Math.toRadians(i);
                worldrenderer.pos(x + radius + Math.cos(rad) * radius, y + radius + Math.sin(rad) * radius, 0.0D).endVertex();
            }
        } else {
            worldrenderer.pos(x, y, 0.0D).endVertex();
        }

        if (topRight) {
            for (int i = 270; i <= 360; i += 3) {
                double rad = Math.toRadians(i);
                worldrenderer.pos(x + width - radius + Math.cos(rad) * radius, y + radius + Math.sin(rad) * radius, 0.0D).endVertex();
            }
        } else {
            worldrenderer.pos(x + width, y, 0.0D).endVertex();
        }

        if (bottomRight) {
            for (int i = 0; i <= 90; i += 3) {
                double rad = Math.toRadians(i);
                worldrenderer.pos(x + width - radius + Math.cos(rad) * radius, y + height - radius + Math.sin(rad) * radius, 0.0D).endVertex();
            }
        } else {
            worldrenderer.pos(x + width, y + height, 0.0D).endVertex();
        }

        if (bottomLeft) {
            for (int i = 90; i <= 180; i += 3) {
                double rad = Math.toRadians(i);
                worldrenderer.pos(x + radius + Math.cos(rad) * radius, y + height - radius + Math.sin(rad) * radius, 0.0D).endVertex();
            }
        } else {
            worldrenderer.pos(x, y + height, 0.0D).endVertex();
        }

        tessellator.draw();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void drawStringScaled(String text, float x, float y, int color, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1.0f);
        net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, 0, 0, color);
        GlStateManager.popMatrix();
    }

    public static void drawCenteredStringScaled(String text, float centerX, float y, int color, float scale) {
        float width = net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.getStringWidth(text) * scale;
        drawStringScaled(text, centerX - width / 2f, y, color, scale);
    }


    public static float animateSmooth(float target, float current, float speed, float deltaTime) {
        float diff = target - current;
        if (Math.abs(diff) < 0.005f) {
            return target;
        }
        return current + diff * Math.min(1.0f, speed * deltaTime);
    }

    public static int interpolateColor(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int a1 = (c1 >> 24) & 0xFF;

        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF;

        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        int a = (int) (a1 + (a2 - a1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ======================== INNER RISE MODULE CARD ========================

    private final class RiseModuleCard {
        private static final float CARD_DEFAULT_HEIGHT = 38f;

        private final Module module;
        private final List<RiseValueEditor> valueEditors = new ArrayList<RiseValueEditor>();

        private double x, y;
        private double cardWidth = 260;
        private boolean expanded;
        private boolean mouseDown;
        private boolean bindingKey;
        private float hoverAnimation;
        private float enabledAnimation;
        private float expandAnimation = CARD_DEFAULT_HEIGHT;
        private float settingOpacity;

        RiseModuleCard(Module module) {
            this.module = module;
            this.enabledAnimation = module.isEnabled() ? 1f : 0f;
            initValueEditors();
        }

        private void initValueEditors() {
            for (Setting setting : module.getSettings()) {
                if (setting instanceof BooleanSetting) {
                    valueEditors.add(new RiseValueEditor.BooleanEditor((BooleanSetting) setting));
                } else if (setting instanceof EnumSetting) {
                    valueEditors.add(new RiseValueEditor.ModeEditor((EnumSetting) setting));
                } else if (setting instanceof NumberSetting || setting instanceof DecimalSetting || setting instanceof IntRangeSetting) {
                    valueEditors.add(new RiseValueEditor.SliderEditor(setting));
                } else if (setting instanceof ActionSetting) {
                    valueEditors.add(new RiseValueEditor.ActionEditor((ActionSetting) setting));
                }
            }
        }

        Module getModule() { return module; }
        void setX(double x) { this.x = x; }
        void setY(double y) { this.y = y; }
        void setCardWidth(double w) { this.cardWidth = w; }
        double getTotalHeight() { return expandAnimation; }
        boolean isBindingKey() { return bindingKey; }

        boolean isTyping() {
            if (bindingKey) return true;
            for (RiseValueEditor editor : valueEditors) {
                if (editor.isTyping()) return true;
            }
            return false;
        }

        void draw(int mouseX, int mouseY, float partialTicks, float deltaTime,
                double guiX, double guiY, double guiW, double guiH, boolean searchMode) {
            float targetHeight = CARD_DEFAULT_HEIGHT;
            if (expanded) {
                targetHeight = CARD_DEFAULT_HEIGHT + 6;
                for (RiseValueEditor editor : valueEditors) {
                    if (editor.isVisible()) {
                        targetHeight += editor.getHeight();
                    }
                }
            }

            expandAnimation = animateSmooth(targetHeight, expandAnimation, 12f, deltaTime);
            settingOpacity = animateSmooth(expanded ? 255f : 0f, settingOpacity, 10f, deltaTime);
            enabledAnimation = animateSmooth(module.isEnabled() ? 1f : 0f, enabledAnimation, 10f, deltaTime);

            boolean visible = !(y + expandAnimation < guiY || y > guiY + guiH);
            if (!visible) return;

            Color accent = new Color(ClickGuiModule.getModernAccentColor());
            boolean overHeader = mouseX >= x && mouseX <= x + cardWidth && mouseY >= y && mouseY <= y + CARD_DEFAULT_HEIGHT;
            float hoverTarget = overHeader ? (mouseDown ? 1f : 0.65f) : 0f;
            hoverAnimation = animateSmooth(hoverTarget, hoverAnimation, 18f, deltaTime);

            int surface = hoverAnimation > 0.02f ? RiseColors.getSurfaceHigh().getRGB() : RiseColors.getSurface().getRGB();
            drawRoundedRect((float) x, (float) y, (float) cardWidth, expandAnimation, 6, surface, true, true, true, true);
            drawRoundedRectOutline((float) x, (float) y, (float) cardWidth, expandAnimation, 6, 1f,
                    module.isEnabled() ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 82).getRGB() : RiseColors.BORDER.getRGB(),
                    true, true, true, true);

            if (enabledAnimation > 0.01f) {
                drawRoundedRect((float) x, (float) y + 6, 2f, CARD_DEFAULT_HEIGHT - 12, 1f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (255 * enabledAnimation)).getRGB(),
                        true, true, true, true);
            }

            Color nameColor = mix(RiseColors.TEXT, accent, enabledAnimation);
            int nameAlpha = module.isEnabled() ? 255 : 205;
            drawStringScaled(module.getName(), (float) x + 10f, (float) y + 6f, RiseColors.withAlpha(nameColor, nameAlpha).getRGB(), 0.9f);

            if (searchMode) {
                String cat = getModuleCategoryName(module);
                float nameWidth = fontRendererObj.getStringWidth(module.getName()) * 0.9f;
                drawStringScaled(cat.toUpperCase(), (float) x + 16f + nameWidth, (float) y + 8f, RiseColors.withAlpha(accent, 175).getRGB(), 0.6f);
            }

            String desc = module.getDescription();
            if (desc != null && !desc.isEmpty()) {
                drawStringScaled(trim(desc, (float) cardWidth - 85f), (float) x + 10f, (float) y + 22f, RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
            } else {
                drawStringScaled(valueEditors.size() + " settings", (float) x + 10f, (float) y + 22f, RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
            }

            drawRightBadges(accent);

            if (expandAnimation > CARD_DEFAULT_HEIGHT + 1 && settingOpacity > 1) {
                drawRect((int) x + 8, (int) (y + CARD_DEFAULT_HEIGHT), (int) (x + cardWidth - 8), (int) (y + CARD_DEFAULT_HEIGHT + 1), RiseColors.BORDER.getRGB());
                float editorY = (float) y + CARD_DEFAULT_HEIGHT + 4;
                for (RiseValueEditor editor : valueEditors) {
                    if (!editor.isVisible()) continue;
                    if (editorY + editor.getHeight() >= guiY && editorY <= guiY + guiH) {
                        editor.draw((float) x + 8f, editorY, (float) cardWidth - 16f, mouseX, mouseY, partialTicks, deltaTime, (int) Math.min(255, settingOpacity));
                    }
                    editorY += editor.getHeight();
                }
            }
        }

        private void drawRightBadges(Color accent) {
            float right = (float) (x + cardWidth - 10);

            if (bindingKey) {
                String text = "Press key";
                float width = fontRendererObj.getStringWidth(text) * 0.65f;
                drawStringScaled(text, right - width, (float) y + 8,
                        System.currentTimeMillis() % 1000 < 500 ? RiseColors.TEXT.getRGB() : RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
                return;
            }

            String key = keyName();
            if (!key.isEmpty()) {
                float textWidth = fontRendererObj.getStringWidth(key) * 0.65f;
                float badgeWidth = textWidth + 6f;
                right -= badgeWidth;
                drawRoundedRect(right, (float) y + 6, badgeWidth, 12, 3, RiseColors.SURFACE_SOFT.getRGB(), true, true, true, true);
                drawStringScaled(key, right + 3, (float) y + 8, RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
                right -= 5;
            }

            if (!valueEditors.isEmpty()) {
                String text = valueEditors.size() + (expanded ? " CLOSE" : " EDIT");
                float width = fontRendererObj.getStringWidth(text) * 0.65f;
                drawStringScaled(text, right - width, (float) y + 22,
                        expanded ? RiseColors.withAlpha(accent, 230).getRGB() : RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
            }
        }

        boolean click(int mouseX, int mouseY, int mouseButton) {
            float right = (float) (x + cardWidth - 10);
            String key = keyName();
            float badgeWidth = (!key.isEmpty() ? fontRendererObj.getStringWidth(key) * 0.65f + 6f : 40f);
            boolean overKeybindBadge = mouseX >= right - badgeWidth - 10 && mouseX <= right + 5 && mouseY >= y + 4 && mouseY <= y + 20;

            if (bindingKey) {
                if (mouseButton >= 2) {
                    module.setKeyCode(-100 + mouseButton);
                    bindingKey = false;
                    return true;
                } else if (mouseButton == 1) {
                    module.setKeyCode(Keyboard.KEY_NONE);
                    bindingKey = false;
                    return true;
                } else if (mouseButton == 0) {
                    bindingKey = false;
                    return true;
                }
            }

            boolean overHeader = mouseX >= x && mouseX <= x + cardWidth && mouseY >= y && mouseY <= y + CARD_DEFAULT_HEIGHT;
            if (overHeader) {
                mouseDown = true;
                if (overKeybindBadge) {
                    if (mouseButton == 0) {
                        bindingKey = true;
                        return true;
                    } else if (mouseButton == 1) {
                        module.setKeyCode(Keyboard.KEY_NONE);
                        bindingKey = false;
                        return true;
                    }
                }

                if (mouseButton == 0) {
                    module.toggle();
                    return true;
                }
                if (mouseButton == 1 && !valueEditors.isEmpty()) {
                    expanded = !expanded;
                    return true;
                }
                if (mouseButton == 2) {
                    bindingKey = !bindingKey;
                    return true;
                }
            }

            if (expanded && expandAnimation > CARD_DEFAULT_HEIGHT + 1) {
                float editorY = (float) y + CARD_DEFAULT_HEIGHT + 4;
                for (RiseValueEditor editor : valueEditors) {
                    if (!editor.isVisible()) continue;
                    if (editor.click(mouseX, mouseY, mouseButton, (float) x + 8f, editorY, (float) cardWidth - 16f)) {
                        return true;
                    }
                    editorY += editor.getHeight();
                }
            }
            return false;
        }

        void released() {
            mouseDown = false;
            for (RiseValueEditor editor : valueEditors) {
                editor.released();
            }
        }

        void key(char typedChar, int keyCode) {
            if (bindingKey) {
                if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
                    module.setKeyCode(Keyboard.KEY_NONE);
                } else {
                    module.setKeyCode(keyCode);
                }
                bindingKey = false;
                return;
            }

            if (expanded) {
                for (RiseValueEditor editor : valueEditors) {
                    editor.key(typedChar, keyCode);
                }
            }
        }

        private String keyName() {
            return com.lionclient.util.KeyBindUtil.getKeyName(module.getKeyCode());
        }

        private Color mix(Color from, Color to, float amount) {
            amount = Math.max(0f, Math.min(1f, amount));
            int r = (int) (from.getRed() + (to.getRed() - from.getRed()) * amount);
            int g = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * amount);
            int b = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * amount);
            return new Color(r, g, b);
        }

        private String trim(String text, float width) {
            if (fontRendererObj.getStringWidth(text) * 0.65f <= width) return text;
            String ellipsis = "...";
            String result = text;
            while (result.length() > 0 && fontRendererObj.getStringWidth(result + ellipsis) * 0.65f > width) {
                result = result.substring(0, result.length() - 1);
            }
            return result + ellipsis;
        }
    }

    // ======================== INNER RISE VALUE EDITORS ========================

    private static abstract class RiseValueEditor {
        protected final Setting setting;

        protected RiseValueEditor(Setting setting) {
            this.setting = setting;
        }

        public boolean isVisible() { return setting.isVisible(); }
        public boolean isTyping() { return false; }
        public abstract float getHeight();
        public abstract void draw(float x, float y, float w, int mouseX, int mouseY, float partialTicks, float deltaTime, int opacity);
        public abstract boolean click(int mouseX, int mouseY, int mouseButton, float x, float y, float w);
        public void released() {}
        public void key(char typedChar, int keyCode) {}

        protected Color accent() { return new Color(ClickGuiModule.getModernAccentColor()); }
        protected int alpha(Color color, int opacity) { return RiseColors.withAlpha(color, opacity).getRGB(); }
        protected boolean over(float x, float y, float w, float h, int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }

        protected float centeredY(float y, float height, float contentHeight) {
            return y + (height - contentHeight) / 2f + 0.5f;
        }

        protected static net.minecraft.client.gui.FontRenderer MinecraftFontRenderer() {
            return net.minecraft.client.Minecraft.getMinecraft().fontRendererObj;
        }



        // ======================== BOOLEAN SETTING EDITOR ========================
        static final class BooleanEditor extends RiseValueEditor {
            private final BooleanSetting prop;
            private float valueAnimation;
            private float hoverAnimation;

            BooleanEditor(BooleanSetting prop) {
                super(prop);
                this.prop = prop;
                this.valueAnimation = prop.isEnabled() ? 1f : 0f;
            }

            @Override
            public float getHeight() { return 18f; }

            @Override
            public void draw(float x, float y, float w, int mouseX, int mouseY, float partialTicks, float deltaTime, int opacity) {
                valueAnimation = animateSmooth(prop.isEnabled() ? 1f : 0f, valueAnimation, 12f, deltaTime);
                hoverAnimation = animateSmooth(over(x, y, w, getHeight(), mouseX, mouseY) ? 1f : 0f, hoverAnimation, 10f, deltaTime);

                if (hoverAnimation > 0.01f) {
                    drawRoundedRect(x - 3, y, w + 6, getHeight() - 1, 3,
                            new Color(255, 255, 255, (int) (10 * hoverAnimation * opacity / 255f)).getRGB(), true, true, true, true);
                }

                drawStringScaled(prop.getName(), x, y + 4, RiseColors.withAlphaRGB(RiseColors.TEXT_SECONDARY, opacity), 0.8f);

                Color accent = accent();
                float switchWidth = 24f;
                float switchHeight = 12f;
                float switchX = x + w - switchWidth;
                float switchY = y + 3f;
                int off = new Color(52, 58, 69, opacity).getRGB();
                int on = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), opacity).getRGB();
                drawRoundedRect(switchX, switchY, switchWidth, switchHeight, 6, interpolateColor(off, on, valueAnimation), true, true, true, true);
                float thumbX = switchX + 6f + valueAnimation * 12f;
                drawRoundedRect(thumbX - 4f, switchY + 2f, 8f, 8f, 4f, RiseColors.withAlphaRGB(Color.WHITE, opacity), true, true, true, true);
            }

            @Override
            public boolean click(int mouseX, int mouseY, int mouseButton, float x, float y, float w) {
                if (mouseButton == 0 && over(x, y, w, getHeight(), mouseX, mouseY)) {
                    prop.toggle();
                    return true;
                }
                return false;
            }
        }

        // ======================== ENUM SETTING EDITOR ========================
        static final class ModeEditor extends RiseValueEditor {
            private final EnumSetting<?> prop;
            private float hoverAnimation;

            ModeEditor(EnumSetting<?> prop) {
                super(prop);
                this.prop = prop;
            }

            @Override
            public float getHeight() { return 18f; }

            @Override
            public void draw(float x, float y, float w, int mouseX, int mouseY, float partialTicks, float deltaTime, int opacity) {
                hoverAnimation = animateSmooth(over(x, y, w, getHeight(), mouseX, mouseY) ? 1f : 0f, hoverAnimation, 10f, deltaTime);

                if (hoverAnimation > 0.01f) {
                    drawRoundedRect(x - 3, y, w + 6, getHeight() - 1, 3,
                            new Color(255, 255, 255, (int) (7 * hoverAnimation * opacity / 255f)).getRGB(), true, true, true, true);
                }

                drawStringScaled(prop.getName(), x, y + 4, RiseColors.withAlphaRGB(RiseColors.TEXT_SECONDARY, opacity), 0.8f);

                String mode = prop.getValueText();
                float modeWidth = MinecraftFontRenderer().getStringWidth(mode) * 0.8f;
                float pillWidth = modeWidth + 10f;
                float pillX = x + w - pillWidth;
                drawRoundedRect(pillX, y + 2, pillWidth, 14, 4,
                        new Color(accent().getRed(), accent().getGreen(), accent().getBlue(), (int) (30f * opacity / 255f)).getRGB(),
                        true, true, true, true);
                drawStringScaled(mode, pillX + 5, y + 4, alpha(accent(), opacity), 0.8f);
            }

            @Override
            public boolean click(int mouseX, int mouseY, int mouseButton, float x, float y, float w) {
                if (over(x, y, w, getHeight(), mouseX, mouseY)) {
                    if (mouseButton == 0) {
                        prop.cycleForward();
                        return true;
                    }
                    if (mouseButton == 1) {
                        prop.cycleBackward();
                        return true;
                    }
                }
                return false;
            }
        }

        // ======================== SLIDER (NUMBER/DECIMAL/RANGE) EDITOR ========================
        static final class SliderEditor extends RiseValueEditor {
            private boolean dragging;
            private boolean draggingHigh;
            private float hoverAnimation;
            private float percentAnimation;
            private float percentHighAnimation;
            private boolean isTyping;
            private String typedString = "";

            SliderEditor(Setting prop) {
                super(prop);
                this.percentAnimation = ratio();
                if (prop instanceof IntRangeSetting) {
                    this.percentHighAnimation = ratioHigh();
                }
            }

            @Override
            public boolean isTyping() {
                return isTyping;
            }

            @Override
            public float getHeight() { return 20f; }

            private double getMin() {
                if (setting instanceof NumberSetting) return ((NumberSetting) setting).getMin();
                if (setting instanceof DecimalSetting) return ((DecimalSetting) setting).getMin();
                return ((IntRangeSetting) setting).getMin();
            }

            private double getMax() {
                if (setting instanceof NumberSetting) return ((NumberSetting) setting).getMax();
                if (setting instanceof DecimalSetting) return ((DecimalSetting) setting).getMax();
                return ((IntRangeSetting) setting).getMax();
            }

            private float ratio() {
                double min = getMin();
                double max = getMax();
                double val = 0;
                if (setting instanceof NumberSetting) val = ((NumberSetting) setting).getValue();
                else if (setting instanceof DecimalSetting) val = ((DecimalSetting) setting).getValue();
                else val = ((IntRangeSetting) setting).getLow();
                return (float) ((val - min) / (max - min));
            }

            private float ratioHigh() {
                if (!(setting instanceof IntRangeSetting)) return 0;
                double min = getMin();
                double max = getMax();
                double val = ((IntRangeSetting) setting).getHigh();
                return (float) ((val - min) / (max - min));
            }

            private void setFromMouse(int mouseX, float x, float w) {
                float trackX = sliderTrackX(x, w);
                float valWidth = 35f;
                float trackW = sliderTrackWidth(x, w, trackX, valWidth);
                float pct = Math.max(0f, Math.min(1f, (mouseX - trackX) / trackW));
                double min = getMin();
                double max = getMax();

                if (setting instanceof NumberSetting) {
                    NumberSetting n = (NumberSetting) setting;
                    n.setValue((int) Math.round(min + pct * (max - min)));
                } else if (setting instanceof DecimalSetting) {
                    DecimalSetting d = (DecimalSetting) setting;
                    d.setValue(min + pct * (max - min));
                } else if (setting instanceof IntRangeSetting) {
                    IntRangeSetting r = (IntRangeSetting) setting;
                    int val = (int) Math.round(min + pct * (max - min));
                    if (draggingHigh) {
                        r.setHigh(val, true);
                    } else {
                        r.setLow(val, true);
                    }
                }
            }

            private String valueText() {
                return setting.getValueText();
            }

            @Override
            public void draw(float x, float y, float w, int mouseX, int mouseY, float partialTicks, float deltaTime, int opacity) {
                hoverAnimation = animateSmooth(over(x, y, w, getHeight(), mouseX, mouseY) || dragging ? 1f : 0f, hoverAnimation, 10f, deltaTime);

                if (hoverAnimation > 0.01f) {
                    drawRoundedRect(x - 3, y, w + 6, getHeight(), 3,
                            new Color(255, 255, 255, (int) (6 * hoverAnimation * opacity / 255f)).getRGB(), true, true, true, true);
                }

                if (dragging && Mouse.isButtonDown(0)) {
                    setFromMouse(mouseX, x, w);
                } else {
                    dragging = false;
                }

                String value = isTyping ? typedString + (System.currentTimeMillis() % 1000 < 500 ? "_" : "") : valueText();
                float valueWidth = 35f;
                float trackX = sliderTrackX(x, w);
                float trackW = sliderTrackWidth(x, w, trackX, valueWidth);
                float trackY = centeredY(y, getHeight(), 3f);
                float textY = y + 4f;

                percentAnimation = animateSmooth(ratio(), percentAnimation, 14f, deltaTime);

                drawStringScaled(setting.getName(), x, textY, RiseColors.withAlphaRGB(RiseColors.TEXT_SECONDARY, opacity), 0.8f);

                float actualValWidth = MinecraftFontRenderer().getStringWidth(value) * 0.8f;
                drawStringScaled(value, x + w - actualValWidth - 2f, textY, isTyping ? RiseColors.withAlphaRGB(RiseColors.TEXT, opacity) : alpha(accent(), opacity), 0.8f);

                // Slider track
                drawRoundedRect(trackX, trackY, trackW, 3, 1.5, new Color(49, 55, 66, opacity).getRGB(), true, true, true, true);

                if (setting instanceof IntRangeSetting) {
                    percentHighAnimation = animateSmooth(ratioHigh(), percentHighAnimation, 14f, deltaTime);
                    float fillX = trackX + trackW * percentAnimation;
                    float fillW = trackW * (percentHighAnimation - percentAnimation);
                    drawRoundedRect(fillX, trackY, fillW, 3, 1.5, alpha(accent(), opacity), true, true, true, true);

                    float thumbXLow = trackX + trackW * percentAnimation;
                    float thumbXHigh = trackX + trackW * percentHighAnimation;
                    drawRoundedRect(thumbXLow - 3f, trackY - 1.5f, 6f, 6f, 3f, RiseColors.withAlphaRGB(Color.WHITE, opacity), true, true, true, true);
                    drawRoundedRect(thumbXHigh - 3f, trackY - 1.5f, 6f, 6f, 3f, RiseColors.withAlphaRGB(Color.WHITE, opacity), true, true, true, true);
                } else {
                    drawRoundedRect(trackX, trackY, Math.max(3, trackW * percentAnimation), 3, 1.5, alpha(accent(), opacity), true, true, true, true);
                    float thumbX = trackX + trackW * percentAnimation;
                    drawRoundedRect(thumbX - 3f, trackY - 1.5f, 6f, 6f, 3f, RiseColors.withAlphaRGB(Color.WHITE, opacity), true, true, true, true);
                }
            }

            @Override
            public boolean click(int mouseX, int mouseY, int mouseButton, float x, float y, float w) {
                if (mouseButton == 0 && over(x, y, w, getHeight(), mouseX, mouseY)) {
                    float valueWidth = 35f;
                    if (mouseX >= x + w - valueWidth - 4f) {
                        isTyping = true;
                        typedString = "";
                        return true;
                    } else if (isTyping) {
                        isTyping = false;
                    }

                    float trackX = sliderTrackX(x, w);
                    float trackW = sliderTrackWidth(x, w, trackX, valueWidth);
                    float pct = (mouseX - trackX) / trackW;

                    if (setting instanceof IntRangeSetting) {
                        float rLow = ratio();
                        float rHigh = ratioHigh();
                        draggingHigh = Math.abs(pct - rHigh) < Math.abs(pct - rLow);
                    }
                    dragging = true;
                    setFromMouse(mouseX, x, w);
                    return true;
                }
                
                if (isTyping) {
                    isTyping = false;
                }
                
                return false;
            }

            @Override
            public void key(char typedChar, int keyCode) {
                if (!isTyping) return;
                
                if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                    isTyping = false;
                    if (!typedString.isEmpty()) {
                        applyTypedValue();
                    }
                    return;
                }
                
                if (keyCode == Keyboard.KEY_BACK) {
                    if (typedString.length() > 0) {
                        typedString = typedString.substring(0, typedString.length() - 1);
                    }
                    return;
                }
                
                if ((typedChar >= '0' && typedChar <= '9') || typedChar == '.' || typedChar == '-') {
                    typedString += typedChar;
                }
            }
            
            private void applyTypedValue() {
                try {
                    double val = Double.parseDouble(typedString);
                    if (setting instanceof NumberSetting) {
                        ((NumberSetting) setting).setValue((int) val);
                    } else if (setting instanceof DecimalSetting) {
                        ((DecimalSetting) setting).setValue(val);
                    } else if (setting instanceof IntRangeSetting) {
                        // Just set low for typing for now
                        ((IntRangeSetting) setting).setLow((int) val, true);
                    }
                } catch (NumberFormatException ignored) {}
            }

            @Override
            public void released() { dragging = false; }

            private float sliderTrackX(float x, float w) {
                float nameWidth = MinecraftFontRenderer().getStringWidth(setting.getName()) * 0.8f;
                return x + nameWidth + 14f;
            }

            private float sliderTrackWidth(float x, float w, float sliderTrackX, float valueWidth) {
                return (x + w - valueWidth - 14f) - sliderTrackX;
            }
        }

        // ======================== ACTION SETTING EDITOR ========================
        static final class ActionEditor extends RiseValueEditor {
            private final ActionSetting prop;
            private float hoverAnimation;

            ActionEditor(ActionSetting prop) {
                super(prop);
                this.prop = prop;
            }

            @Override
            public float getHeight() { return 18f; }

            @Override
            public void draw(float x, float y, float w, int mouseX, int mouseY, float partialTicks, float deltaTime, int opacity) {
                hoverAnimation = animateSmooth(over(x, y, w, getHeight(), mouseX, mouseY) ? 1f : 0f, hoverAnimation, 10f, deltaTime);

                if (hoverAnimation > 0.01f) {
                    drawRoundedRect(x - 3, y, w + 6, getHeight() - 1, 3,
                            new Color(255, 255, 255, (int) (8 * hoverAnimation * opacity / 255f)).getRGB(), true, true, true, true);
                }

                drawStringScaled(prop.getName(), x, y + 4, RiseColors.withAlphaRGB(RiseColors.TEXT_SECONDARY, opacity), 0.8f);

                String actionText = prop.getValueText();
                float actW = MinecraftFontRenderer().getStringWidth(actionText) * 0.75f;
                float actX = x + w - actW - 8f;
                drawRoundedRect(actX, y + 2, actW + 8f, 13, 3, new Color(42, 48, 59, opacity).getRGB(), true, true, true, true);
                drawStringScaled(actionText, actX + 4f, y + 4, RiseColors.withAlphaRGB(Color.WHITE, opacity), 0.75f);
            }

            @Override
            public boolean click(int mouseX, int mouseY, int mouseButton, float x, float y, float w) {
                if (mouseButton == 0 && over(x, y, w, getHeight(), mouseX, mouseY)) {
                    prop.run();
                    return true;
                }
                return false;
            }
        }
    }

    // ======================== CONFIG PRESETS MANAGER ========================

    public void refreshConfigs() {
        userConfigs.clear();
        com.lionclient.config.ConfigManager cm = LionClient.getInstance().getConfigManager();
        if (cm != null) {
            List<String> list = cm.listConfigs();
            for (String name : list) {
                userConfigs.add(new ConfigCard(name));
            }
        }
        configsNeedRefresh = false;
    }

    private void drawConfigScreen(float x, float y, float w, float h, int mouseX, int mouseY, float deltaTime) {
        if (configsNeedRefresh) refreshConfigs();

        float cardW = (w - 28f) / 3f;
        float cardH = 46f;
        float gap = 7f;
        float rowH = 53f;
        float curY = y + 3f - configScrollOffset;

        drawStringScaled("Your Config Profiles", x + 7f, curY, RiseColors.TEXT.getRGB(), 1.0f);
        String countStr = String.valueOf(userConfigs.size());
        drawStringScaled(countStr, x + 130f, curY + 2f, ClickGuiModule.getModernAccentColor(), 0.7f);

        curY += 24f;
        for (int i = 0; i < userConfigs.size(); i++) {
            int col = i % 3;
            int row = i / 3;
            float cardX = x + 7f + col * (cardW + gap);
            float cardY = curY + row * rowH;
            if (cardY + cardH >= y && cardY <= y + h) {
                userConfigs.get(i).draw(cardX, cardY, cardW, cardH, mouseX, mouseY, deltaTime);
            }
        }

        int rows = (int) Math.ceil(userConfigs.size() / 3.0);
        curY += rows * rowH + 8f;

        drawSaveConfigButton(x + 7f, curY, w - 14f, 32f, mouseX, mouseY);
        curY += 40f;

        if (curY > y && curY < y + h) {
            drawStringScaled("Left click to load profile. Right click to delete profile.", x + 7f, curY, RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
        }
    }

    private void drawSaveConfigButton(float x, float y, float w, float h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = hovered ? RiseColors.getSurfaceHigh().getRGB() : RiseColors.getSurface().getRGB();
        drawRoundedRect(x, y, w, h, 7, bg, true, true, true, true);

        int accent = ClickGuiModule.getModernAccentColor();
        int border = hovered ? RiseColors.withAlpha(new Color(accent), 80).getRGB() : RiseColors.BORDER.getRGB();
        drawRoundedRectOutline(x, y, w, h, 7, 1f, border, true, true, true, true);

        if (creatingNewConfig) {
            String cursor = System.currentTimeMillis() % 1000 < 500 ? "|" : "";
            drawStringScaled("Name: " + newConfigName + cursor, x + 11f, y + 9f, RiseColors.TEXT.getRGB(), 0.85f);
        } else {
            drawStringScaled("+  Save current profile", x + 11f, y + 9f, hovered ? RiseColors.TEXT.getRGB() : RiseColors.TEXT_SECONDARY.getRGB(), 0.85f);
        }
    }

    private boolean clickConfigScreen(float x, float y, float w, float h, int mouseX, int mouseY, int button) {
        if (configsNeedRefresh) refreshConfigs();

        float cardW = (w - 28f) / 3f;
        float cardH = 46f;
        float gap = 7f;
        float rowH = 53f;
        float curY = y + 3f - configScrollOffset + 24f;

        for (int i = 0; i < userConfigs.size(); i++) {
            int col = i % 3;
            int row = i / 3;
            float cardX = x + 7f + col * (cardW + gap);
            float cardY = curY + row * rowH;
            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
                ConfigCard card = userConfigs.get(i);
                com.lionclient.config.ConfigManager cm = LionClient.getInstance().getConfigManager();
                if (button == 0) {
                    cm.load(card.name);
                    card.flashAnimation = 1f;
                    return true;
                }
                if (button == 1) {
                    cm.deleteConfig(card.name);
                    configsNeedRefresh = true;
                    return true;
                }
            }
        }

        int rows = (int) Math.ceil(userConfigs.size() / 3.0);
        curY += rows * rowH + 8f;
        float buttonX = x + 7f;
        float buttonW = w - 14f;
        if (button == 0 && mouseX >= buttonX && mouseX <= buttonX + buttonW && mouseY >= curY && mouseY <= curY + 32f) {
            if (creatingNewConfig && !newConfigName.trim().isEmpty()) {
                saveNewConfig();
            } else {
                creatingNewConfig = true;
                newConfigName = "";
            }
            return true;
        }
        return false;
    }

    private boolean keyConfigScreen(char typedChar, int keyCode) {
        if (!creatingNewConfig) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            creatingNewConfig = false;
            newConfigName = "";
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            saveNewConfig();
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!newConfigName.isEmpty()) {
                newConfigName = newConfigName.substring(0, newConfigName.length() - 1);
            }
            return true;
        }
        if (typedChar >= ' ' && typedChar <= '~' && newConfigName.length() < 24) {
            if ("<>:\"/\\|?*".indexOf(typedChar) < 0) {
                newConfigName += typedChar;
            }
            return true;
        }
        return false;
    }

    private void saveNewConfig() {
        String name = newConfigName.trim();
        if (!name.isEmpty()) {
            com.lionclient.config.ConfigManager cm = LionClient.getInstance().getConfigManager();
            if (cm != null) {
                cm.saveAs(name);
                com.lionclient.util.ChatUtil.sendFormatted("&a[Fury] Saved config profile (&o" + name + ".json&r&a)");
            }
            creatingNewConfig = false;
            newConfigName = "";
            configsNeedRefresh = true;
        }
    }

    private static final class ConfigCard {
        private final String name;
        private float hoverAnimation;
        private float flashAnimation;

        private ConfigCard(String name) {
            this.name = name;
        }

        private void draw(float x, float y, float w, float h, int mouseX, int mouseY, float deltaTime) {
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            hoverAnimation = animateSmooth(hovered ? 1f : 0f, hoverAnimation, 10f, deltaTime);
            flashAnimation = animateSmooth(0f, flashAnimation, 5f, deltaTime);

            int bg = hoverAnimation > 0.05f ? RiseColors.getSurfaceHigh().getRGB() : RiseColors.getSurface().getRGB();
            drawRoundedRect(x, y, w, h, 7, bg, true, true, true, true);

            int accent = ClickGuiModule.getModernAccentColor();
            int border = hoverAnimation > 0.05f ? RiseColors.withAlpha(new Color(accent), 75).getRGB() : RiseColors.BORDER.getRGB();
            drawRoundedRectOutline(x, y, w, h, 7, 1f, border, true, true, true, true);

            if (flashAnimation > 0.01f) {
                Color c = new Color(accent);
                int flash = new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (flashAnimation * 60)).getRGB();
                drawRoundedRect(x, y, w, h, 7, flash, true, true, true, true);
            }

            com.lionclient.config.ConfigManager cm = LionClient.getInstance().getConfigManager();
            boolean isActive = cm != null && name.equalsIgnoreCase(cm.getCurrentConfigName());

            drawStringScaled(name, x + 8f, y + 8f, RiseColors.TEXT.getRGB(), 0.9f);
            drawStringScaled(isActive ? "Active Profile" : "Click: load / Right-click: delete", x + 8f, y + 24f,
                    isActive ? (0xFF000000 | accent) : RiseColors.TEXT_TRINARY.getRGB(), 0.65f);
        }
    }
}
