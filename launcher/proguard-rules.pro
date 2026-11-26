# mbCAN JNI glue resolves Java symbols by exact names/signatures.
# Without this, release minify crashes at System.loadLibrary (java_class == null).
-keep class com.mengbo.** { *; }

-keepclassmembers enum com.mengbo.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclasseswithmembernames class * {
    native <methods>;
}
