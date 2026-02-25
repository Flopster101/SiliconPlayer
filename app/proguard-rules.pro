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

# SMBJ/HTTP stack pulls in optional JVM/Jakarta classes that are not available
# on Android and are not used on our runtime paths. Keep R8 from failing optimized
# builds on these optional references.
-dontwarn java.rmi.UnmarshalException
-dontwarn javax.annotation.Nullable
-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
