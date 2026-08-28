package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public final class ClickGuiModule extends Module {
    private static final int DEFAULT_CLASSIC_ACCENT_COLOR = 0x3882FF;
    private static final ThemePalette DEFAULT_THEME = ThemePalette.forPreset(ThemePreset.DEEP_OCEAN);
    private static ClickGuiModule instance;

    private final EnumSetting<GuiStyle> style = new EnumSetting<GuiStyle>("Style", GuiStyle.values(), GuiStyle.MODERN);
    private final EnumSetting<ThemePreset> theme = new EnumSetting<ThemePreset>("Theme", ThemePreset.values(), ThemePreset.DEEP_OCEAN);
    private final NumberSetting red = new NumberSetting("Red", 0, 255, 5, 48);
    private final NumberSetting green = new NumberSetting("Green", 0, 255, 5, 92);
    private final NumberSetting blue = new NumberSetting("Blue", 0, 255, 5, 168);
    private final BooleanSetting snowflakes = new BooleanSetting("Snowflakes", true);
    private final NumberSetting modernRed = new NumberSetting("Modern Red", 0, 255, 1, 74);
    private final NumberSetting modernGreen = new NumberSetting("Modern Green", 0, 255, 1, 158);
    private final NumberSetting modernBlue = new NumberSetting("Modern Blue", 0, 255, 1, 255);

    public ClickGuiModule() {
        super("ClickGUI", "Configure the ClickGUI", Category.CLIENT, Keyboard.KEY_RSHIFT);
        instance = this;
        addSetting(style);
        addSetting(theme);
        addSetting(snowflakes);
        addSetting(modernRed);
        addSetting(modernGreen);
        addSetting(modernBlue);
        addSetting(red);
        addSetting(green);
        addSetting(blue);

        java.util.function.BooleanSupplier classicVisibility = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return style.getValue() == GuiStyle.CLASSIC;
            }
        };
        java.util.function.BooleanSupplier modernVisibility = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return style.getValue() == GuiStyle.MODERN;
            }
        };
        java.util.function.BooleanSupplier customThemeVisibility = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return style.getValue() == GuiStyle.MODERN && theme.getValue() == ThemePreset.CUSTOM;
            }
        };

        snowflakes.setVisibility(modernVisibility);
        modernRed.setVisibility(customThemeVisibility);
        modernGreen.setVisibility(customThemeVisibility);
        modernBlue.setVisibility(customThemeVisibility);
        red.setVisibility(classicVisibility);
        green.setVisibility(classicVisibility);
        blue.setVisibility(classicVisibility);
    }

    @Override
    public void toggle() {
        LionClient client = LionClient.getInstance();
        if (client != null) {
            client.toggleClickGui();
        }
    }

    @Override
    public boolean canBeUnbound() {
        return false;
    }

    public static int getAccentColor() {
        if (instance == null) {
            return DEFAULT_CLASSIC_ACCENT_COLOR;
        }
        return toColor(instance.red, instance.green, instance.blue);
    }

    public static GuiStyle getGuiStyle() {
        return instance == null ? GuiStyle.MODERN : instance.style.getValue();
    }

    public static ClickGuiModule getInstance() {
        return instance;
    }

    public static int getModernAccentColor() {
        if (instance == null) {
            return DEFAULT_THEME.getAccentColor();
        }
        if (instance.theme.getValue() == ThemePreset.RAINBOW) {
            return getRainbowColor();
        }
        if (instance.theme.getValue() == ThemePreset.RGB) {
            return getTriRgbColor();
        }
        return instance.getThemePalette().getAccentColor();
    }

    public static int getRainbowColor() {
        float hue = (System.currentTimeMillis() % 5000L) / 5000.0F;
        return java.awt.Color.HSBtoRGB(hue, 0.85F, 1.0F) & 0xFFFFFF;
    }

    // Crimson Red, Toxic Lime Green, Sky Blue — the only 3 RGB colors
    public static final int TRI_RED   = 0xDC143C; // Crimson Red
    public static final int TRI_GREEN = 0x39FF14; // Toxic Lime Green
    public static final int TRI_BLUE  = 0x87CEEB; // Sky Blue

    public static int getTriRgbColor() {
        float progress = getTriRgbPhase();
        if (progress < 0.333F) {
            float p = progress / 0.333F;
            return blendColor(TRI_RED, TRI_GREEN, p);
        } else if (progress < 0.666F) {
            float p = (progress - 0.333F) / 0.333F;
            return blendColor(TRI_GREEN, TRI_BLUE, p);
        } else {
            float p = (progress - 0.666F) / 0.334F;
            return blendColor(TRI_BLUE, TRI_RED, p);
        }
    }

    /** Returns 0.0 - 1.0 progress through the tri-color cycle */
    public static float getTriRgbPhase() {
        long time = System.currentTimeMillis() % 4500L;
        return time / 4500.0F;
    }

    /**
     * Returns an animated ThemePalette for the RGB theme that blends between:
     *  - Phase 1 (Red):   Crimson red accent, extreme black background
     *  - Phase 2 (Green): Toxic lime green accent, forest green background
     *  - Phase 3 (Blue):  Sky blue accent, extreme dark blue background
     */
    public static ThemePalette getTriRgbPalette() {
        float phase = getTriRgbPhase();
        int accent = getTriRgbColor();
        int soft = blendColor(accent, 0xFFFFFF, 0.55F);
        int dark = blendColor(accent, 0x000000, 0.60F);

        // Phase-specific backgrounds
        // Red phase:   extreme black (0x060606 base)
        // Green phase: forest green  (0x0A1F0A base)
        // Blue phase:  extreme dark blue (0x020820 base)
        int bgWindow, bgSidebar, bgHeader, bgPanel, bgPanelAlt, bgRow, bgInput, bgOutline, bgMuted, bgDisabled;

        if (phase < 0.333F) {
            float p = phase / 0.333F;
            // Red -> Green transition
            bgWindow   = blendColor(0x060606, 0x0A1F0A, p);
            bgSidebar  = blendColor(0x0A0808, 0x0E260C, p);
            bgHeader   = blendColor(0x0E0A0A, 0x143212, p);
            bgPanel    = blendColor(0x0C0808, 0x122C10, p);
            bgPanelAlt = blendColor(0x080606, 0x0E240C, p);
            bgRow      = blendColor(0x100C0C, 0x1A3A16, p);
            bgInput    = blendColor(0x0A0808, 0x10280E, p);
            bgOutline  = blendColor(0x2A1818, 0x2A5C22, p);
            bgMuted    = blendColor(0x8A6060, 0x6AAC58, p);
            bgDisabled = blendColor(0x6A4848, 0x508840, p);
        } else if (phase < 0.666F) {
            float p = (phase - 0.333F) / 0.333F;
            // Green -> Blue transition
            bgWindow   = blendColor(0x0A1F0A, 0x020820, p);
            bgSidebar  = blendColor(0x0E260C, 0x060E30, p);
            bgHeader   = blendColor(0x143212, 0x0A1640, p);
            bgPanel    = blendColor(0x122C10, 0x081238, p);
            bgPanelAlt = blendColor(0x0E240C, 0x060E2E, p);
            bgRow      = blendColor(0x1A3A16, 0x0E1C4C, p);
            bgInput    = blendColor(0x10280E, 0x071032, p);
            bgOutline  = blendColor(0x2A5C22, 0x1A3078, p);
            bgMuted    = blendColor(0x6AAC58, 0x6090C8, p);
            bgDisabled = blendColor(0x508840, 0x4870A0, p);
        } else {
            float p = (phase - 0.666F) / 0.334F;
            // Blue -> Red transition
            bgWindow   = blendColor(0x020820, 0x060606, p);
            bgSidebar  = blendColor(0x060E30, 0x0A0808, p);
            bgHeader   = blendColor(0x0A1640, 0x0E0A0A, p);
            bgPanel    = blendColor(0x081238, 0x0C0808, p);
            bgPanelAlt = blendColor(0x060E2E, 0x080606, p);
            bgRow      = blendColor(0x0E1C4C, 0x100C0C, p);
            bgInput    = blendColor(0x071032, 0x0A0808, p);
            bgOutline  = blendColor(0x1A3078, 0x2A1818, p);
            bgMuted    = blendColor(0x6090C8, 0x8A6060, p);
            bgDisabled = blendColor(0x4870A0, 0x6A4848, p);
        }

        return new ThemePalette(accent, soft, dark, bgWindow, bgSidebar, bgHeader,
                bgPanel, bgPanelAlt, bgRow, bgInput, bgOutline, bgMuted, bgDisabled);
    }

    public static boolean isRainbowTheme() {
        return instance != null && instance.theme.getValue() == ThemePreset.RAINBOW;
    }

    public static boolean isRgbTheme() {
        return instance != null && instance.theme.getValue() == ThemePreset.RGB;
    }

    public static boolean areSnowflakesEnabled() {
        return instance == null || instance.snowflakes.isEnabled();
    }

    public static int getLightAccentColor() {
        return blendColor(getModernAccentColor(), 0xFFFFFF, 0.52F);
    }

    public static int getDarkAccentColor() {
        return blendColor(getModernAccentColor(), 0x08111B, 0.48F);
    }

    public static ThemePalette getThemePalette() {
        if (instance == null) {
            return DEFAULT_THEME;
        }

        if (instance.style.getValue() != GuiStyle.MODERN) {
            return DEFAULT_THEME;
        }

        if (instance.theme.getValue() == ThemePreset.CUSTOM) {
            return ThemePalette.custom(toColor(instance.modernRed, instance.modernGreen, instance.modernBlue));
        }

        if (instance.theme.getValue() == ThemePreset.RAINBOW) {
            return ThemePalette.custom(getRainbowColor());
        }

        if (instance.theme.getValue() == ThemePreset.RGB) {
            return getTriRgbPalette();
        }

        return ThemePalette.forPreset(instance.theme.getValue());
    }

    public static String getThemeName() {
        if (instance == null) {
            return ThemePreset.DEEP_OCEAN.toString();
        }

        if (instance.style.getValue() != GuiStyle.MODERN) {
            return ThemePreset.DEEP_OCEAN.toString();
        }

        return instance.theme.getValue().toString();
    }

    public static int blendColor(int start, int end, float progress) {
        float amount = Math.max(0.0F, Math.min(1.0F, progress));
        int startR = (start >>> 16) & 255;
        int startG = (start >>> 8) & 255;
        int startB = start & 255;
        int endR = (end >>> 16) & 255;
        int endG = (end >>> 8) & 255;
        int endB = end & 255;
        int red = Math.round(startR + ((endR - startR) * amount));
        int green = Math.round(startG + ((endG - startG) * amount));
        int blue = Math.round(startB + ((endB - startB) * amount));
        return (red << 16) | (green << 8) | blue;
    }

    private static int toColor(NumberSetting red, NumberSetting green, NumberSetting blue) {
        return ((red.getValue() & 255) << 16)
            | ((green.getValue() & 255) << 8)
            | (blue.getValue() & 255);
    }

    public enum ThemePreset {
        CYBERPUNK("Cyberpunk"),
        AMBER_GOLD("Amber Gold"),
        TOXIC_LIME("Toxic Lime"),
        BLOOD_RED("Blood Red"),
        DEEP_OCEAN("Deep Ocean"),
        ROYAL_INDIGO("Royal Indigo"),
        SUNSET_VIOLET("Sunset Violet"),
        STEEL_TEAL("Steel Teal"),
        CARBON_WHITE("Carbon White"),
        CRIMSON_BLACK("Crimson Black"),
        RAINBOW("Rainbow"),
        RGB("RGB"),
        CUSTOM("Custom");

        private final String label;

        ThemePreset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final class ThemePalette {
        private final int accentColor;
        private final int accentSoftColor;
        private final int accentDarkColor;
        private final int windowColor;
        private final int sidebarColor;
        private final int headerColor;
        private final int panelColor;
        private final int panelAltColor;
        private final int rowColor;
        private final int inputColor;
        private final int outlineColor;
        private final int textMutedColor;
        private final int textDisabledColor;

        private ThemePalette(int accentColor, int accentSoftColor, int accentDarkColor, int windowColor, int sidebarColor, int headerColor, int panelColor, int panelAltColor, int rowColor, int inputColor, int outlineColor, int textMutedColor, int textDisabledColor) {
            this.accentColor = accentColor;
            this.accentSoftColor = accentSoftColor;
            this.accentDarkColor = accentDarkColor;
            this.windowColor = windowColor;
            this.sidebarColor = sidebarColor;
            this.headerColor = headerColor;
            this.panelColor = panelColor;
            this.panelAltColor = panelAltColor;
            this.rowColor = rowColor;
            this.inputColor = inputColor;
            this.outlineColor = outlineColor;
            this.textMutedColor = textMutedColor;
            this.textDisabledColor = textDisabledColor;
        }

        public int getAccentColor() { return accentColor; }
        public int getAccentSoftColor() { return accentSoftColor; }
        public int getAccentDarkColor() { return accentDarkColor; }
        public int getWindowColor() { return windowColor; }
        public int getSidebarColor() { return sidebarColor; }
        public int getHeaderColor() { return headerColor; }
        public int getPanelColor() { return panelColor; }
        public int getPanelAltColor() { return panelAltColor; }
        public int getRowColor() { return rowColor; }
        public int getInputColor() { return inputColor; }
        public int getOutlineColor() { return outlineColor; }
        public int getTextMutedColor() { return textMutedColor; }
        public int getTextDisabledColor() { return textDisabledColor; }

        private static ThemePalette forPreset(ThemePreset preset) {
            switch (preset) {
                case CYBERPUNK:
                    return new ThemePalette(
                        0xC738FF, 0xF0B0FF, 0x340A48,
                        0x140A1E, 0x1B0E2A, 0x28143C,
                        0x241236, 0x1C0E2B, 0x361B50,
                        0x1D0E2B, 0x562478, 0xB686D4, 0x9064AA
                    );
                case AMBER_GOLD:
                    return new ThemePalette(
                        0xFFB000, 0xFFE299, 0x4A3000,
                        0x1C1306, 0x261B0B, 0x382812,
                        0x32230F, 0x261B0B, 0x463216,
                        0x281C0C, 0x785418, 0xD4B686, 0xAA8B5C
                    );
                case TOXIC_LIME:
                    return new ThemePalette(
                        0x84CC16, 0xD2F58A, 0x223A00,
                        0x0D1606, 0x13200B, 0x1F3012,
                        0x1A2A0F, 0x13200B, 0x293D18,
                        0x15220C, 0x466616, 0x98C46C, 0x72984A
                    );
                case BLOOD_RED:
                    return new ThemePalette(
                        0xEF4444, 0xFCA5A5, 0x4A0C0C,
                        0x1A0909, 0x240F0F, 0x381616,
                        0x301313, 0x240F0F, 0x461B1B,
                        0x261010, 0x782424, 0xD48686, 0xAA5C5C
                    );
                case ROYAL_INDIGO:
                    return new ThemePalette(
                        0x6366F1, 0xC7D2FE, 0x1E1B4B,
                        0x0C0D20, 0x12142E, 0x1C1F44,
                        0x181B3C, 0x12142E, 0x242856,
                        0x131530, 0x34397C, 0x8A8FC8, 0x64699E
                    );
                case SUNSET_VIOLET:
                    return new ThemePalette(
                        0xE040FB, 0xF8BBD0, 0x4A005B,
                        0x18081C, 0x220C28, 0x34123B,
                        0x2D0F34, 0x220C28, 0x40164A,
                        0x240D2C, 0x6C207D, 0xC67ACF, 0x9E58A6
                    );
                case STEEL_TEAL:
                    return new ThemePalette(
                        0x14B8A6, 0x99F6E4, 0x043A35,
                        0x071917, 0x0D2422, 0x143431,
                        0x112E2B, 0x0D2422, 0x1A423E,
                        0x0E2624, 0x1A645C, 0x70C2B8, 0x509A90
                    );
                case CARBON_WHITE:
                    return new ThemePalette(
                        0xF3F4F6, 0xFFFFFF, 0x1F2937,
                        0x111317, 0x171A20, 0x222630,
                        0x1D212A, 0x171A20, 0x292E3B,
                        0x181B22, 0x485060, 0x9CA3AF, 0x6B7280
                    );
                case CRIMSON_BLACK:
                    // Accent: #990000 (Crimson Red)  Background base: #030201 (near-black)
                    // Rich dark contrast — subtle crimson bleed into deep black surfaces
                    return new ThemePalette(
                        0x990000,  // accent: crimson red
                        0xCC4444,  // accentSoft: muted crimson highlight
                        0x330000,  // accentDark: deep blood shadow
                        0x030201,  // window: extreme black (#030201)
                        0x060303,  // sidebar: barely-there crimson tint
                        0x0C0504,  // header: dark with faint warm undertone
                        0x0A0404,  // panel: near-black with trace red
                        0x060303,  // panelAlt: matches sidebar depth
                        0x120606,  // row: subtle crimson lift for contrast
                        0x080303,  // input: deep black-red
                        0x3D0A0A,  // outline: visible crimson border
                        0x8C3030,  // textMuted: readable crimson-grey
                        0x5C1A1A   // textDisabled: dim blood tone
                    );
                case RAINBOW:
                    return new ThemePalette(
                        0xFFFFFF, 0xF0F0F0, 0x1C1C24,
                        0x121218, 0x18181E, 0x222228,
                        0x202026, 0x18181E, 0x2C2C36,
                        0x18181E, 0x484858, 0xA0A0B4, 0x808098
                    );
                case RGB:
                    return getTriRgbPalette();
                case CUSTOM:
                case DEEP_OCEAN:
                default:
                    return new ThemePalette(
                        0x0EA5E9, 0xBAE6FD, 0x033852,
                        0x071520, 0x0C1E2C, 0x142C40,
                        0x112638, 0x0C1E2C, 0x183750,
                        0x0D202E, 0x18567A, 0x7AB6D6, 0x5A8EAA
                    );
            }
        }

        private static ThemePalette custom(int accentColor) {
            int soft = blendColor(accentColor, 0xFFFFFF, 0.62F);
            int dark = blendColor(accentColor, 0x08111B, 0.48F);
            int window = blendColor(accentColor, 0x0B0F14, 0.92F);
            int sidebar = blendColor(accentColor, 0x10161D, 0.86F);
            int header = blendColor(accentColor, 0x171E27, 0.84F);
            int panel = blendColor(accentColor, 0x1C2631, 0.80F);
            int panelAlt = blendColor(accentColor, 0x121923, 0.84F);
            int row = blendColor(accentColor, 0x232E3A, 0.72F);
            int input = blendColor(accentColor, 0x131922, 0.85F);
            int outline = blendColor(accentColor, 0x3F4E5D, 0.78F);
            int muted = blendColor(accentColor, 0x90A1B2, 0.76F);
            int disabled = blendColor(accentColor, 0x6B7A88, 0.74F);
            return new ThemePalette(accentColor, soft, dark, window, sidebar, header, panel, panelAlt, row, input, outline, muted, disabled);
        }
    }

    public enum GuiStyle {
        MODERN("Modern"),
        CLASSIC("Classic");

        private final String label;

        GuiStyle(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
