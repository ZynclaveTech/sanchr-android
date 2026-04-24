# ─────────────────────────────────────────────────────────────────────────────
# Sanchr Android — R8 / ProGuard keep rules
#
# Added in M6 Phase 2 (2026-04-24). `app/build.gradle.kts` already referenced
# this file for the `release` buildType but it did not exist — R8 was running
# with the default-optimize preset only, which strips reflection-heavy paths
# (Hilt, Room, protobuf, libsignal) and produces an APK that crashes at first
# run. This file closes that gap.
#
# Scope decision (2026-04-24): rules for all library modules live here rather
# than as per-module `consumer-rules.pro`. Consumer-rules are needed when a
# library is consumed by an *external* AAR consumer; all our `:core:*`,
# `:domain:*`, and `:feature:*` modules are consumed only by `:app` in the
# same build, so R8 sees the merged classpath at app-build time. If any
# module is ever published externally, migrate its rules into a
# `consumer-rules.pro` inside that module and wire
# `consumerProguardFiles("consumer-rules.pro")` in its `defaultConfig`.
# ─────────────────────────────────────────────────────────────────────────────

# ── Attributes R8 must preserve for reflection/stack-traces ─────────────────
# Generic signatures are required by Retrofit/Moshi/Gson/gRPC-style reflection;
# SourceFile + LineNumberTable keep stacktraces useful in Crashlytics / bug
# reports. Repack source files into a sentinel so the original paths don't
# leak obfuscated-class → source-path mappings.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Androidx @Keep ──────────────────────────────────────────────────────────
-keep @androidx.annotation.Keep class * { *; }
-keep class * {
    @androidx.annotation.Keep *;
}

# ── Application entry point (Hilt) ──────────────────────────────────────────
-keep class com.sanchr.app.SanchrApp { *; }

# ─────────────────────────────────────────────────────────────────────────────
# Hilt / Dagger
# ─────────────────────────────────────────────────────────────────────────────
# Hilt ships its own consumer-rules but a couple of generated patterns need
# extra help when minification is aggressive. @HiltAndroidApp and
# @AndroidEntryPoint classes must survive because the Hilt runtime reaches
# them reflectively via `Hilt_*` generated subclasses. @Inject-annotated
# constructors must keep their argument types so the component graph resolves.
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }

-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembers class * {
    @dagger.assisted.AssistedInject <init>(...);
}

# Hilt-generated components / entry points (Hilt_* prefix convention)
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_HiltComponents { *; }
-keep class **_HiltComponents$* { *; }
-keep class **_MembersInjector { *; }
-keep class **_Factory { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class dagger.hilt.internal.** { *; }

# ─────────────────────────────────────────────────────────────────────────────
# Room
# ─────────────────────────────────────────────────────────────────────────────
# Room's compiler generates `*_Impl` classes that are loaded reflectively from
# the abstract `@Database` class via `Room.databaseBuilder`. Entities and
# type-converters are reached via annotations at runtime when migrations run.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * {
    @androidx.room.TypeConverter *;
}
# Generated _Impl classes live next to their interfaces/abstract classes.
-keep class **_Impl { *; }
# Keep fields on entities — Room serializes via generated code but reflection
# is used by the compiler output for column binding.
-keepclassmembers @androidx.room.Entity class * { <fields>; }

# ─────────────────────────────────────────────────────────────────────────────
# Kotlinx serialization
# ─────────────────────────────────────────────────────────────────────────────
# The official kotlinx-serialization rules cover most cases via
# consumer-rules. Add belt-and-suspenders for @Serializable classes on the
# Sanchr side — sealed-class subtypes and @SerialName-renamed fields are
# reached reflectively via companion `serializer()` methods.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable *;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─────────────────────────────────────────────────────────────────────────────
# Protobuf + gRPC
# ─────────────────────────────────────────────────────────────────────────────
# protobuf-javalite (which ours via protobuf 4.26 is not — we use full
# protobuf-java) reflects over generated message classes to build parsers.
# gRPC-Java generated stubs + gRPC-Kotlin suspend wrappers both need to
# survive; the method-descriptor dispatch is reflection-driven.
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageV3 { *; }
-keep class com.sanchr.proto.** { *; }

-keep class io.grpc.** { *; }
-keep class * extends io.grpc.stub.AbstractStub { *; }
-keep class * extends io.grpc.BindableService { *; }
-keepclassmembers class * extends io.grpc.stub.AbstractStub {
    <init>(...);
}

# gRPC uses ServiceLoader; keep META-INF/services entries implicitly via the
# default rules, but still keep the backing classes.
-keep class io.grpc.netty.shaded.io.netty.** { *; }
-keep class io.grpc.okhttp.** { *; }
-dontwarn io.grpc.netty.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**

# gRPC's JVM-side JNDI DNS resolver (JndiResourceResolverFactory) is shipped
# in the same jar but references `javax.naming.*` which Android does not
# provide. The class is never invoked on Android — NameResolverRegistry
# selects DnsNameResolver or the platform resolver instead. Quieting R8.
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**

