# Wave's shrinking rules (used by the "fast" build, and later 1.0's release build).
#
# Shrinking and optimizing is where the speed comes from; renaming classes buys almost nothing
# and makes crash reports unreadable, so it's off.
-dontobfuscate
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable, RuntimeVisible*Annotations

# YouTube Music client (Metrolist innertube + InnerTubeX): JSON models and stream extraction,
# some of it reflective. Kept whole: correctness over a few hundred KB.
-keep class com.metrolist.** { *; }
-dontwarn com.metrolist.**

# kotlinx.serialization
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }

# Ktor finds its engine and plugins through ServiceLoader and reflection.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# QuickJS (JavaScript for stream deciphering) calls back into Kotlin from native code.
-keep class com.dokar.quickjs.** { *; }
-dontwarn com.dokar.quickjs.**

# PO token WebView bridge: methods called from JavaScript.
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keep class app.wave.music.playback.potoken.** { *; }

-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
