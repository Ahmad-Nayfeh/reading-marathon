# kotlinx.serialization: keep generated serializers for the core model.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.readingmarathon.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.readingmarathon.core.**$$serializer { *; }
# Firestore maps documents through reflection-free Map APIs here, nothing extra to keep.
