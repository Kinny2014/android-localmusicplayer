# kotlinx.serialization：保留 @Serializable 模型与生成器
-keepattributes *Annotation*

-keepclassmembers class com.kinny.localmusicplayer.model.** {
    *** Companion;
}

-keepclasseswithmembers class com.kinny.localmusicplayer.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-if class com.kinny.localmusicplayer.model.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if class com.kinny.localmusicplayer.model.**$Companion
-keepclassmembers class <1> {
    kotlinx.serialization.KSerializer serializer(...);
}
