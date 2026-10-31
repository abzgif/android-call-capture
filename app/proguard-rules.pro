# Call Capture Proguard / R8 Optimization Rules

# Preserve Kotlin Annotations & Reflection metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database Persistence Guards (prevent column/table name obfuscation)
-keep class com.callcapture.app.data.db.** { *; }
-keep class com.callcapture.app.data.model.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Jetpack Compose Stability
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
