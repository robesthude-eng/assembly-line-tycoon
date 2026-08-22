# ── kotlinx.serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Модели домена сериализуются — сохраняем их сериализаторы.
-keep,includedescriptorclasses class com.example.assemblylinetycoon.**$$serializer { *; }
-keepclassmembers class com.example.assemblylinetycoon.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.assemblylinetycoon.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Yandex Mobile Ads ────────────────────────────────────────────────────────
-keep class com.yandex.mobile.ads.** { *; }
-keep class com.monetization.** { *; }
-dontwarn com.yandex.mobile.ads.**

# ── RuStore Billing ──────────────────────────────────────────────────────────
-keep class ru.rustore.sdk.** { *; }
-dontwarn ru.rustore.sdk.**

# ── Coroutines ───────────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
