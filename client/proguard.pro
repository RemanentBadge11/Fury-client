# ========================================================================
# ProGuard config for Fury Client (Java Agent JAR targeting Forge 1.8.9)
# ========================================================================

# We target Java 8
-target 1.8

# Don't warn about missing Minecraft / Forge / LWJGL classes (compileOnly)
-dontwarn net.minecraft.**
-dontwarn net.minecraftforge.**
-dontwarn org.lwjgl.**
-dontwarn com.mojang.**
-dontwarn com.google.gson.**
-dontwarn sun.misc.**
-dontwarn javax.**

# Don't optimize – just obfuscate names
-dontoptimize
-dontpreverify
-ignorewarnings

# Keep source file / line info for stack traces
-keepattributes SourceFile,LineNumberTable,Exceptions,InnerClasses,Signature,RuntimeVisibleAnnotations

# ======== KEEP: Java Agent entry points ========
-keep public class lion.client.agent.LionAgent {
    public static void premain(java.lang.String, java.lang.instrument.Instrumentation);
    public static void agentmain(java.lang.String, java.lang.instrument.Instrumentation);
    public static java.lang.instrument.Instrumentation instrumentation();
    public static void installTransformerAndRetransform();
    public static volatile java.lang.ClassLoader MC_CLASSLOADER;
    public static volatile boolean IS_VANILLA;
    *;
}

-keep public class lion.client.Agent {
    public static void main(java.lang.String[]);
    *;
}

# ======== KEEP: Hooks (called by transformed MC bytecode) ========
-keep class lion.client.agent.Hooks { *; }

# ======== KEEP: LionTransformer (ClassFileTransformer) ========
-keep class lion.client.agent.LionTransformer { *; }
-keep class lion.client.agent.LionVanillaTransformer { *; }

# ======== KEEP: Forge mod loading entry / event handlers ========
-keep class lion.client.hook.** { *; }

# ======== KEEP: LionClient singleton (reflection-accessed) ========
-keep class com.lionclient.LionClient { *; }

# ======== KEEP: Module base class ========
-keep class com.lionclient.feature.module.Module { *; }
-keep class com.lionclient.feature.module.Category { *; }

# ======== KEEP: All Module implementations (reflection-registered) ========
-keep class com.lionclient.feature.module.impl.** { *; }

# ======== KEEP: ModuleManager ========
-keep class com.lionclient.feature.module.ModuleManager { *; }

# ======== KEEP: Setting classes (Gson serialization) ========
-keep class com.lionclient.feature.setting.** { *; }

# ======== KEEP: Config system (Gson) ========
-keep class com.lionclient.config.** { *; }

# ======== KEEP: Command system (reflection) ========
-keep class com.lionclient.command.** { *; }

# ======== KEEP: Event system ========
-keep class com.lionclient.event.** { *; }

# ======== KEEP: Network classes ========
-keep class com.lionclient.network.** { *; }

# ======== KEEP: Session ========
-keep class com.lionclient.session.** { *; }

# ======== KEEP: Deobf / Mappings ========
-keep class lion.client.deobf.** { *; }
-keep class lion.client.ClientLogger { *; }

# ======== KEEP: Shaded ASM (used by LionTransformer) ========
-keep class lion.shaded.asm.** { *; }

# ======== KEEP: Enum values() / valueOf() ========
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ======== KEEP: Serializable ========
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Obfuscate utility classes
-repackageclasses 'f'
-allowaccessmodification
-overloadaggressively
