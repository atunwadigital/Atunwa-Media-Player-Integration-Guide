# kotlinx.serialization models the Atunwa Media SDK uses to parse /sdk/v1 responses.
# See android/docs/getting-started.md#6-code-shrinking.
-keep,includedescriptorclasses class com.atunwadigital.mediasdk.models.**$$serializer { *; }
-keepclassmembers class com.atunwadigital.mediasdk.models.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
