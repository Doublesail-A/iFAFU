#noinspection ShrinkerUnresolvedReference

-dontobfuscate

## iFAFU ##
-keep class cn.ifafu.** {*;}
-keep class cn.woolsen.** {*;}

#noinspection ShrinkerUnresolvedReference
## Glide ##
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}


## Retrofit ##
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions.**
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

#################################
#                               #
#            默认保留            #
#                               #
#################################

# 继承activity,application,service,broadcastReceiver,contentprovider....不进行混淆
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.support.multidex.MultiDexApplication
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep class android.support.** {*;}## 保留support下的所有类及其内部类

###################################
#                                 #
#              Bugly              #
#                                 #
###################################
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}


#################### Gson : Begin ####################
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-keep class sun.misc.Unsafe { *; }
#-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class cn.ifafu.ifafu.domain.bean.dto.** { *; } ##这里包名需要改成自己的java bean包
-keep class cn.ifafu.ifafu.bean.dto.** { *; } ##这里包名需要改成自己的java bean包

#################### Gson : End ####################


#################### umeng : Begin ####################
-dontwarn com.umeng.**
-dontwarn com.taobao.**
-dontwarn anet.channel.**
-dontwarn anetwork.channel.**
-dontwarn org.android.**
-dontwarn org.apache.thrift.**
-dontwarn com.xiaomi.**
-dontwarn com.huawei.**
-dontwarn com.meizu.**

-keepattributes *Annotation*

-keep class com.taobao.** {*;}
-keep class org.android.** {*;}
-keep class anet.channel.** {*;}
-keep class com.umeng.** {*;}
-keep class com.xiaomi.** {*;}
-keep class com.huawei.** {*;}
-keep class com.meizu.** {*;}
-keep class org.apache.thrift.** {*;}

-keep class com.alibaba.sdk.android.**{*;}
-keep class com.ut.**{*;}
-keep class com.ta.**{*;}

-keep public class **.R$*{
   public static final int *;
}
#################### umeng : End ####################

# Optional desktop/server adapters bundled by Fastjson, and optional JVM TLS
# providers probed by OkHttp. These are not Android app dependencies. Keep this
# list explicit so R8 still reports missing classes used by application code.
-dontwarn com.google.common.collect.ArrayListMultimap
-dontwarn com.google.common.collect.Multimap
-dontwarn java.awt.Color
-dontwarn java.awt.Font
-dontwarn java.awt.Point
-dontwarn java.awt.Rectangle
-dontwarn javax.money.CurrencyUnit
-dontwarn javax.money.Monetary
-dontwarn javax.ws.rs.Consumes
-dontwarn javax.ws.rs.Produces
-dontwarn javax.ws.rs.core.Response
-dontwarn javax.ws.rs.core.StreamingOutput
-dontwarn javax.ws.rs.ext.MessageBodyReader
-dontwarn javax.ws.rs.ext.MessageBodyWriter
-dontwarn javax.ws.rs.ext.Provider
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.glassfish.jersey.internal.spi.AutoDiscoverable
-dontwarn org.javamoney.moneta.Money
-dontwarn org.joda.time.DateTime
-dontwarn org.joda.time.DateTimeZone
-dontwarn org.joda.time.Duration
-dontwarn org.joda.time.Instant
-dontwarn org.joda.time.LocalDate
-dontwarn org.joda.time.LocalDateTime
-dontwarn org.joda.time.LocalTime
-dontwarn org.joda.time.Period
-dontwarn org.joda.time.ReadablePartial
-dontwarn org.joda.time.format.DateTimeFormat
-dontwarn org.joda.time.format.DateTimeFormatter
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn springfox.documentation.spring.web.json.Json

# The separate instrumentation APK calls tracing and Kotlin runtime APIs.
# Preserve their public binary interface in the minified target APK.
-keep class androidx.tracing.Trace { public *; }
-keep class kotlin.** { public *; }

# Keep the public schedule interfaces used by external device verification.
-keep class cn.ifafu.ifafu.schedule.** { public *; }
-keep class com.blankj.utilcode.util.SPUtils { public *; }

# PDFBox optional JPEG2000 image codec is not required for calendar text.
-dontwarn com.gemalto.jp2.JP2Decoder

# Public interfaces invoked by the separate release verification APK.
-keep class kotlinx.coroutines.Dispatchers { public *; }
-keep class kotlinx.coroutines.BuildersKt { public *; }
-keep class kotlinx.coroutines.BuildersKt__BuildersKt { public *; }
-keep class androidx.room.Room { public *; }
