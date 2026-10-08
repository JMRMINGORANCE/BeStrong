# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.bestrong.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.bestrong.core.**$$serializer { *; }
