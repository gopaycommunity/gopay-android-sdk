# R8 rules for apps that consume the GoPay SDK. AGP adds them to the host's own release build.
#
# The SDK reads and writes JSON through Moshi's KotlinJsonAdapterFactory, which works by
# reflection: it matches constructor parameters to properties by their Kotlin names and reads
# @Json from the members. With minification on (R8 full mode is the AGP 8 default) the models are
# renamed and their metadata trimmed, and every gateway response, the payment token included,
# fails to decode, while the JWE payload silently serializes to nothing. A release build of the
# demo app with minification confirmed all of it.

# Every model the gateway, Google Pay or the JWE reads or writes, with its members, Kotlin
# metadata and enum constants.
-keep class cz.gopay.sdk.model.** { *; }
-keep class cz.gopay.sdk.modules.network.TokenResponse { *; }
-keep class cz.gopay.sdk.service.GooglePayHelper$* { *; }
-keep class kotlin.Metadata { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Retrofit 2.9 builds the SDK's endpoints from the generic signatures of their suspend methods.
# Full mode strips the types those signatures name unless they are kept, and every call then
# fails with a ClassCastException. Retrofit 2.9 already keeps the service interfaces and the
# attributes; it ships these three rules itself only from 2.10 on.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
