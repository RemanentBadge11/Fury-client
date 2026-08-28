package com.lionclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
import com.lionclient.feature.setting.ActionSetting;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.IntRangeSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.feature.setting.Setting;
import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigManager instance;
    private static boolean suppressSave;

    private final ModuleManager moduleManager;
    private final File configDirectory;
    private final File currentConfigFile;
    private String currentConfigName;

    public ConfigManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        File base = new File(Minecraft.getMinecraft().mcDataDir, "furyclient");
        this.configDirectory = new File(base, "configs");
        this.currentConfigFile = new File(base, "current-config.txt");
        instance = this;
    }

    public static void saveActiveConfig() {
        if (instance != null && !suppressSave) {
            instance.saveCurrent();
            instance.moduleManager.refreshConfigModule();
        }
    }

    public void initialize() {
        ensureDirectory();
        currentConfigName = readCurrentConfigName();
        if (currentConfigName == null || !getConfigFile(currentConfigName).isFile()) {
            currentConfigName = listConfigs().isEmpty() ? "default" : listConfigs().get(0);
        }
        if (!getConfigFile(currentConfigName).isFile()) {
            saveAs(currentConfigName);
        }
        persistCurrentConfigName();
        applyConfig(currentConfigName, false);

        // Register JVM Shutdown Hook for Auto-Save on Exit
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    saveCurrent();
                }
            }));
        } catch (Throwable ignored) {}
    }

    public List<String> listConfigs() {
        ensureDirectory();
        File[] files = configDirectory.listFiles();
        List<String> names = new ArrayList<String>();
        if (files == null) {
            return names;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                names.add(file.getName().substring(0, file.getName().length() - 5));
            }
        }

        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public String getCurrentConfigName() {
        return currentConfigName;
    }

    public void saveCurrent() {
        if (currentConfigName == null || currentConfigName.isEmpty()) {
            currentConfigName = "default";
        }
        saveAs(currentConfigName);
    }

    public boolean deleteConfig(String name) {
        ensureDirectory();
        File file = getConfigFile(name);
        if (file.isFile() && file.delete()) {
            if (name.equalsIgnoreCase(currentConfigName)) {
                List<String> remaining = listConfigs();
                if (remaining.isEmpty()) {
                    currentConfigName = "default";
                    saveAs(currentConfigName);
                } else {
                    currentConfigName = remaining.get(0);
                    applyConfig(currentConfigName, false);
                }
            }
            return true;
        }
        return false;
    }

    public void createNextConfig() {
        saveCurrent();
        String name = nextConfigName();
        saveAs(name);
        currentConfigName = name;
        persistCurrentConfigName();
    }

    public void deleteCurrentConfig() {
        deleteConfig(currentConfigName);
    }

    public void load(String name) {
        saveCurrent();
        applyConfig(name, true);
    }

    public void applyConfig(String name, boolean notifyChat) {
        File file = getConfigFile(name);
        if (!file.isFile()) {
            if (notifyChat) {
                com.lionclient.util.ChatUtil.sendFormatted("&c[Fury] Config file not found (&o" + name + ".json&r&c)");
            }
            return;
        }

        try {
            suppressSave = true;
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonElement parsed = new JsonParser().parse(raw);
            if (parsed == null || !parsed.isJsonObject()) {
                if (notifyChat) {
                    com.lionclient.util.ChatUtil.sendFormatted("&c[Fury] Invalid config format (&o" + name + ".json&r&c)");
                }
                return;
            }

            JsonObject root = parsed.getAsJsonObject();
            JsonObject modules = root.has("modules") && root.get("modules").isJsonObject()
                    ? root.getAsJsonObject("modules") : root;

            for (Module module : moduleManager.getModules()) {
                JsonElement mElem = modules.get(module.getName());
                if (mElem == null || !mElem.isJsonObject()) {
                    continue;
                }
                JsonObject moduleJson = mElem.getAsJsonObject();

                // Check toggled / enabled
                if (moduleJson.has("toggled")) {
                    module.setEnabled(moduleJson.get("toggled").getAsBoolean());
                } else if (moduleJson.has("enabled")) {
                    module.setEnabled(moduleJson.get("enabled").getAsBoolean());
                }

                // Check key / keyCode
                if (moduleJson.has("key")) {
                    module.setKeyCode(moduleJson.get("key").getAsInt());
                } else if (moduleJson.has("keyCode")) {
                    module.setKeyCode(moduleJson.get("keyCode").getAsInt());
                }

                JsonObject settingsJson = moduleJson.has("settings") && moduleJson.get("settings").isJsonObject()
                        ? moduleJson.getAsJsonObject("settings") : moduleJson;

                for (Setting setting : module.getSettings()) {
                    if (!settingsJson.has(setting.getName())) {
                        continue;
                    }

                    JsonElement value = settingsJson.get(setting.getName());
                    try {
                        if (setting instanceof BooleanSetting) {
                            ((BooleanSetting) setting).setEnabled(value.getAsBoolean());
                        } else if (setting instanceof DecimalSetting) {
                            ((DecimalSetting) setting).setManualValue(value.getAsDouble());
                        } else if (setting instanceof IntRangeSetting && value.isJsonArray()) {
                            JsonArray array = value.getAsJsonArray();
                            if (array.size() >= 2) {
                                ((IntRangeSetting) setting).setRange(array.get(0).getAsInt(), array.get(1).getAsInt(), false);
                            }
                        } else if (setting instanceof NumberSetting) {
                            ((NumberSetting) setting).setManualValue(value.getAsInt());
                        } else if (setting instanceof EnumSetting) {
                            ((EnumSetting<?>) setting).setValueByName(value.getAsString());
                        }
                    } catch (Throwable ignored) {}
                }
            }

            currentConfigName = name;
            persistCurrentConfigName();
            moduleManager.refreshConfigModule();

            if (notifyChat) {
                com.lionclient.util.ChatUtil.sendFormatted("&a[Fury] Config has been loaded (&o" + name + ".json&r&a)");
            }
        } catch (Exception e) {
            if (notifyChat) {
                com.lionclient.util.ChatUtil.sendFormatted("&c[Fury] Config couldn't be loaded (&o" + name + ".json&r&c)");
            }
        } finally {
            suppressSave = false;
        }
    }

    public void openFolder() {
        ensureDirectory();
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(configDirectory);
            }
        } catch (IOException ignored) {
        }
    }

    public void saveAs(String name) {
        ensureDirectory();
        name = sanitize(name);
        File file = getConfigFile(name);
        JsonObject root = new JsonObject();

        for (Module module : moduleManager.getModules()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("toggled", module.isEnabled());
            moduleJson.addProperty("key", module.getKeyCode());

            for (Setting setting : module.getSettings()) {
                if (setting instanceof ActionSetting) {
                    continue;
                }
                if (setting instanceof BooleanSetting) {
                    moduleJson.addProperty(setting.getName(), ((BooleanSetting) setting).isEnabled());
                } else if (setting instanceof DecimalSetting) {
                    moduleJson.addProperty(setting.getName(), ((DecimalSetting) setting).getValue());
                } else if (setting instanceof IntRangeSetting) {
                    IntRangeSetting range = (IntRangeSetting) setting;
                    JsonArray array = new JsonArray();
                    array.add(new JsonPrimitive(range.getLow()));
                    array.add(new JsonPrimitive(range.getHigh()));
                    moduleJson.add(setting.getName(), array);
                } else if (setting instanceof NumberSetting) {
                    moduleJson.addProperty(setting.getName(), ((NumberSetting) setting).getValue());
                } else if (setting instanceof EnumSetting) {
                    moduleJson.addProperty(setting.getName(), ((EnumSetting<?>) setting).getValue().name());
                }
            }

            root.add(module.getName(), moduleJson);
        }

        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            try {
                GSON.toJson(root, writer);
            } finally {
                writer.close();
            }
            currentConfigName = name;
            persistCurrentConfigName();
            moduleManager.refreshConfigModule();
        } catch (IOException ignored) {
        }
    }

    private File getConfigFile(String name) {
        return new File(configDirectory, sanitize(name) + ".json");
    }

    private void ensureDirectory() {
        if (!configDirectory.isDirectory()) {
            configDirectory.mkdirs();
        }
        File parent = currentConfigFile.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
    }

    private String readCurrentConfigName() {
        if (!currentConfigFile.isFile()) {
            return null;
        }

        try {
            return sanitize(new String(Files.readAllBytes(currentConfigFile.toPath()), StandardCharsets.UTF_8).trim());
        } catch (IOException ignored) {
            return null;
        }
    }

    private void persistCurrentConfigName() {
        try {
            Files.write(currentConfigFile.toPath(), currentConfigName.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private String nextConfigName() {
        List<String> configs = listConfigs();
        int index = 1;
        while (configs.contains("config-" + index)) {
            index++;
        }
        return "config-" + index;
    }

    private String sanitize(String input) {
        if (input == null) return "default";
        String s = input.trim();
        if (s.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            s = s.substring(0, s.length() - 5);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                builder.append(c);
            }
        }
        return builder.length() == 0 ? "default" : builder.toString();
    }
}
