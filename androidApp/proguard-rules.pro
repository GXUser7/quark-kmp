# kotlinx.serialization ships its own rules for generated serializers; these
# keep the settings and cache models, which are only reached reflectively
# through their companions' serializer().
-keepclassmembers @kotlinx.serialization.Serializable class com.quark.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.quark.**$$serializer { *; }

# Ktor and OkHttp reference optional platform pieces that are not on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.ktor.utils.io.jvm.nio.**
-dontwarn reactor.blockhound.**

# SQLDelight's generated queries are plain classes; nothing to keep. Media3 and
# the AndroidX libraries ship consumer rules.
