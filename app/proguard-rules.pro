# =============================================================
# Retrofit 2
# =============================================================
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# Keep Retrofit @GET/@POST etc. annotated interface methods
-keep,allowobfuscation interface * {
    @retrofit2.http.GET <methods>;
    @retrofit2.http.POST <methods>;
    @retrofit2.http.PUT <methods>;
    @retrofit2.http.DELETE <methods>;
    @retrofit2.http.PATCH <methods>;
    @retrofit2.http.HEAD <methods>;
    @retrofit2.http.OPTIONS <methods>;
    @retrofit2.http.HTTP <methods>;
    @retrofit2.http.Path <methods>;
    @retrofit2.http.Query <methods>;
    @retrofit2.http.QueryMap <methods>;
    @retrofit2.http.Body <methods>;
    @retrofit2.http.Header <methods>;
}

# =============================================================
# OkHttp / Okio
# =============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# =============================================================
# Gson
# =============================================================
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Critical: prevents "Class cannot be cast to ParameterizedType" by preserving
# generic type signatures that Gson reads via reflection at runtime
-keep class sun.misc.Unsafe { *; }

# Keep fields annotated with @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# Keep TypeAdapter infrastructure
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# =============================================================
# App data model classes — Gson reflects on ALL fields by name
# =============================================================
-keep class com.baseballnerd.app.data.model.** { *; }
-keepclassmembers class com.baseballnerd.app.data.model.** { *; }

# Keep Kotlin data class generated methods (component1(), copy(), etc.)
-keepclassmembers class com.baseballnerd.app.data.model.** {
    public synthetic <methods>;
    public <init>(...);
}

# Keep the Retrofit service interface itself
-keep interface com.baseballnerd.app.data.api.MlbApiService { *; }

# =============================================================
# Kotlin
# =============================================================
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# =============================================================
# Kotlin Coroutines
# =============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# =============================================================
# Coil (image loading)
# =============================================================
-dontwarn coil.**

# =============================================================
# AndroidX / Material — fixes duplicate class warnings
# =============================================================
-keep class androidx.annotation.experimental.** { *; }
-dontwarn androidx.annotation.experimental.**