# gRPC-OkHttp has an optional fallback path to the legacy OkHttp 2.x API
# (`com.squareup.okhttp.*`). We use OkHttp 4 (`okhttp3.*`) so the legacy
# classes are absent by design.
-dontwarn com.squareup.okhttp.**

# gRPC-Kotlin suspend wrappers expose coroutine-flavored stubs that R8
# otherwise inlines away. Keep the generated Kt stub classes.
-keep class **GrpcKt { *; }
-keep class **GrpcKt$* { *; }

# ─────────────────────────────────────────────────────────────────────────────
# libsignal-client (org.signal.libsignal)
# ─────────────────────────────────────────────────────────────────────────────
# Heavy JNI bridge: `org.signal.libsignal.internal.Native` is invoked from
# C++ via registerNatives, and many classes are allocated by the native side.
# Safest policy: keep the entire package graph and don't warn on internal
# optional paths.
-keep class org.signal.libsignal.** { *; }
-keepclassmembers class org.signal.libsignal.** { *; }
-dontwarn org.signal.libsignal.**

# ─────────────────────────────────────────────────────────────────────────────
# SQLCipher (net.sqlcipher)
# ─────────────────────────────────────────────────────────────────────────────
# Another JNI bridge. Room-SQLCipher integration reflects over the
# SupportOpenHelper factory names.
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }
-dontwarn net.sqlcipher.**
-dontwarn net.zetetic.**

# ─────────────────────────────────────────────────────────────────────────────
# WorkManager
# ─────────────────────────────────────────────────────────────────────────────
# WorkManager instantiates workers reflectively via their (Context, WorkerParameters)
# ctor. Our SyncWorker, PushRefreshWorker, etc. all live under com.sanchr.**.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# Hilt-Worker binds through HiltWorkerFactory; the @HiltWorker annotation
# already flows through Hilt keep-rules above, but be explicit.
-keepclasseswithmembers class * {
    @dagger.hilt.android.qualifiers.ApplicationContext <fields>;
}

# ─────────────────────────────────────────────────────────────────────────────
# Tink (encryption primitives used under crypto/)
# ─────────────────────────────────────────────────────────────────────────────
# Tink registers KeyManagers via ServiceLoader + reflective registry.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ─────────────────────────────────────────────────────────────────────────────
# WebRTC (:core:callengine — not used by the v1 slice but compiled in)
# ─────────────────────────────────────────────────────────────────────────────
# Very heavy JNI surface. Calls isn't exercised by v1 but is linked in.
# Narrow scope: keep native-adjacent packages. If WebRTC bloats the APK
# beyond budget we'll split out `:feature:calls` as a dynamic feature in M7.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# ─────────────────────────────────────────────────────────────────────────────
# OkHttp / Okio (transitive via gRPC-OkHttp + Coil)
# ─────────────────────────────────────────────────────────────────────────────
# Both ship consumer-rules; only quiet transitive warnings about JSR-305
# annotations and Conscrypt/Bouncycastle optional deps.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ─────────────────────────────────────────────────────────────────────────────
# Coroutines / Compose
# ─────────────────────────────────────────────────────────────────────────────
# Both have comprehensive consumer-rules. Leave stub for any Studio-specific
# Compose reflection we may add later.

# ─────────────────────────────────────────────────────────────────────────────
# Sanchr app-specific
# ─────────────────────────────────────────────────────────────────────────────
# Keep sealed-class hierarchies that are serialized or pattern-matched via
# `when` over nullable `-> error(...)` paths where R8's closed-world
# assumption might otherwise collapse branches.
-keep class com.sanchr.core.model.** { *; }
-keep class com.sanchr.core.common.Result { *; }
-keep class com.sanchr.core.common.Result$* { *; }

# DataStore proto schemas + generated Pref wrappers
-keep class androidx.datastore.*.** { *; }

# Kotlin metadata — required by kotlinx-reflect uses (kotlinx-serialization,
# compose-compiler runtime).
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }

# ─────────────────────────────────────────────────────────────────────────────
# Known non-actionable warnings
# ─────────────────────────────────────────────────────────────────────────────
# AGP 8.7.3 bundles an R8 version whose kotlinx-metadata-jvm predates Kotlin
# 2.1.0's metadata format. Each use of a Kotlin 2.1-compiled class produces
# a `WARNING: R8: An error occurred when parsing kotlin metadata` line at
# minification time. R8 falls back to metadata-agnostic optimisation for
# those classes — functionally correct output, just slightly more conservative.
# Fix requires upgrading AGP; tracked as a post-M6 follow-up (see
# `docs/superpowers/plans/2026-04-24-android-m6-hardening.md` self-review).
# Do NOT attempt to silence these by adding `-dontwarn kotlin.Metadata` or
# similar — that would mask real keep-rule gaps.
