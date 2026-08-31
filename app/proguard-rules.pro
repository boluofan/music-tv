# ── Gson：数据模型靠反射按字段名反序列化，必须保留 ──
-keep,allowobfuscation class top.boluofan.musictv.data.model.** { <fields>; }
-keep,allowobfuscation class top.boluofan.musictv.data.repository.AuthRepository$LoginErrorBody { <fields>; }
-keep,allowobfuscation class top.boluofan.musictv.data.storage.ResumeSnapshot { <fields>; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
