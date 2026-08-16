# kotlinx.serialization keeps its generated serializers via companion objects.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.qui.android.**$$serializer { *; }
-keepclassmembers class dev.qui.android.** {
    *** Companion;
}
-keepclasseswithmembers class dev.qui.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation class retrofit2.Response
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
