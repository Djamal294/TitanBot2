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
    
    // قائمة ديناميكية (تعبأ من المربع)
    private List<String> ACTIVE_PROXIES = new ArrayList<>();

    // قائمة الأجهزة (للتمويه)
    private final String[] USER_AGENTS = {
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36", 
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; M2101K6G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
    };

    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn, proxyInputBox;
    // تم حذف serverCountView من هنا
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
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            setContentView(R.layout.activity_main);

            // ربط العناصر (تم حذف السطر الذي يسبب المشكلة)
            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            // serverCountView = findViewById(R.id.serverCountView); <--- هذا السطر المحذوف
            
            linkIn = findViewById(R.id.linkInput);
            proxyInputBox = findViewById(R.id.proxyInputBox);
            controlBtn = findViewById(R.id.controlButton);

            web1 = findViewById(R.id.webview_1);
            web2 = findViewById(R.id.webview_2);
            web3 = findViewById(R.id.webview_3);

            controlBtn.setOnClickListener(v -> toggleSystem());

            CookieManager.getInstance().setAcceptCookie(true);
            
            if(web1 != null) setupWeb(web1);
            if(web2 != null) setupWeb(web2);
            if(web3 != null) setupWeb(web3);

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::V23Manual");

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupWeb(WebView wv) {
        if (wv == null) return;
        try {
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setSupportMultipleWindows(false); 
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url.startsWith("http")) view.loadUrl(url);
                    return true; 
                }

                @Override
                public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) {
                        v.stopLoading();
                        v.loadUrl("about:blank");
                        if (isRunning) mHandler.postDelayed(() -> runSingleBot(v), 200);
                    }
                }
                
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                     handler.proceed();
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    if (url.equals("about:blank")) return;

                    if (url.contains("sorry") || url.contains("captcha")) {
                        v.loadUrl("about:blank");
                        if (isRunning) mHandler.postDelayed(() -> runSingleBot(v), 300);
                        return;
                    }

                    injectStealth(v);
                    if (url.contains("google.com")) {
                        injectCookies(v);
                        mHandler.postDelayed(() -> navigateToTarget(v), 1000); 
                    } else {
                        mHandler.post(() -> {
                            totalJumps++;
                            dashView.setText("💰 Hits: " + totalJumps);
                        });
                        simulateAction(v);
                    }
                }
            });
        } catch (Exception e) {}
    }

    private void navigateToTarget(WebView v) {
        String targetUrl = "";
        if(linkIn != null) targetUrl = linkIn.getText().toString().trim();
        if(!targetUrl.isEmpty() && v != null) v.loadUrl(targetUrl);
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
            "   setInterval(function(){ window.scrollBy(0, 50); }, 300);" +
            "   setTimeout(function(){ document.body.click(); }, 2000);" +
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

            if (ACTIVE_PROXIES.isEmpty()) {
                Toast.makeText(this, "⚠️ Please paste proxies first!", Toast.LENGTH_SHORT).show();
                return;
            }

            aiStatusView.setText("✅ Loaded " + ACTIVE_PROXIES.size() + " Proxies");
        }

        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP" : "🚀 START V23");
        
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
            if (wv.getSettings() != null) {
                wv.getSettings().setUserAgentString(randomAgent);
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                    .addProxyRule(proxy).build(), r -> {}, () -> {});
            }
            
            wv.loadUrl("https://www.google.com");
            
            mHandler.postDelayed(() -> {
                if(isRunning && wv.getProgress() == 100) runSingleBot(wv);
            }, 25000); 

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 1000);
        }
    }
                }
                        
