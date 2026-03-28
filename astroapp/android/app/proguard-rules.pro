# ProGuard rules for Astroluna
# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep our models
-keep class com.astroluna.data.model.** { *; }

# Keep Retrofit and Gson
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okio.**
-dontwarn javax.annotation.**

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Socket.IO
-keep class io.socket.** { *; }
-keep class okhttp3.** { *; }
-dontwarn io.socket.**

# WebRTC (Stream SDK)
-keep class org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
-dontwarn org.webrtc.**
-dontwarn io.getstream.webrtc.**

# JNI
-keepclasseswithmembernames class * {
    native <methods>;
}
