# Gson: data models are (de)serialized by field name via reflection.
# 必须用 -keep 而非 -keepclassmembers：R8 full mode 会把"代码从未直接读取"的字段
# （Gson 仅反射读写）当作死字段删除，即使被 keepclassmembers 命中（曾导致登录接口
# 返回 200 但 LoginResponse 全为默认值，界面报"登录失败"）。
# -keep 同时禁止删除与改名；类名仍可被其他规则混淆，体积影响极小。
-keep class top.boluofan.musictv.data.model.** { <fields>; }
-keep class top.boluofan.musictv.data.repository.AuthRepository$LoginErrorBody { <fields>; }
-keep class top.boluofan.musictv.data.storage.ResumeSnapshot { <fields>; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Retrofit: 通过动态代理读取接口方法的泛型返回类型（如 suspend login(): LoginResponse），
# 必须在接口本身（而非字段）上保留方法签名，否则 R8 会移除 ParameterizedType 元信息导致
# "java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType"
-keep,allowobfuscation interface top.boluofan.musictv.data.api.LxUserApi { *; }
-keep,allowobfuscation interface top.boluofan.musictv.data.api.LxMusicApi { *; }
-keep,allowobfuscation interface top.boluofan.musictv.data.api.LxDataApi { *; }

# Kotlin 协程 suspend 函数会被编译为带 Continuation 末尾参数的普通方法，反射桥接需要保留
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.KotlinExtensions
-keep,allowobfuscation,allowshrinking class retrofit2.KotlinExtensions$*

# Retrofit 内部反射工具类
-keep,allowobfuscation,allowshrinking class retrofit2.Platform
-keepclassmembers,allowobfuscation,allowshrinking class retrofit2.Retrofit$* {
    <init>(...);
}