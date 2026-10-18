# Keep the JavaScript bridge methods if you ever turn on minification.
-keepclassmembers class com.tfishapps.roborunner.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
