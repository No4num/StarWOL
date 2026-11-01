# 保留序列化相关
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# 保持数据模型
-keepclassmembers class com.starchen.wol.data.** { *; }
-keep,includedescriptorclasses class com.starchen.wol.data.**$$serializer { *; }
