package com.lionclient.feature.setting;

import com.lionclient.config.ConfigManager;

public final class EnumSetting<T extends Enum<T>> extends Setting {
    private final T[] values;
    private int index;

    public EnumSetting(String name, T[] values, T initial) {
        super(name);
        this.values = values;
        this.index = indexOf(initial);
    }

    public T getValue() {
        return values[index];
    }

    public T[] getValues() {
        return values;
    }

    public boolean setIndex(int index) {
        if (index < 0 || index >= values.length) {
            return false;
        }

        this.index = index;
        ConfigManager.saveActiveConfig();
        return true;
    }

    public void setValue(T value) {
        this.index = indexOf(value);
        ConfigManager.saveActiveConfig();
    }

    public boolean setValueByName(String name) {
        if (name == null) return false;
        String cleanInput = name.replace(" ", "").replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);

        for (int i = 0; i < values.length; i++) {
            T val = values[i];
            if (val.name().equalsIgnoreCase(name) || val.toString().equalsIgnoreCase(name)) {
                index = i;
                ConfigManager.saveActiveConfig();
                return true;
            }
            String cleanName = val.name().replace(" ", "").replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
            String cleanStr = val.toString().replace(" ", "").replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
            if (cleanName.equals(cleanInput) || cleanStr.equals(cleanInput)) {
                index = i;
                ConfigManager.saveActiveConfig();
                return true;
            }
        }
        return false;
    }

    public void cycleForward() {
        index = (index + 1) % values.length;
        ConfigManager.saveActiveConfig();
    }

    public void cycleBackward() {
        index = (index - 1 + values.length) % values.length;
        ConfigManager.saveActiveConfig();
    }

    @Override
    public String getValueText() {
        return getValue().toString();
    }

    private int indexOf(T initial) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == initial) {
                return i;
            }
        }
        return 0;
    }
}
