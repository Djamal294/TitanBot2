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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity {
    
    private List<String> ACTIVE_PROXIES = new ArrayList<>();

    // قائمة هواتف حقيقية جداً
    private final String[] USER_AGENTS = {
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36", 
        "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
    };

    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn, proxyInputBox;
    private TextView dashView, aiStatusView;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setContentView(R.layout.activity_main);

            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            linkIn = findViewById(R.id.linkInput);
            proxyInputBox = findViewById(R.id.proxyInputBox);
            controlBtn = findViewById(R.id.controlButton);
            web1 = findViewById(R.id.webview_1);
            web2 = findViewById(R.id.webview_2);
            web3 = findViewById(R.id.webview_3);

            controlBtn.setOnClickListener(v -> toggleSystem());
            CookieManager.getInstance().setAcceptCookie(true);
            
            if(web1 != null) setupDirectWeb(web1);
            if(web2 != null) setupDirectWeb(web2);
            if(web3 != null) setupDirectWeb(web3);

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::V27Direct");
        } catch (Exception e) {}
    }

    private void setupDirectWeb(WebView wv) {
        if (wv == null) return;
        try {
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String url) {
                    injectSuperStealth(v); // تشغيل التخفي
                    
                    if (!url.equals("about:blank")) {
                        if (url.contains("google")) {
                            // لن ننتظر في جوجل، سننتقل فوراً
                            navigateToTarget(v); 
                        } else {
                            mHandler.post(() -> {
                                dashView.setText("💰 Hits: " + (++totalJumps));
                                aiStatusView.setText("⚡ V27 Direct Hit");
                            });
                            simulateHuman(v);
                        }
                    }
                }

                @Override
                public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) {
                        v.loadUrl("about:blank");
                        if (isRunning) mHandler.postDelayed(() -> runSingleBot(v), 1000);
                    }
                }
            });
        } catch (Exception e) {}
    }

    // 🔥 V27: التخفي المباشر بدون إحالة 🔥
    private void injectSuperStealth(WebView v) {
        String js = "javascript:(function() {" +
            // حجب WebRTC بقوة أكبر
            "const rtc = {value: undefined, writable: false, configurable: false};" +
            "try { Object.defineProperty(window, 'RTCPeerConnection', rtc); } catch(e){}" +
            "try { Object.defineProperty(window, 'webkitRTCPeerConnection', rtc); } catch(e){}" +
            
            // إخفاء الـ WebDriver
            "try { Object.defineProperty(navigator, 'webdriver', {get: () => false}); } catch(e){}" +
            
            // تزييف الخصائص لتبدو كإنسان
            "try { Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3]}); } catch(e){}" +
            "try { Object.defineProperty(navigator, 'languages', {get: () => ['en-US']}); } catch(e){}" +
            "})()";
        v.evaluateJavascript(js, null);
    }

    private void navigateToTarget(WebView v) {
        String targetUrl = "";
        if(linkIn != null) targetUrl = linkIn.getText().toString().trim();
        
        // الدخول المباشر (بدون Headers) لتجنب الشكوك
        if(!targetUrl.isEmpty() && v != null) {
             v.loadUrl(targetUrl);
        }
    }

    private void simulateHuman(WebView v) {
        v.evaluateJavascript("(function(){" +
            "   setInterval(function(){ window.scrollBy(0, 20); }, 200);" + // تمرير بطيء
            "   setTimeout(function(){ document.body.click(); }, 3000);" +
            "})()", null);
    }

    private void toggleSystem() {
        if (!isRunning) {
            String rawText = proxyInputBox.getText().toString();
            ACTIVE_PROXIES.clear();
            String[] lines = rawText.split("\n");
            for (String line : lines) {
                String clean = line.trim();
                if (!clean.isEmpty() && clean.contains(":")) {
                    ACTIVE_PROXIES.add(clean);
                }
            }
            if (ACTIVE_PROXIES.isEmpty()) return;
        }

        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP" : "⚡ START V27");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            if (web1 != null) runSingleBot(web1);
            if (web2 != null) mHandler.postDelayed(() -> runSingleBot(web2), 2000);
            if (web3 != null) mHandler.postDelayed(() -> runSingleBot(web3), 4000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (wv == null || !isRunning || ACTIVE_PROXIES.isEmpty()) return;

        try {
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
            wv.clearHistory();
            wv.clearCache(true);

            String proxy = ACTIVE_PROXIES.get(rnd.nextInt(ACTIVE_PROXIES.size()));
            String randomAgent = USER_AGENTS[rnd.nextInt(USER_AGENTS.length)];
            wv.getSettings().setUserAgentString(randomAgent);

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                    .addProxyRule(proxy).build(), r -> {}, () -> {});
            }
            
            // محاولة الدخول المباشر للهدف (تخطي جوجل)
            String targetUrl = linkIn.getText().toString().trim();
            if(targetUrl.isEmpty()) targetUrl = "https://www.google.com";
            
            wv.loadUrl(targetUrl);
            
            mHandler.postDelayed(() -> {
                if(isRunning && wv.getProgress() == 100) runSingleBot(wv);
            }, 30000); 

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 1000);
        }
    }
}
