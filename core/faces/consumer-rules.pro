# LiteRT creates and throws these from native code (JNI), so R8 must keep their names and constructors.
-keep class com.google.ai.edge.litert.** { *; }
