# ProGuard规则文件
# 用于代码和资源压缩优化

# ==================== 基本规则 ====================
# 保持基本Android类
-keep class android.support.** { *; }
-keep class androidx.** { *; }
-dontwarn android.support.**
-dontwarn androidx.**

# ==================== Kotlin相关 ====================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ==================== Gson序列化 ====================
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 保持数据模型类（用于Gson序列化）
-keep class com.example.aifloatingball.model.** { *; }
-keep class com.example.aifloatingball.data.** { *; }

# ==================== WebView相关 ====================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ==================== 反射使用的类 ====================
# 保持通过反射访问的类
-keep class com.example.aifloatingball.SimpleModeActivity { *; }
-keep class com.example.aifloatingball.service.** { *; }
-keep class com.example.aifloatingball.manager.** { *; }

# ==================== 保留R类中的资源ID ====================
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ==================== 保留Parcelable ====================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ==================== 保留枚举 ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== 保留泛型签名 ====================
-keepattributes Signature
-keepattributes Exceptions

# ==================== 保留注解 ====================
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ==================== 第三方库 ====================
# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Lottie
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**


# ==================== 资源压缩保留规则 ====================
# 保留动态加载的资源
-keep class **.R$layout { *; }
-keep class **.R$drawable { *; }
-keep class **.R$mipmap { *; }

# 保留通过反射访问的资源
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ==================== WebView资源 ====================
# 保留WebView需要的资源
-keep class **.R$raw { *; }
-keep class **.R$xml { *; }

# ==================== 混淆优化 ====================
# 启用优化
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# 优化选项
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ==================== 移除日志 ====================
# Release版本移除Log调用
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ==================== 警告处理 ====================
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.**
-dontwarn org.conscrypt.**
-dontwarn javax.servlet.**

# ==================== Cling DLNA 库 ====================
-keep class org.fourthline.cling.** { *; }
-dontwarn org.fourthline.cling.**
-keep class javax.servlet.** { *; }
-keep class org.eclipse.jetty.** { *; }
-dontwarn org.eclipse.jetty.**