# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/josh/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
-keep public class fm.icelink.audioprocessing.** { *; }
-keep public class fm.icelink.openh264.** { *; }
-keep public class fm.icelink.opus.** { *; }
-keep public class fm.icelink.vpx.** { *; }
-keep public class fm.icelink.yuv.** { *; }