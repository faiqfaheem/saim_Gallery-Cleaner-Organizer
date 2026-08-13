# ============================================================================
# APP SPECIFIC & GENERAL RULES
# ============================================================================

# Preserve line numbers and source files for readable stack traces in crash logs
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep ViewBinding classes (prevents R8 from stripping generated binding bindings)
-keep class * implements androidx.viewbinding.ViewBinding { *; }

# Keep Fragment zero-argument constructors used by Navigation Component
-keepclassmembers class * extends androidx.fragment.app.Fragment {
    public <init>();
}

# ============================================================================
# KOTLIN & COROUTINES
# ============================================================================

# Keep Kotlin metadata
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================================================
# COIL & OKHTTP (Image & Video Loading)
# ============================================================================

-keep class coil.** { *; }
-dontwarn coil.**

# OkHttp rules (used internally by Coil)
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ============================================================================
# MPANDROIDCHART (Storage Donut Chart)
# ============================================================================

-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Keep getters and setters needed by chart animations
-keepclassmembers class * extends com.github.mikephil.charting.data.Entry {
    public *;
}

# ============================================================================
# PHOTOVIEW (Image Preview Zooming)
# ============================================================================

-keep class com.github.chrisbanes.photoview.** { *; }
-dontwarn com.github.chrisbanes.photoview.**

# ============================================================================
# INTUIT SDP / SSP (Responsive Dimensions)
# ============================================================================

-keep class com.intuit.sdp.** { *; }
-keep class com.intuit.ssp.** { *; }

# ============================================================================
# ANDROIDX DATASTORE & LIFECYCLE
# ============================================================================

-dontwarn androidx.datastore.**
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}