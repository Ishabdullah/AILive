# AILive ProGuard Rules - Production Obfuscation and Optimization
# Android recommendations + library-specific rules

# ============================================================================
# ANDROID PLATFORM RULES
# ============================================================================

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep activity methods accessed from XML
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================================
# AILIVE APPLICATION RULES
# ============================================================================

# Keep AILive core classes (needed for reflection and JNI)
-keep class com.ailive.core.** { *; }
-keep class com.ailive.ai.** { *; }
-keep class com.ailive.personality.** { *; }

# Keep AI tool classes (registered dynamically)
-keep class * extends com.ailive.personality.tools.AITool { *; }

# Keep model manager (native library interface)
-keep class com.ailive.ai.models.ModelManager { *; }
-keep class com.ailive.ai.llm.LLMManager { *; }

# Keep memory system (Room database entities)
-keep class com.ailive.memory.** { *; }

# Keep settings (accessed via SharedPreferences)
-keep class com.ailive.settings.AISettings { *; }

# ============================================================================
# KOTLIN SPECIFIC RULES
# ============================================================================

# Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# Kotlin serialization (if used)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ============================================================================
# ANDROIDX RULES
# ============================================================================

# Fragment
-keep class androidx.fragment.app.** { *; }
-keep class androidx.fragment.app.Fragment { *; }

# Lifecycle
-keep class androidx.lifecycle.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract <methods>;
}

# CameraX
-keep class androidx.camera.** { *; }

# ============================================================================
# THIRD-PARTY LIBRARY RULES
# ============================================================================

# llama.cpp Android module (JNI)
-keep class de.kherud.llama.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ONNX Runtime (embeddings)
-keep class ai.onnxruntime.** { *; }

# OkHttp + Retrofit (web search)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }

# Moshi JSON (web search parsing)
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.* <methods>;
}

# MPAndroidChart (dashboard visualizations)
-keep class com.github.mikephil.charting.** { *; }

# Google Play Services (location)
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ============================================================================
# CRASHLYTICS (when enabled)
# ============================================================================

# Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# ============================================================================
# OPTIMIZATION SETTINGS
# ============================================================================

# Aggressive optimization for production
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================================
# WARNINGS TO IGNORE
# ============================================================================

# Suppress common library warnings
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
