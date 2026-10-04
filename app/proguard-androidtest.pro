# Prevent the test APK from shadowing the target APK with an incomplete desugared runtime.
-keep class j$.** { *; }
