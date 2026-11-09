# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.globalinstitute.akademos.**$$serializer { *; }
-keepclassmembers class com.globalinstitute.akademos.** { *** Companion; }
-keepclasseswithmembers class com.globalinstitute.akademos.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.globalinstitute.akademos.data.** { *; }
# Supabase / Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
