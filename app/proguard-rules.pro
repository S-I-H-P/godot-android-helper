# Godot 开发助手 - ProGuard 规则

# 保留原生库方法
-keep class com.godot.devassistant.native.** { *; }

# 保留数据模型
-keep class com.godot.devassistant.model.** { *; }

# 保留 Gson 类型标记
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# 保留 ViewBinding
-keep class com.godot.devassistant.databinding.** { *; }
