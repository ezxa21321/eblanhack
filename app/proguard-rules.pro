# ===========================================================================
# БАЗОВАЯ ОПТИМИЗАЦИЯ И АГРЕССИВНАЯ ОБФУСКАЦИЯ
# ===========================================================================
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-allowaccessmodification
-repackageclasses 'o'
-overloadaggressively

# Удаляем отладочную информацию и строки логов в релизе
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# ===========================================================================
# ЗАЩИТА JNI, NATIVE МЕТОДОВ И ROOT SHELL
# ===========================================================================
# Сохраняем все классы и методы, которые вызываются из C++ (injector.cpp / ptrace_utils.cpp)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Сохраняем RootShell и методы взаимодействия с нативным кодом
-keep class com.eblanhack.loader.RootShell { *; }
-keep class com.eblanhack.loader.MainActivity {
    public <methods>;
    native <methods>;
}

# Защита кастомных коллбэков из JNI (если вызываются через GetMethodID)
-keepclassmembers class com.eblanhack.loader.** {
    @android.webkit.JavascriptInterface <methods>;
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

# ===========================================================================
# ПРАВИЛА ДЛЯ KOTLIN COROUTINES & JETPACK COMPOSE
# ===========================================================================
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Сохраняем стабильность Compose Runtime
-keep class androidx.compose.runtime.** { *; }

# ===========================================================================
# ANDROID СИСТЕМНЫЕ КЛАССЫ
# ===========================================================================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
