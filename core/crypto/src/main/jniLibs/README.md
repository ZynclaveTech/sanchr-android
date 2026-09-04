# Vendored native libraries

`libsanchr_psi_jni.so` is built from the backend's `crates/sanchr-psi-jni` and
vendored here per ABI, the same arrangement as `ios/Sanchr-iOS/Vendor/SanchrPSI`.
The Gradle build needs no Rust toolchain.

Regenerate from the backend checkout (see that crate's README for prerequisites):

```sh
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 \
  -o ../android/sanchr-android/core/crypto/src/main/jniLibs \
  build -p sanchr-psi-jni --release
```

Regenerate **together with** the golden vectors copied into
`src/test/resources/oprf_vectors.json` and `src/androidTest/assets/oprf_vectors.json`
— the binary and the vectors describe the same protocol version, and
`OprfNativeTest` will fail on-device if they disagree.
