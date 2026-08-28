package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.ActionSetting;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;

public final class HudModule extends Module {
    private static HudModule instance;

    public enum ColorMode {
        THEME("Theme"),
        CUSTOM("Custom"),
        CATEGORY("Category"),
        RAINBOW("Rainbow");

        private final String label;

        ColorMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.MODERN);
    private final EnumSetting<ColorMode> colorMode = new EnumSetting<ColorMode>("Color Mode", ColorMode.values(), ColorMode.THEME);
    private final BooleanSetting logo = new BooleanSetting("Logo", true);
    private final NumberSetting red = new NumberSetting("Red", 0, 255, 5, 255);
    private final NumberSetting green = new NumberSetting("Green", 0, 255, 5, 255);
    private final NumberSetting blue = new NumberSetting("Blue", 0, 255, 5, 255);
    private final NumberSetting x = new NumberSetting("X", 0, 4000, 2, 4);
    private final NumberSetting y = new NumberSetting("Y", 0, 4000, 2, 4);
    private final ActionSetting editor = new ActionSetting("Move HUD", new Runnable() {
        @Override
        public void run() {
            LionClient client = LionClient.getInstance();
            if (client != null) {
                client.openHudEditor();
            }
        }
    }, new ActionSetting.ValueProvider() {
        @Override
        public String get() {
            return "OPEN";
        }
    });

    private final Map<Module, Float> alphaMap = new HashMap<Module, Float>();
    private long lastFrameTime = System.currentTimeMillis();

