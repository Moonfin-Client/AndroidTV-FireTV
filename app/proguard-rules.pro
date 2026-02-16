# Commonmark (used by Markwon for markdown parsing)
-keep class org.commonmark.** { *; }
-dontwarn org.commonmark.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.jellyfin.androidtv.**$$serializer { *; }
-keepclassmembers class org.jellyfin.androidtv.** {
    *** Companion;
}
-keepclasseswithmembers class org.jellyfin.androidtv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Jellyfin SDK models (uses kotlinx.serialization)
-keep class org.jellyfin.sdk.model.** { *; }
-keep class org.jellyfin.sdk.api.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Koin
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# ACRA
-keep class org.acra.** { *; }
-dontwarn org.acra.**

# Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Markwon (markdown rendering)
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# AndroidX Leanback
-keep class androidx.leanback.** { *; }

# Data binding & view binding
-keep class org.jellyfin.androidtv.databinding.** { *; }

# Keep Compose @Composable functions from being removed
-keep @androidx.compose.runtime.Composable class * { *; }

# Timber
-dontwarn org.jetbrains.annotations.**

# SLF4J
-dontwarn org.slf4j.**

# Enum values (needed for preferences etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# R8 full mode compatibility
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }
-keep class * extends android.app.Service { *; }
