# Ported from Store (YAZ/Store at HEAD, app/proguard-rules.pro), itself from Calculator (github.com/213YaZ786/Calculator, app/proguard-rules.pro)
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.yaz.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class com.yaz.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}
