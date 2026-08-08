# TDLib passa per JNI: i nomi non vanno offuscati o il ponte nativo si rompe.
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
