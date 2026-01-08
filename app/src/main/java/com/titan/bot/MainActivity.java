package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // === ضع البروكسي المدفوع/التجريبي هنا ===
    // مثال: "192.168.1.1:8080" أو الرابط الذي يعطيه لك الموقع
    private static final String SUPER_PROXY = "PUT_YOUR_PREMIUM_PROXY_HERE:PORT"; 
    // ==========================================

    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            setContentView(R.layout.activity_main);

            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            serverCountView = findViewById(R.id.serverCountView);
            linkIn = findViewById(R.id.linkInput);
            controlBtn = findViewById(R.id.controlButton);

            web1 = findViewById(R.id.webview_1);
            web2 = findViewById(R.id.webview_2);
            web3 = findViewById(R.id.webview_3);

            if (controlBtn != null) {
                controlBtn.setOnClickListener(v -> toggleSystem());
            }

            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
            
            // إعداد المتصفحات على وضع "الدوران"
            if(web1 != null) setupRotatorWebView(web1);
            if(web2 != null) setupRotatorWebView(web2);
            if(web3 != null) setupRotatorWebView(web3);

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::V20Rotator");
            
            // تفعيل البروكسي "السوبر" مرة واحدة للنظام كله
            applySuperProxy();

            aiStatusView.setText("💎 V20: PREMIUM ROTATOR READY");
            serverCountView.setText("🌐 Source: Unlimited Rotation");

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applySuperProxy() {
        // إذا لم يضع المستخدم بروكسي، لا نفعل شيئاً (ننتظر الإدخال اليدوي ربما)
        if (SUPER_PROXY.contains("PUT_YOUR")) return;

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                // إجبار كل المتصفحات على استخدام هذا المدخل الموحد
                ProxyConfig proxyConfig = new ProxyConfig.Builder()
                    .addProxyRule(SUPER_PROXY)
                    .build();
                ProxyController.getInstance().setProxyOverride(proxyConfig, r -> {}, () -> {});
            } catch (Exception e) {
                aiStatusView.setText("Proxy Error: " + e.getMessage());
            }
        }
    }

    private void setupRotatorWebView(WebView wv) {
        if (wv == null) return;
        try {
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setSupportMultipleWindows(false); 
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            
            // تمويه قوي جداً (Samsung Galaxy S24)
            s.setUserAgentString("Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36");
            
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url.startsWith("http")) view.loadUrl(url);
                    return true; 
                }

                @Override
                public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) {
                        // في هذا النظام، الخطأ يعني أن الـ IP الحالي سيء
                        // الحل: إعادة التحميل فوراً ليقوم المزود بتغيير الـ IP
                        v.stopLoading();
                        v.loadUrl("about:blank");
                        if (isRunning) mHandler.postDelayed(() -> runSingleBot(v), 500);
                    }
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    if (url.equals("about:blank")) return;

                    // كشف الحظر والتدوير التلقائي
                    if (url.contains("sorry") || url.contains("captcha")) {
                        mHandler.post(() -> aiStatusView.setText("♻️ Rotating IP..."));
                        v.loadUrl("about:blank");
                        if (isRunning) mHandler.postDelayed(() -> runSingleBot(v), 200);
                        return;
                    }

                    // نجاح
                    injectStealth(v);
                    if (url.contains("google.com")) {
                        injectCookies(v);
                        mHandler.postDelayed(() -> navigateToTarget(v), 1000); 
                    } else {
                        mHandler.post(() -> {
                            totalJumps++;
                            dashView.setText("💰 Hits: " + totalJumps);
                            aiStatusView.setText("✅ SUCCESS (New IP)");
                        });
                        simulateAction(v);
                    }
                }
                
                 @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    handler.proceed(); 
                }
            });

        } catch (Exception e) {}
    }

    private void navigateToTarget(WebView v) {
        String targetUrl = "";
        if(linkIn != null) targetUrl = linkIn.getText().toString().trim();
        
        if(!targetUrl.isEmpty() && v != null) {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Requested-With", ""); 
            headers.put("Referer", "https://www.google.com/");
            v.loadUrl(targetUrl, headers);
        }
    }

    private void injectCookies(WebView v) {
        String js = "(function() { document.cookie = 'CONSENT=YES+US.en+202201; path=/; domain=.google.com'; })();";
        v.evaluateJavascript(js, null);
    }

    private void injectStealth(WebView v) {
        String js = "(function() { try { Object.defineProperty(navigator, 'webdriver', {get: () => undefined}); } catch(e) {} })();";
        v.evaluateJavascript(js, null);
    }

    private void simulateAction(WebView v) {
        v.evaluateJavascript("(function(){" +
            "   setInterval(function(){ window.scrollBy(0, 60); }, 250);" +
            "   setTimeout(function(){ document.body.click(); }, 1500);" +
            "})()", null);
    }

    private void toggleSystem() {
        isRunning = !isRunning;
        if (controlBtn != null) controlBtn.setText(isRunning ? "🛑 STOP" : "🚀 LAUNCH V20");
        
        // إعادة تطبيق البروكسي للتأكد
        if (isRunning) applySuperProxy();
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            if (web1 != null) runSingleBot(web1);
            if (web2 != null) mHandler.postDelayed(() -> runSingleBot(web2), 1200);
            if (web3 != null) mHandler.postDelayed(() -> runSingleBot(web3), 2400);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (wv == null || !isRunning) return;

        try {
            // تنظيف كامل ليبدو كجلسة جديدة تماماً
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
            wv.clearHistory();
            wv.clearCache(true);

            // نطلب جوجل، والبروكسي الخلفي سيعطينا IP جديد تلقائياً
            wv.loadUrl("https://www.google.com");
            
            // التكرار بعد فترة قصيرة
            mHandler.postDelayed(() -> {
                if(isRunning && wv.getProgress() == 100) runSingleBot(wv);
            }, 15000); // دورة كل 15 ثانية

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 500);
        }
    }
                }
