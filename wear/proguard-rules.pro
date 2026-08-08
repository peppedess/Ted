
# kotlinx.serialization: R8 rimuoverebbe i serializzatori generati,
# e il guasto si vedrebbe solo in release, a runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class it.peppedess.ted.protocol.** {
    *** Companion;
}
-keepclasseswithmembers class it.peppedess.ted.protocol.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class it.peppedess.ted.protocol.**$$serializer { *; }
-keep class it.peppedess.ted.protocol.** { *; }