    public HudModule() {
        super("HUD", "Displays enabled modules on screen.", Category.RENDER, Keyboard.KEY_NONE);
        instance = this;
        red.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return colorMode.getValue() == ColorMode.CUSTOM;
            }
        });
        green.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return colorMode.getValue() == ColorMode.CUSTOM;
            }
        });
        blue.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return colorMode.getValue() == ColorMode.CUSTOM;
            }
        });
        addSetting(mode);
        addSetting(colorMode);
        addSetting(logo);
        addSetting(red);
        addSetting(green);
        addSetting(blue);
        addSetting(x);
        addSetting(y);
        addSetting(editor);
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings.showDebugInfo) {
            return;
        }

        long now = System.currentTimeMillis();
        float delta = (now - lastFrameTime) / 1000.0F;
        lastFrameTime = now;
        if (delta <= 0.0F || delta > 1.0F) {
            delta = 0.016F;
        }

        // 1. Draw Watermark in top-left
        if (logo.isEnabled()) {
            drawGradientWatermark(minecraft);
        }

        // 2. Draw Array list in top-right with category colors and animations
        drawActiveModules(event.resolution, delta);
    }

    private void drawGradientWatermark(Minecraft mc) {
        String watermarkText = "FURY v" + LionClient.VERSION;
        int curX = 6;
        int themeAccent = ClickGuiModule.getModernAccentColor();
        for (int i = 0; i < watermarkText.length(); i++) {
            char c = watermarkText.charAt(i);
            float pct = (float) i / watermarkText.length();
            int charColor = ClickGuiModule.blendColor(themeAccent, 0xFFFFFFFF, pct * 0.4f);
            mc.fontRendererObj.drawStringWithShadow(String.valueOf(c), curX, 6, charColor);
            curX += mc.fontRendererObj.getCharWidth(c);
        }
    }

    private void drawActiveModules(ScaledResolution resolution, float delta) {
        Minecraft mc = Minecraft.getMinecraft();
        LionClient client = LionClient.getInstance();
        if (client == null) return;

        List<Module> active = new ArrayList<Module>();
        for (Module module : client.getModuleManager().getModules()) {
            Float alphaVal = alphaMap.get(module);
            float alpha = alphaVal != null ? alphaVal.floatValue() : 0.0F;
            if (module.isEnabled() && module.isVisible() && module != this) {
                alpha = Math.min(1.0F, alpha + delta * 5.0F);
            } else {
                alpha = Math.max(0.0F, alpha - delta * 5.0F);
            }
            alphaMap.put(module, Float.valueOf(alpha));

            if (alpha > 0.01F) {
                active.add(module);
            }
        }

        // Sort by module name length descending (Rise style)
        Collections.sort(active, new Comparator<Module>() {
            @Override
            public int compare(Module o1, Module o2) {
                String s1 = o1.getName() + (o1.getHudInfo() != null ? " " + o1.getHudInfo() : "");
                String s2 = o2.getName() + (o2.getHudInfo() != null ? " " + o2.getHudInfo() : "");
                return s2.length() - s1.length();
            }
        });

        int screenWidth = resolution.getScaledWidth();
        int y = 6;

        int index = 0;
        long time = System.currentTimeMillis();

        for (Module module : active) {
            Float alphaVal = alphaMap.get(module);
            float alpha = alphaVal != null ? alphaVal.floatValue() : 1.0F;

            String hudInfo = module.getHudInfo();
            String name = (hudInfo == null || hudInfo.isEmpty()) ? module.getName() : module.getName() + " " + hudInfo;
            int textWidth = mc.fontRendererObj.getStringWidth(name);

            int baseColor;
            switch (colorMode.getValue()) {
                case CUSTOM:
                    baseColor = toColor(red.getValue(), green.getValue(), blue.getValue());
                    break;
                case CATEGORY:
                    baseColor = getCategoryColor(module.getCategory());
                    break;
                case RAINBOW:
                    float hue = ((time % 4000L) / 4000.0F + (index * 0.08F)) % 1.0F;
                    baseColor = java.awt.Color.HSBtoRGB(hue, 0.75F, 1.0F) & 0x00FFFFFF;
                    break;
                case THEME:
                default:
                    baseColor = ClickGuiModule.getModernAccentColor() & 0x00FFFFFF;
                    break;
            }

            int barCol = withAlpha(baseColor, (int) (255 * alpha));
            int renderTextColor = (colorMode.getValue() == ColorMode.CATEGORY)
                    ? withAlpha(0xFFFFFFFF, (int) (255 * alpha))
                    : withAlpha(baseColor, (int) (255 * alpha));

            // Draw left-side accent bar next to the entry
            int barX = screenWidth - 4;
            int rowHeight = mc.fontRendererObj.FONT_HEIGHT + 2;

            // Smooth scale vertical entry space as it fades
            int drawY = y;
            y += Math.round(rowHeight * alpha);

            // Draw text
            mc.fontRendererObj.drawStringWithShadow(name, screenWidth - textWidth - 8, drawY + 1, renderTextColor);

            // Draw Accent Bar
            Gui.drawRect(barX, drawY, screenWidth - 2, drawY + rowHeight, barCol);

            index++;
        }
    }

    private int getCategoryColor(Category category) {
        switch (category) {
            case COMBAT: return 0xFFFF4A4A; // Red
            case MOVEMENT: return 0xFF4A9EFF; // Blue
            case RENDER: return 0xFFFF4AFF; // Purple/Magenta
            case PLAYER: return 0xFFFFC14A; // Orange/Yellow
            case CLIENT: return 0xFF4AFFFF; // Cyan
            case MISC:
            default:
                return 0xFF4AFF4A; // Green
        }
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 255) << 24);
    }

    private static int toColor(int r, int g, int b) {
        return ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    public static HudModule getInstance() {
        return instance;
    }

    public int getAnchorX() {
        return x.getValue();
    }

    public int getAnchorY() {
        return y.getValue();
    }

    public void setPosition(int x, int y) {
        this.x.setValue(x);
        this.y.setValue(y);
    }

    public boolean isRightAligned(ScaledResolution resolution) {
        return x.getValue() >= resolution.getScaledWidth() / 2;
    }

    private enum Mode {
        MODERN("Modern"),
        CLASSIC("Classic");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public void renderEditorPreview(ScaledResolution resolution) {
        drawActiveModules(resolution, 0.016F);
    }

    public int getPreviewWidth(Minecraft minecraft) {
        int width = 0;
        List<String> preview = getPreviewModuleNames();
        for (String s : preview) {
            width = Math.max(width, minecraft.fontRendererObj.getStringWidth(s) + 12);
        }
        return width;
    }

    public int getPreviewHeight(Minecraft minecraft) {
        return getPreviewModuleNames().size() * (minecraft.fontRendererObj.FONT_HEIGHT + 2);
    }

    private List<String> getPreviewModuleNames() {
        List<String> list = new ArrayList<String>();
        list.add("KillAura");
        list.add("LeftClicker");
        list.add("Sprint");
        return list;
    }
}
