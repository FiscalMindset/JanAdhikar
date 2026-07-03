# ── JNI bridges: called reflectively from native code, must not be renamed ──
-keep class com.janadhikar.stt.WhisperBridge { *; }
-keep class com.janadhikar.memory.vec.SqliteVecBridge { *; }
-keep class com.janadhikar.llm.LlamaBridge { *; }

# ── MediaPipe GenAI / LiteRT ──
-keep class com.google.mediapipe.** { *; }
-keep class com.google.ai.edge.** { *; }
-dontwarn com.google.mediapipe.**

# ── Room entities are reflection-adjacent via KSP-generated impls ──
-keep class com.janadhikar.memory.model.** { *; }

# ── kotlinx.serialization ──
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.janadhikar.**$$serializer { *; }
-keepclassmembers class com.janadhikar.** {
    *** Companion;
}
