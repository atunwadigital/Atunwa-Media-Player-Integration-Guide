# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:
# kotlinx.serialization models the Atunwa Media SDK uses to parse /sdk/v1 responses.
# See android/docs/getting-started.md#6-code-shrinking.
-keep,includedescriptorclasses class com.atunwadigital.mediasdk.models.**$$serializer { *; }
-keepclassmembers class com.atunwadigital.mediasdk.models.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
