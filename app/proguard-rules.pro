# PRD-08: R8/ProGuard 규칙
# MediaPipe tasks-genai — JNI로 native 엔진을 호출하므로 클래스/메서드 이름을 유지한다.
-keep class com.google.mediapipe.tasks.genai.** { *; }
-dontwarn com.google.mediapipe.tasks.genai.**

# kotlinx.coroutines: 내부 상태 유지 (기존 기본 규칙 위에 방어적 추가)
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
