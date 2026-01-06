package com.titan.bot;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebStorage;
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

    // مخزن البروكسيات الجاهزة
    private CopyOnWriteArrayList<String> READY_TO_USE_PROXIES = new CopyOnWriteArrayList<>();
    
    // --- إضافة: مصادر متعددة لجلب الخوادم (Proxy Sources) ---
    private String[] PROXY_SOURCES = {
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
        "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
        "https://raw.githubusercontent.com/mmpx12/proxy-list/master/http.txt"
    };

    // --- إضافة: مصادر الزيارات (Referrers) ---
    private String[] REFERRER_SOURCES = {
        "https://www.google.com/", "https://www.bing.com/", "https://t.co/", 
        "https://www.facebook.com/", "https://www.youtube.com/", "https://duckduckgo.com/"
    };

    private String[] USER_AGENTS = {
        "WIN10:::Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36:::Intel Inc.:::Intel(R) Iris(R) Xe Graphics",
        "MAC:::Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Version/17.2 Safari/605.1.15:::Apple Inc.:::Apple M2 GPU",
        "ANDROID:::Mozilla/5.0 (Linux; Android 14; SM-S918B) Chrome/120.0.6099.144 Mobile Safari/537.36:::Qualcomm:::Adreno 740"
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

        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    injectTitanUltimatePatch();
                    autoScrollBehavior();
                    // محاولة حل الكابتشا
                    myBrowser.loadUrl("javascript:(function(){ var b = document.querySelector('.g-recaptcha, #captcha-submit'); if(b) b.click(); })()");
                    handler.postDelayed(() -> decideAndClick(), 15000);
                }
            }
        });

        controlButton.setOnClickListener(v -> toggleBot());
        startFastProxyHunter(); // تشغيل الصياد السريع
    }

    // --- محرك الصياد السريع المتعدد المصادر ---
    private void startFastProxyHunter() {
        ExecutorService executor = Executors.newFixedThreadPool(4); // فحص 4 مصادر في وقت واحد
        new Thread(() -> {
            while (true) {
                if (READY_TO_USE_PROXIES.size() < 20) {
                    hunterStatus = "⚡ Fast Hunting...";
                    updateUI();
                    for (String source : PROXY_SOURCES) {
                        executor.execute(() -> {
                            try {
                                HttpURLConnection c = (HttpURLConnection) new URL(source).openConnection();
                                c.setConnectTimeout(3000);
                                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                                String l;
                                while ((l = r.readLine()) != null) {
                                    if (l.contains(":") && !READY_TO_USE_PROXIES.contains(l)) {
                                        READY_TO_USE_PROXIES.add(l.trim());
                                    }
                                }
                            } catch (Exception e) {}
                        });
                    }
                }
                try { Thread.sleep(30000); } catch (Exception e) {}
            }
        }).start();
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        CookieManager.getInstance().removeAllCookies(null);
        WebStorage.getInstance().deleteAllData();

        if (!READY_TO_USE_PROXIES.isEmpty()) {
            currentProxy = READY_TO_USE_PROXIES.remove(0);
            applyProxy(currentProxy);
        }

        String[] agentData = USER_AGENTS[random.nextInt(USER_AGENTS.length)].split(":::");
        myBrowser.getSettings().setUserAgentString(agentData[1]);

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", REFERRER_SOURCES[random.nextInt(REFERRER_SOURCES.length)]);

        visitCounter++;
        currentStatus = "Surfing...";
        updateUI();
        myBrowser.loadUrl(linkInput.getText().toString(), headers);
        
        handler.postDelayed(this::startNewSession, 60000 + random.nextInt(60000));
    }

    private void injectTitanUltimatePatch() {
        int w = 1280 + random.nextInt(400);
        int h = 720 + random.nextInt(300);
        String js = "javascript:(function() {" +
                "Object.defineProperty(screen, 'width', {get: () => " + w + "});" +
                "Object.defineProperty(screen, 'height', {get: () => " + h + "});" +
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                "})()";
        myBrowser.loadUrl(js);
    }

    private void decideAndClick() {
        if (random.nextInt(100) < 6) { // نسبة النقر 6%
            myBrowser.loadUrl("javascript:(function(){ var ads = document.querySelectorAll('iframe, ins, a[href*=\"googleads\"]'); if(ads.length > 0) ads[Math.floor(Math.random()*ads.length)].click(); })()");
            clickCounter++;
            currentStatus = "🎯 Clicked!";
        }
        updateUI();
    }

    private void applyProxy(String proxyStr) {
        try {
            String[] p = proxyStr.split(":");
            System.setProperty("http.proxyHost", p[0]);
            System.setProperty("http.proxyPort", p[1]);
            System.setProperty("https.proxyHost", p[0]);
            System.setProperty("https.proxyPort", p[1]);
        } catch (Exception e) {}
    }

    private void autoScrollBehavior() {
        handler.postDelayed(() -> myBrowser.scrollBy(0, random.nextInt(500)), 2000);
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        if (isRunning()) {
            controlButton.setText("STOP");
            startNewSession();
        } else {
            controlButton.setText("START");
            myBrowser.loadUrl("about:blank");
        }
    }

    private boolean isRunning() { return isBotRunning; }

    private void updateUI() {
        runOnUiThread(() -> {
            dashboardView.setText("📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + 
                                 "\n🌐 Proxy: " + currentProxy + " | Hunter: " + hunterStatus +
                                 "\n📦 Vault: " + READY_TO_USE_PROXIES.size());
        });
    }
          }
