# Add project specific ProGuard rules here.

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.stocksense.app.**$$serializer { *; }
-keepclassmembers class com.stocksense.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.stocksense.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Native JNI (llama.cpp)
-keep class com.stocksense.app.engine.LlamaCpp { *; }
