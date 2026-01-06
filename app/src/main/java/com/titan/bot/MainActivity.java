package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebStorage;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private WebView myBrowser;
    private Button controlButton;
    private EditText linkInput;
    private TextView dashboardView;
    private Handler handler = new Handler();
    private Random random = new Random();
    
    private int visitCounter = 0;
    private int clickCounter = 0;
    private String currentStatus = "Idle";
    private String currentProxy = "Direct";
    private String hunterStatus = "Ready";
    private boolean isBotRunning = false;

    // قائمة البروكسيات الجاهزة
    private CopyOnWriteArrayList<String> READY_TO_USE_PROXIES = new CopyOnWriteArrayList<>();
    
    // --- تحديث: مصادر متنوعة تشمل SOCKS و HTTP ودول مختلفة ---
    private String[] PROXY_SOURCES = {
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks4.txt",
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/all.txt"
    };

    private String[] REFERRER_SOURCES = {
        "https://www.google.com/", "https://www.bing.com/", "https://t.co/", 
        "https://www.facebook.com/", "https://www.youtube.com/"
    };

    private String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/121.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2_1) AppleWebKit/537.36",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UD1A.231105.004)"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        controlButton = findViewById(R.id.controlButton);
        myBrowser = findViewById(R.id.myBrowser);

        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    injectTitanUltimatePatch();
                    autoScrollBehavior();
                    // محاولة الضغط على الأزرار الشائعة
                    myBrowser.loadUrl("javascript:(function(){ " +
                            "var b = document.querySelector('.g-recaptcha, #captcha-submit, .btn-primary, #confirm'); " +
                            "if(b) b.click(); " +
                            "})()");
                    handler.postDelayed(() -> decideAndClick(), 15000);
                }
            }
        });

        controlButton.setOnClickListener(v -> toggleBot());
        startFastProxyHunter();
    }

    // --- ميزة دعم جميع أنواع البروكسيات ---
    private void applyProxy(String proxyStr) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            // التحقق من نوع البروكسي (إذا كان المصدر يوفر البروتوكول، وإلا نعتبره HTTP)
            String proxyUrl = proxyStr;
            if (!proxyUrl.contains("://")) {
                proxyUrl = "http://" + proxyStr; // افتراضي
            }

            ProxyConfig proxyConfig = new ProxyConfig.Builder()
                    .addProxyRule(proxyUrl) 
                    .addProxyRule("https://" + proxyStr)
                    .addProxyRule("socks4://" + proxyStr)
                    .addProxyRule("socks5://" + proxyStr)
                    .addDirect() // العودة للاتصال المباشر في حال فشل البروكسي
                    .build();
            
            ProxyController.getInstance().setProxyOverride(proxyConfig, command -> {}, () -> {});
        }
    }

    private void startNewSession() {
        if (!isBotRunning) return;

        // تنظيف الجلسة
        CookieManager.getInstance().removeAllCookies(null);
        WebStorage.getInstance().deleteAllData();

        // اختيار بروكسي مع ميزة Geo-targeting بسيطة (اختيار عشوائي من المخزن المتنوع)
        if (!READY_TO_USE_PROXIES.isEmpty()) {
            currentProxy = READY_TO_USE_PROXIES.remove(0);
            applyProxy(currentProxy);
        }

        myBrowser.getSettings().setUserAgentString(USER_AGENTS[random.nextInt(USER_AGENTS.length)]);

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", REFERRER_SOURCES[random.nextInt(REFERRER_SOURCES.length)]);

        visitCounter++;
        currentStatus = "🌐 New Session: " + currentProxy;
        updateUI();

        myBrowser.loadUrl(linkInput.getText().toString(), headers);
        
        // توقيت عشوائي بين الزيارات لكسر النمط
        handler.postDelayed(this::startNewSession, 45000 + random.nextInt(90000));
    }

    private void startFastProxyHunter() {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        new Thread(() -> {
            while (true) {
                if (READY_TO_USE_PROXIES.size() < 50) {
                    hunterStatus = "🚀 Hunter Active";
                    updateUI();
                    for (String source : PROXY_SOURCES) {
                        executor.execute(() -> {
                            try {
                                HttpURLConnection c = (HttpURLConnection) new URL(source).openConnection();
                                c.setConnectTimeout(8000);
                                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                                String l;
                                while ((l = r.readLine()) != null) {
                                    l = l.trim();
                                    if (l.contains(":") && !READY_TO_USE_PROXIES.contains(l)) {
                                        // هنا يمكن إضافة فلتر للدول إذا كان المصدر يوفرها (مثل: US, GB)
                                        READY_TO_USE_PROXIES.add(l);
                                    }
                                }
                            } catch (Exception e) {}
                        });
                    }
                }
                try { Thread.sleep(60000); } catch (Exception e) {}
            }
        }).start();
    }

    private void injectTitanUltimatePatch() {
        // إخفاء هوية المتصفح المتقدم
        String js = "javascript:(function() {" +
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});" +
                "window.chrome = { runtime: {} };" +
                "const getParameter = WebGLRenderingContext.getParameter;" +
                "WebGLRenderingContext.prototype.getParameter = function(parameter) {" +
                "if (parameter === 37445) return 'Intel Inc.';" +
                "if (parameter === 37446) return 'Intel(R) Iris(R) Xe Graphics';" +
                "return getParameter(parameter); };" +
                "})()";
        myBrowser.loadUrl(js);
    }

    private void decideAndClick() {
        if (random.nextInt(100) < 7) { 
            myBrowser.loadUrl("javascript:(function(){ " +
                    "var ads = document.querySelectorAll('iframe, ins, a[href*=\"googleads\"], a[href*=\"doubleclick\"], a[href*=\"adservice\"]'); " +
                    "if(ads.length > 0) ads[Math.floor(Math.random()*ads.length)].click(); " +
                    "})()");
            clickCounter++;
            currentStatus = "🎯 Ad Clicked!";
        }
        updateUI();
    }

    private void autoScrollBehavior() {
        // تمرير بطريقة أكثر سلاسة وعشوائية
        handler.postDelayed(() -> {
            int scrollAmount = 300 + random.nextInt(700);
            myBrowser.loadUrl("javascript:window.scrollBy({top: " + scrollAmount + ", behavior: 'smooth'});");
        }, 3000);
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        if (isBotRunning) {
            controlButton.setText("STOP");
            startNewSession();
        } else {
            controlButton.setText("START");
            myBrowser.loadUrl("about:blank");
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(command -> {}, () -> {});
            }
        }
    }

    private void updateUI() {
        runOnUiThread(() -> {
            dashboardView.setText("📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + 
                                 "\n📡 Type: HTTP/SOCKS | 📦 Vault: " + READY_TO_USE_PROXIES.size() +
                                 "\n📍 Current: " + currentProxy);
        });
    }
                          }
