# LiteRT creates and throws these from native code (JNI), so R8 must keep their names and constructors.
-keep class com.google.ai.edge.litert.** { *; }
# Play AI delivery is excluded from the build (see build.gradle.kts); only LiteRT's unused ModelProvider refers to it.
-dontwarn com.google.android.play.**
-dontwarn com.google.android.gms.**
# The recognizer uses the classic Interpreter, whose JNI side looks up these classes by name.
-keep class org.tensorflow.lite.** { *; }
