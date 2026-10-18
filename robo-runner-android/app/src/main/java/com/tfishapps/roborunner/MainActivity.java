package com.tfishapps.roborunner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Robo Runner for Android: a full-screen WebView that runs the game from app/src/main/assets/index.html.
 *
 * The game is served from https://appassets.androidplatform.net/assets/ (WebViewAssetLoader) rather than
 * file://, so it gets a real https origin: saves (localStorage, IndexedDB) persist between launches and
 * Firebase online levels work the same as in a browser.
 */
public class MainActivity extends Activity {

    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + ASSET_HOST + "/assets/index.html";
    private static final int REQUEST_FILE = 1001;
    private static final int REQUEST_SAVE = 1002;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private OnBackInvokedCallback backCallback;
    private String pendingSaveText;   // level JSON waiting for the player to pick where to save it

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Debug builds can be inspected from desktop Chrome at chrome://inspect
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        web = new WebView(this);
        web.setBackgroundColor(0xFF120F33);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // The WebView sits in a frame padded away from the camera cutout (Android 15+ always lays windows out
        // under it) and, on Android 11+, from the on-screen keyboard, so nothing is hidden behind either.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF120F33);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            applySafeInsets(v, insets);
            return consumed(insets);   // handled here; the WebView shouldn't pad again
        });
        setContentView(root);
        hideSystemBars();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                    // localStorage: progress, coins, your levels
        s.setMediaPlaybackRequiresUserGesture(false);    // level music
        s.setTextZoom(100);                              // ignore the system font size; the game sizes its own text
        s.setAllowFileAccess(false);                     // everything comes through the asset loader

        final WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                WebResourceResponse res = assets.shouldInterceptRequest(url);
                if (res == null && ASSET_HOST.equals(url.getHost())) {
                    // the app's own address never goes to the real internet: anything not in assets/ is "not found"
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                            new HashMap<>(), new ByteArrayInputStream(new byte[0]));
                }
                return res;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (ASSET_HOST.equals(url.getHost())) return false;   // the game itself
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url)); // any other link opens in the browser
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // <input type="file">: picking an MP3 for level music, or a level JSON to import
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(fileIntent(params.getAcceptTypes()), REQUEST_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        web.addJavascriptInterface(new Bridge(), "RoboAndroid");

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
            pendingSaveText = savedInstanceState.getString("pendingSaveText");   // survives Android closing the app during "save as"
        }
        if (web.getUrl() == null) web.loadUrl(START_URL);

        // Back button / back gesture. Android 13+ uses this callback; older versions call onBackPressed().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    /** Ask the game to handle back (close a dialog, leave a level, go up a menu). On the home screen, leave the app. */
    private void handleBack() {
        web.evaluateJavascript("(window.roboBack && window.roboBack()) ? 'handled' : 'exit'", value -> {
            if (value == null || !value.contains("handled")) finish();
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    @SuppressWarnings("deprecation")
    private static WindowInsets consumed(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return WindowInsets.CONSUMED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return insets.consumeSystemWindowInsets().consumeDisplayCutout();
        return insets.consumeSystemWindowInsets();
    }

    /** Pads the game away from the camera cutout and (Android 11+) the keyboard. */
    private static void applySafeInsets(View v, WindowInsets insets) {
        int left = 0, top = 0, right = 0, bottom = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Insets cut = insets.getInsets(WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
            left = cut.left;
            top = cut.top;
            right = cut.right;
            bottom = Math.max(cut.bottom, ime.bottom);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout dc = insets.getDisplayCutout();
            if (dc != null) {
                left = dc.getSafeInsetLeft();
                top = dc.getSafeInsetTop();
                right = dc.getSafeInsetRight();
                bottom = dc.getSafeInsetBottom();
            }
        }
        v.setPadding(left, top, right, bottom);
    }

    /** Builds the file picker intent from the input's accept list, e.g. ".mp3,audio/mpeg,audio/*" or ".json,application/json". */
    private static Intent fileIntent(String[] acceptTypes) {
        List<String> mimes = new ArrayList<>();
        if (acceptTypes != null) {
            for (String entry : acceptTypes) {
                if (entry == null) continue;
                for (String part : entry.split(",")) {
                    String t = part.trim().toLowerCase(Locale.ROOT);
                    if (t.isEmpty()) continue;
                    if (t.contains("/")) addOnce(mimes, t);
                    else if (t.equals(".mp3")) addOnce(mimes, "audio/mpeg");
                    else if (t.equals(".json")) {
                        addOnce(mimes, "application/json");
                        addOnce(mimes, "text/plain");
                        addOnce(mimes, "application/octet-stream");   // many file managers label .json like this
                    }
                }
            }
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (!mimes.isEmpty()) intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
        return intent;
    }

    private static void addOnce(List<String> list, String value) {
        if (!list.contains(value)) list.add(value);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SAVE) {
            String text = pendingSaveText;
            pendingSaveText = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                if (text == null) {
                    Toast.makeText(this, "Couldn't save the file, please try again", Toast.LENGTH_LONG).show();
                    return;
                }
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (out == null) throw new IOException("no output stream");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                } catch (IOException | SecurityException e) {
                    Toast.makeText(this, "Couldn't save the file", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        if (requestCode == REQUEST_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** Full screen: hide the status and navigation bars (swipe from an edge to show them briefly). */
    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onPause() {
        // stop music and freeze the level for a moment so returning to the app isn't an instant death
        web.evaluateJavascript("window.roboPause && window.roboPause()", null);
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        hideSystemBars();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
        if (pendingSaveText != null) outState.putString("pendingSaveText", pendingSaveText);
    }

    @Override
    protected void onDestroy() {
        if (backCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        web.destroy();
        super.onDestroy();
    }

    /** Things the game can ask the app to do (window.RoboAndroid in JavaScript). */
    public class Bridge {
        /** "download" in the level JSON box: the system "save as" screen (Downloads, Drive, ...) writes a real .json file. */
        @JavascriptInterface
        public void saveFile(final String fileName, final String text) {
            runOnUiThread(() -> {
                pendingSaveText = text;
                Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.addCategory(Intent.CATEGORY_OPENABLE);
                save.setType("application/json");
                save.putExtra(Intent.EXTRA_TITLE, fileName);
                try {
                    startActivityForResult(save, REQUEST_SAVE);
                } catch (ActivityNotFoundException e) {
                    pendingSaveText = null;
                    shareText(fileName, text);   // no file picker on this device: offer the share sheet instead
                }
            });
        }

        /** Fallback: open the share sheet with the level as text (email, chat, notes...). */
        @JavascriptInterface
        public void shareText(final String fileName, final String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, fileName);
                send.putExtra(Intent.EXTRA_TITLE, fileName);
                send.putExtra(Intent.EXTRA_TEXT, text);
                try {
                    startActivity(Intent.createChooser(send, "Save or send " + fileName));
                } catch (ActivityNotFoundException ignored) {
                }
            });
        }
    }
}
