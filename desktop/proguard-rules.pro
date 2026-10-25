# Wave for computers: shrink only what's safe to shrink. Everything is kept as-is except Material's
# extended icon set (tens of MB), of which Wave uses a few dozen icons.
-dontobfuscate
-dontoptimize
-dontwarn **
-dontnote **
-keep class !androidx.compose.material.icons.**,** { *; }
-keepattributes *
