# Project-specific R8/ProGuard rules.
#
# JNI bridge classes are resolved by exact class/method names from native code
# (both via explicit JNI symbol names and JNI_OnLoad lookups). Do not obfuscate
# or strip these classes/members.

-keep class com.flopster101.siliconplayer.MainActivity { *; }
-keep class com.flopster101.siliconplayer.NativeBridge { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}
