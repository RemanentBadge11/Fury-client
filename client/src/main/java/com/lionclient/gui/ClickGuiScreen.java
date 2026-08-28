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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public final class ClickGuiScreen extends GuiScreen {
    private final List<CategoryPanel> panels = new ArrayList<CategoryPanel>();

    public ClickGuiScreen(ModuleManager moduleManager) {
        int x = 16;
        int y = 24;
        for (Category category : Category.values()) {
            panels.add(new CategoryPanel(category, x, y, moduleManager.getModules(category)));
            x += 134;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Gui.drawRect(0, 0, this.width, this.height, 0xAA05070B);
        int accent = getAccentColor();
        drawCenteredString(this.fontRendererObj, "Fury Client", this.width / 2, 8, accent);
        drawCenteredString(this.fontRendererObj, "Legacy module board", this.width / 2, 18, 0xFFB8BECF);

        String status = "Style: " + ClickGuiModule.getGuiStyle() + " / Theme: " + ClickGuiModule.getThemeName();
        int statusWidth = this.fontRendererObj.getStringWidth(status);
        this.fontRendererObj.drawString(status, this.width - statusWidth - 8, 8, 0xFF9CA7B4);

        for (CategoryPanel panel : panels) {
            panel.draw(mouseX, mouseY, fontRendererObj, this.width);
        }

        for (CategoryPanel panel : panels) {
            panel.drawDescriptionOverlay(fontRendererObj, this.width);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (CategoryPanel panel : panels) {
            panel.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        for (CategoryPanel panel : panels) {
            if (panel.handleKeyTyped(keyCode)) {
                return;
            }
        }

        if (keyCode == Keyboard.KEY_ESCAPE || handleCloseKeybind(keyCode)) {
            LionClient client = LionClient.getInstance();
            if (client != null) {
                client.toggleClickGui();
            } else {
                mc.displayGuiScreen(null);
            }
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        for (CategoryPanel panel : panels) {
            panel.mouseReleased();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
        int mouseX = org.lwjgl.input.Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
        int mouseY = sr.getScaledHeight() - org.lwjgl.input.Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;

        for (CategoryPanel panel : panels) {
            if (panel.isMouseOverPanel(mouseX, mouseY)) {
                panel.scroll(wheel > 0 ? -16 : 16);
                return;
            }
        }

        int amount = wheel > 0 ? 12 : -12;
        for (CategoryPanel panel : panels) {
            panel.offset(amount);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean handleCloseKeybind(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return false;
        }

        ClickGuiModule clickGuiModule = ClickGuiModule.getInstance();
        if (clickGuiModule == null || keyCode != clickGuiModule.getKeyCode()) {
            return false;
        }

        LionClient client = LionClient.getInstance();
        if (client == null) {
            return false;
        }

        client.toggleClickGui();
        return true;
    }

    private int getAccentColor() {
        return ClickGuiModule.getGuiStyle() == ClickGuiModule.GuiStyle.MODERN
            ? ClickGuiModule.getThemePalette().getAccentColor()
            : ClickGuiModule.getAccentColor();
    }

    private static final class CategoryPanel {
        private static final int WIDTH = 128;
        private static final int HEADER_HEIGHT = 16;
        private static final int ROW_HEIGHT = 14;
        private static final long DESCRIPTION_HOVER_DELAY_MS = 2000L;
        private static final String KEYBIND_LABEL = "Keybind";

        private final Category category;
        private final List<Module> modules;
        private Module expandedModule;
        private Module bindingModule;
        private Module hoveredModule;
        private long hoveredSince;
        private int hoveredRowY;
        private int x;
        private int y;
        private boolean dragging;
        private boolean collapsed = false;
        private int dragOffsetX;
        private int dragOffsetY;

        private int scrollOffset = 0;
        private Setting draggingSliderSetting = null;
        private Module draggingSliderModule = null;

        private CategoryPanel(Category category, int x, int y, List<Module> modules) {
            this.category = category;
            this.x = x;
            this.y = y;
            this.modules = modules;
        }

        public boolean isMouseOverPanel(int mouseX, int mouseY) {
            int visibleHeight = collapsed ? HEADER_HEIGHT : Math.min(getContentHeight(), getScreenMaxHeight());
            return mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + visibleHeight;
        }

        public void scroll(int amount) {
            if (collapsed) return;
            int maxScroll = Math.max(0, getContentHeight() - getScreenMaxHeight());
            scrollOffset = Math.max(0, Math.min(scrollOffset + amount, maxScroll));
        }

        private int getScreenMaxHeight() {
            net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());
            return Math.max(120, Math.min(280, sr.getScaledHeight() - y - 16));
        }

        private void draw(int mouseX, int mouseY, net.minecraft.client.gui.FontRenderer fontRenderer, int screenWidth) {
            int accent = ClickGuiModule.getAccentColor();
            if (dragging) {
                x = mouseX - dragOffsetX;
                y = mouseY - dragOffsetY;
            }

            if (draggingSliderSetting != null && draggingSliderModule != null) {
                if (org.lwjgl.input.Mouse.isButtonDown(0)) {
                    double pct = Math.max(0.0, Math.min(1.0, (double) (mouseX - (x + 6)) / (double) (WIDTH - 12)));
                    updateSettingFromPct(draggingSliderSetting, draggingSliderModule, pct);
                } else {
                    draggingSliderSetting = null;
                    draggingSliderModule = null;
                }
            }

            int maxPanelHeight = getScreenMaxHeight();
            int contentHeight = getContentHeight();
            int visibleHeight = collapsed ? HEADER_HEIGHT : Math.min(contentHeight, maxPanelHeight);
            int maxScroll = Math.max(0, contentHeight - visibleHeight);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            // Single unified dark rounded container
            ModernClickGuiScreen.drawRoundedRect(x, y, WIDTH, visibleHeight, 8.0, 0xEE18151E, true, true, true, true);
            ModernClickGuiScreen.drawRoundedRectOutline(x, y, WIDTH, visibleHeight, 8.0f, 1.0f, 0x33FFFFFF, true, true, true, true);

            // Centered category title
            String title = getCategoryDisplayName(category);
            int titleWidth = fontRenderer.getStringWidth(title);
            fontRenderer.drawStringWithShadow(title, x + (WIDTH - titleWidth) / 2, y + 4, 0xFFFFFFFF);

            if (collapsed) {
                updateHoveredModule(null);
                return;
            }

            // Draw right vertical scrollbar if panel content overflows max height
            if (maxScroll > 0) {
                int scrollbarTrackY = y + HEADER_HEIGHT + 1;
                int scrollbarTrackH = visibleHeight - HEADER_HEIGHT - 2;
                int thumbH = Math.max(12, (int) ((double) visibleHeight / contentHeight * scrollbarTrackH));
                int thumbY = scrollbarTrackY + (int) ((double) scrollOffset / maxScroll * (scrollbarTrackH - thumbH));
                Gui.drawRect(x + WIDTH - 4, scrollbarTrackY, x + WIDTH - 2, scrollbarTrackY + scrollbarTrackH, 0x33FFFFFF);
                Gui.drawRect(x + WIDTH - 4, thumbY, x + WIDTH - 2, thumbY + thumbH, 0xFF000000 | accent);
            }

            net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());
            int sf = sr.getScaleFactor();
            boolean useScissor = maxScroll > 0;
            if (useScissor) {
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
                org.lwjgl.opengl.GL11.glScissor(x * sf, (sr.getScaledHeight() - (y + visibleHeight)) * sf, WIDTH * sf, (visibleHeight - HEADER_HEIGHT) * sf);
            }

            int rowY = y + HEADER_HEIGHT - scrollOffset;
            Module currentlyHoveredModule = null;
            int currentHoveredRowY = 0;
            for (Module module : modules) {
                if (!module.isVisible()) {
                    continue;
                }
                boolean hovered = isHovered(mouseX, mouseY, x, rowY, WIDTH, ROW_HEIGHT);
                if (hovered) {
                    Gui.drawRect(x + 2, rowY, x + WIDTH - 2, rowY + ROW_HEIGHT, 0x22FFFFFF);
                }

                if (module.isEnabled()) {
                    // Left 3px vertical accent bar on panel border
                    Gui.drawRect(x, rowY, x + 3, rowY + ROW_HEIGHT, 0xFF000000 | accent);
                    // Enabled text in Pink / Magenta accent
                    fontRenderer.drawString(module.getName(), x + 8, rowY + 3, 0xFF000000 | accent);
                } else {
                    // Disabled text in crisp white
                    fontRenderer.drawString(module.getName(), x + 8, rowY + 3, 0xFFFFFFFF);
                }

                // Vertical three dots icon ⋮ on far right
                fontRenderer.drawString("\u22EE", x + WIDTH - 10, rowY + 3, module.isEnabled() ? (0xFF000000 | accent) : 0xFF777777);

                if (hovered) {
                    currentlyHoveredModule = module;
                    currentHoveredRowY = rowY;
                }
                rowY += ROW_HEIGHT;

                if (expandedModule == module) {
                    for (Setting setting : module.getSettings()) {
                        if (!setting.isVisible()) {
                            continue;
                        }
                        Gui.drawRect(x + 4, rowY, x + WIDTH - 4, rowY + ROW_HEIGHT, 0x60252033);
                        String valText = setting.getValueText();
                        int valWidth = fontRenderer.getStringWidth(valText);
                        int maxNameWidth = WIDTH - 16 - valWidth;
                        String displayName = trimToWidth(fontRenderer, setting.getName(), maxNameWidth);
                        fontRenderer.drawString(displayName, x + 6, rowY + 2, 0xFFE8EAF1);
                        fontRenderer.drawString(valText, x + WIDTH - 6 - valWidth, rowY + 2, 0xFF000000 | accent);

                        // Visual slider track for numerical settings
                        if (isSliderSetting(setting)) {
                            double pct = getSettingPct(setting);
                            Gui.drawRect(x + 6, rowY + ROW_HEIGHT - 3, x + WIDTH - 6, rowY + ROW_HEIGHT - 1, 0x40FFFFFF);
                            int fillW = (int) Math.round(pct * (WIDTH - 12));
                            Gui.drawRect(x + 6, rowY + ROW_HEIGHT - 3, x + 6 + fillW, rowY + ROW_HEIGHT - 1, 0xFF000000 | accent);
                            Gui.drawRect(x + 5 + fillW, rowY + ROW_HEIGHT - 4, x + 7 + fillW, rowY + ROW_HEIGHT, 0xFFFFFFFF);
                        }
                        rowY += ROW_HEIGHT;
                    }

                    if (module.showsKeybindSetting()) {
                        Gui.drawRect(x + 4, rowY, x + WIDTH - 4, rowY + ROW_HEIGHT, 0x60252033);
                        String keybindVal = bindingModule == module ? "Press key..." : getKeybindText(module);
                        fontRenderer.drawString("Key: " + keybindVal, x + WIDTH - 6 - fontRenderer.getStringWidth("Key: " + keybindVal), rowY + 3, 0xFF000000 | accent);
                        rowY += ROW_HEIGHT;
                    }
                }
            }

            if (useScissor) {
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            }

            updateHoveredModule(currentlyHoveredModule);
            hoveredRowY = currentHoveredRowY;
        }

        private static String trimToWidth(net.minecraft.client.gui.FontRenderer fontRenderer, String text, int maxWidth) {
            if (maxWidth <= 8) return "";
            if (fontRenderer.getStringWidth(text) <= maxWidth) return text;
            String ellipsis = "..";
            int ellipsisWidth = fontRenderer.getStringWidth(ellipsis);
            while (!text.isEmpty() && (fontRenderer.getStringWidth(text) + ellipsisWidth) > maxWidth) {
                text = text.substring(0, text.length() - 1);
            }
            return text.isEmpty() ? "" : text + ellipsis;
        }

        private static boolean isSliderSetting(Setting setting) {
            return setting instanceof NumberSetting || setting instanceof DecimalSetting || setting instanceof IntRangeSetting;
        }

        private static double getSettingPct(Setting setting) {
            if (setting instanceof NumberSetting) {
                NumberSetting n = (NumberSetting) setting;
                double min = n.getMin();
                double max = n.getMax();
                return max > min ? (n.getValue() - min) / (max - min) : 0.0;
            }
            if (setting instanceof DecimalSetting) {
                DecimalSetting d = (DecimalSetting) setting;
                double min = d.getMin();
                double max = d.getMax();
                return max > min ? (d.getValue() - min) / (max - min) : 0.0;
            }
            if (setting instanceof IntRangeSetting) {
                IntRangeSetting r = (IntRangeSetting) setting;
                double min = r.getMin();
                double max = r.getMax();
                return max > min ? (r.getHigh() - min) / (max - min) : 0.0;
            }
            return 0.0;
        }

        private void updateSettingFromPct(Setting setting, Module module, double pct) {
            pct = Math.max(0.0, Math.min(1.0, pct));
            if (setting instanceof NumberSetting) {
                NumberSetting n = (NumberSetting) setting;
                double val = n.getMin() + pct * (n.getMax() - n.getMin());
                n.setManualValue((int) Math.round(val));
                enforceNumberBounds(module);
                return;
            }
            if (setting instanceof DecimalSetting) {
                DecimalSetting d = (DecimalSetting) setting;
                double val = d.getMin() + pct * (d.getMax() - d.getMin());
                d.setValue(val);
                return;
            }
            if (setting instanceof IntRangeSetting) {
                IntRangeSetting r = (IntRangeSetting) setting;
                int val = (int) Math.round(r.getMin() + pct * (r.getMax() - r.getMin()));
                r.setHigh(Math.max(r.getLow(), val));
            }
        }

        private static String getCategoryDisplayName(Category category) {
            switch (category) {
                case COMBAT: return "Combat";
                case MOVEMENT: return "Movement";
                case PLAYER: return "Player";
                case RENDER: return "Visual";
                case CLIENT: return "Client";
                case MISC: return "Other";
                default:
                    return category.name();
            }
        }

        private void drawDescriptionOverlay(net.minecraft.client.gui.FontRenderer fontRenderer, int screenWidth) {
            if (shouldShowDescription(hoveredModule)) {
                drawModuleDescription(hoveredModule, hoveredRowY, fontRenderer, screenWidth);
            }
        }

        private void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (isHovered(mouseX, mouseY, x, y, WIDTH, HEADER_HEIGHT)) {
                if (mouseButton == 0) {
                    dragging = true;
                    dragOffsetX = mouseX - x;
                    dragOffsetY = mouseY - y;
                    return;
                }
                if (mouseButton == 1) {
                    collapsed = !collapsed;
                    return;
                }
            }

            if (collapsed) {
                return;
            }

            int rowY = y + HEADER_HEIGHT - scrollOffset;
            for (Module module : modules) {
                if (!module.isVisible()) {
                    continue;
                }
                if (isHovered(mouseX, mouseY, x, rowY, WIDTH, ROW_HEIGHT)) {
                    if (mouseButton == 0) {
                        module.toggle();
                        return;
                    }
                    if (mouseButton == 1) {
                        expandedModule = expandedModule == module ? null : module;
                        if (expandedModule != module) {
                            bindingModule = null;
                        }
                        return;
                    }
                }
                rowY += ROW_HEIGHT;

                if (expandedModule == module) {
                    for (Setting setting : module.getSettings()) {
                        if (!setting.isVisible()) {
                            continue;
                        }
                        if (isHovered(mouseX, mouseY, x + 4, rowY, WIDTH - 8, ROW_HEIGHT)) {
                            if (isSliderSetting(setting) && mouseButton == 0) {
                                draggingSliderSetting = setting;
                                draggingSliderModule = module;
                                double pct = Math.max(0.0, Math.min(1.0, (double) (mouseX - (x + 6)) / (double) (WIDTH - 12)));
                                updateSettingFromPct(setting, module, pct);
                                return;
                            }
                            handleSettingClick(setting, mouseButton, module);
                            return;
                        }
                        rowY += ROW_HEIGHT;
                    }

                    if (module.showsKeybindSetting() && isHovered(mouseX, mouseY, x + 4, rowY, WIDTH - 8, ROW_HEIGHT)) {
                        handleKeybindClick(module, mouseButton);
                        return;
                    }
                    if (module.showsKeybindSetting()) {
                        rowY += ROW_HEIGHT;
                    }
                }
            }
        }

        private void mouseReleased() {
            dragging = false;
            draggingSliderSetting = null;
            draggingSliderModule = null;
        }

        private void offset(int amount) {
            y += amount;
        }

        private int getContentHeight() {
            if (collapsed) {
                return HEADER_HEIGHT;
            }
            int rows = 0;
            for (Module module : modules) {
                if (module.isVisible()) {
                    rows++;
                }
            }
            if (expandedModule != null) {
                for (Setting setting : expandedModule.getSettings()) {
                    if (setting.isVisible()) {
                        rows++;
                    }
                }
                if (expandedModule.showsKeybindSetting()) {
                    rows++;
                }
            }
            return HEADER_HEIGHT + (rows * ROW_HEIGHT);
        }

        private void handleSettingClick(Setting setting, int mouseButton, Module module) {
            if (setting instanceof ActionSetting && mouseButton == 0) {
                ((ActionSetting) setting).run();
                return;
            }

            if (setting instanceof BooleanSetting && mouseButton == 0) {
                ((BooleanSetting) setting).toggle();
                return;
            }

            if (setting instanceof IntRangeSetting) {
                IntRangeSetting range = (IntRangeSetting) setting;
                boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                if (mouseButton == 0) {
                    if (shift) {
                        range.incrementLow();
                    } else {
                        range.incrementHigh();
                    }
                } else if (mouseButton == 1) {
                    if (shift) {
                        range.decrementLow();
                    } else {
                        range.decrementHigh();
                    }
                }
                return;
            }

            if (setting instanceof NumberSetting) {
                NumberSetting number = (NumberSetting) setting;
                if (mouseButton == 0) {
                    number.increment();
                } else if (mouseButton == 1) {
                    number.decrement();
                }

                enforceNumberBounds(module);
                return;
            }

            if (setting instanceof DecimalSetting) {
                DecimalSetting decimal = (DecimalSetting) setting;
                if (mouseButton == 0) {
                    decimal.increment();
                } else if (mouseButton == 1) {
                    decimal.decrement();
                }
                return;
            }

            if (setting instanceof EnumSetting && (mouseButton == 0 || mouseButton == 1)) {
                EnumSetting<?> enumSetting = (EnumSetting<?>) setting;
                if (mouseButton == 0) {
                    enumSetting.cycleForward();
                } else {
                    enumSetting.cycleBackward();
                }
                refreshClickGuiStyleIfNeeded(module, setting);
            }
        }

        private void refreshClickGuiStyleIfNeeded(Module module, Setting setting) {
            if (module != ClickGuiModule.getInstance() || !"Style".equals(setting.getName())) {
                return;
            }

            LionClient client = LionClient.getInstance();
            if (client != null) {
                client.refreshClickGuiStyle();
            }
        }

        private void enforceNumberBounds(Module module) {
            NumberSetting min = null;
            NumberSetting max = null;
            for (Setting setting : module.getSettings()) {
                if (!(setting instanceof NumberSetting)) {
                    continue;
                }

                if ("Min CPS".equals(setting.getName())) {
                    min = (NumberSetting) setting;
                } else if ("Max CPS".equals(setting.getName())) {
                    max = (NumberSetting) setting;
                }
            }

            if (min != null && max != null && max.getValue() < min.getValue()) {
                max.setManualValue(min.getValue());
            }
        }

        private void handleKeybindClick(Module module, int mouseButton) {
            if (bindingModule != null) {
                if (mouseButton >= 2) {
                    bindingModule.setKeyCode(-100 + mouseButton);
                } else if (mouseButton == 1 && bindingModule.canBeUnbound()) {
                    bindingModule.setKeyCode(Keyboard.KEY_NONE);
                }
                bindingModule = null;
                return;
            }

            if (mouseButton == 0) {
                bindingModule = module;
                return;
            }

            if (mouseButton == 1 && module.canBeUnbound()) {
                module.setKeyCode(Keyboard.KEY_NONE);
                bindingModule = null;
            }
        }

        private boolean handleKeyTyped(int keyCode) {
            if (bindingModule == null) {
                return false;
            }

            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                if (bindingModule.canBeUnbound()) {
                    bindingModule.setKeyCode(Keyboard.KEY_NONE);
                }
            } else {
                bindingModule.setKeyCode(keyCode);
            }

            bindingModule = null;
            return true;
        }

        private String getKeybindText(Module module) {
            return com.lionclient.util.KeyBindUtil.getKeyName(module.getKeyCode()).toUpperCase();
        }

        private void updateHoveredModule(Module module) {
            long now = System.currentTimeMillis();
            if (module != hoveredModule) {
                hoveredModule = module;
                hoveredSince = module == null ? 0L : now;
            }
        }

        private boolean shouldShowDescription(Module module) {
            return module != null
                && module.getDescription() != null
                && !module.getDescription().isEmpty()
                && System.currentTimeMillis() - hoveredSince >= DESCRIPTION_HOVER_DELAY_MS;
        }

        private void drawModuleDescription(Module module, int rowY, net.minecraft.client.gui.FontRenderer fontRenderer, int screenWidth) {
            String description = module.getDescription();
            int padding = 4;
            int tooltipWidth = fontRenderer.getStringWidth(description) + (padding * 2);
            int tooltipX = x + (WIDTH - tooltipWidth) / 2;
            int tooltipY = rowY - ROW_HEIGHT - 4;

            if (tooltipX < 4) {
                tooltipX = 4;
            }

            int maxX = screenWidth - tooltipWidth - 4;
            if (tooltipX > maxX) {
                tooltipX = maxX;
            }

            if (tooltipY < 4) {
                tooltipY = rowY + 2;
            }

            Gui.drawRect(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + ROW_HEIGHT + 1, 0x66000000);
            Gui.drawRect(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + ROW_HEIGHT, 0xE0101018);
            Gui.drawRect(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 1, 0xFF000000 | ClickGuiModule.getAccentColor());
            fontRenderer.drawStringWithShadow(description, tooltipX + padding, tooltipY + 3, 0xFFFFFFFF);
        }

        private boolean isHovered(int mouseX, int mouseY, int rectX, int rectY, int width, int height) {
            return mouseX >= rectX && mouseX <= rectX + width && mouseY >= rectY && mouseY <= rectY + height;
        }
    }
}
