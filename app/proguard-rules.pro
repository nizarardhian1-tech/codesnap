# ProGuard rules untuk code.editor.mon

# ── Keep classes that are accessed via reflection ───────────────────────────

# ViewModel & LiveData
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keepclassmembers class * extends androidx.lifecycle.LiveData { *; }

# Fragment
-keepclassmembers class * extends androidx.fragment.app.Fragment { <init>(); }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# DocumentFile (SAF)
-keep class androidx.documentfile.provider.** { *; }

# Navigation Component
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# Material Components
-keep class com.google.android.material.** { *; }

# ── Remove unused resources (shrinkResources sudah di build.gradle) ─────────

# Optimasi: biarkan ProGuard melakukan obfuscation normal
# Jangan keep seluruh package kecuali benar-benar diperlukan

