package com.titan.bot;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Switch;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView myBrowser;
    private Button controlButton;
    private EditText linkInput, manualProxyInput;
    private TextView dashboardView;
    private Switch proxyModeSwitch;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scraperExecutor = Executors.newFixedThreadPool(15); 
    private ExecutorService validatorExecutor = Executors.newFixedThreadPool(50); 
    
    private Random random = new Random();
    private int visitCounter = 0, clickCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct", currentCountry = "Bypassing...";
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    // هوية متصفحات حديثة جداً لتجاوز الحماية
    private String[] CHROME_PROFILES = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // تفعيل تسريع العتاد لظهور الإعلانات بسرعة
        getWindow().setFlags(16777216, 16777216); 

        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        manualProxyInput = findViewById(R.id.manualProxyInput);
        proxyModeSwitch = findViewById(R.id.proxyModeSwitch);
        controlButton = findViewById(R.id.controlButton);
        myBrowser = findViewById(R.id.myBrowser);

        createNotificationChannel(); 
        initHyperSettings();
        startGlobalScraper(); 
    }

    private void initHyperSettings() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE); // لضمان تحميل إعلان جديد كل مرة
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    // نظام تجاوز الحماية العميق (Canvas & WebRTC Bypass)
                    myBrowser.loadUrl("javascript:(function(){" +
                        "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                        "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});" +
                        "var pc = window.RTCPeerConnection || window.webkitRTCPeerConnection;" +
                        "if(pc) pc.prototype.createOffer = function(){ return new Promise(function(res,rej){ rej(); }); };" +
                        "})()");

                    // نقر سريع عشوائي
                    if (random.nextInt(100) < 5) {
                        mainHandler.postDelayed(() -> {
                            myBrowser.loadUrl("javascript:document.querySelector('a, button').click();");
                            clickCounter++;
                        }, 5000);
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // معالجة "صفحة الويب غير متوفرة" عبر إعادة التشغيل الفوري ببروكسي جديد
                if (isBotRunning && request.isForMainFrame()) {
                    mainHandler.post(() -> startNewSession());
                }
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        
        // تنظيف شامل للجلسة السابقة لضمان السرعة
        CookieManager.getInstance().removeAllCookies(null);
        myBrowser.clearCache(true);

        if (proxyModeSwitch.isChecked() && !manualProxyInput.getText().toString().isEmpty()) {
            String[] list = manualProxyInput.getText().toString().split("\n");
            currentProxy = list[random.nextInt(list.length)].trim();
        } else if (!VERIFIED_PROXIES.isEmpty()) {
            currentProxy = VERIFIED_PROXIES.remove(0);
        }

        applyProxySettings(currentProxy);
        fetchGeoInfo(currentProxy);

        String userAgent = CHROME_PROFILES[random.nextInt(CHROME_PROFILES.length)];
        myBrowser.getSettings().setUserAgentString(userAgent);

        String url = linkInput.getText().toString().trim();
        if (url.isEmpty()) return;

        visitCounter++;
        updateDashboard("");

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.google.com/");
        headers.put("Sec-CH-UA", "\"Not/A)Bit\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"");
        myBrowser.loadUrl(url, headers);

        // تقليل الزمن ليكون عشوائياً بين 20 و 35 ثانية
        int randomTime = (20 + random.nextInt(16)) * 1000; 
        mainHandler.postDelayed(this::startNewSession, randomTime);
    }

    // نظام جلب بروكسيات عالمي فائق السرعة
    private void startGlobalScraper() {
        String[] sources = {
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt"
        };
        for (String src : sources) {
            scraperExecutor.execute(() -> {
                while (true) {
                    try {
                        URL url = new URL(src);
                        BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) {
                            if (l.contains(":")) validateProxy(l.trim());
                        }
                        Thread.sleep(120000);
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validateProxy(String addr) {
        validatorExecutor.execute(() -> {
            try {
                String[] p = addr.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                c.setConnectTimeout(3000); // تقليل وقت الانتظار للبروكسيات البطيئة
                if (c.getResponseCode() == 200) {
                    if (!VERIFIED_PROXIES.contains(addr)) {
                        VERIFIED_PROXIES.add(addr);
                        updateDashboard("");
                    }
                }
            } catch (Exception e) {}
        });
    }

    private void updateDashboard(String msg) {
        mainHandler.post(() -> {
            dashboardView.setText("🚀 Mode: Hyper-Speed Bypass\n📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + 
                "\n🌍 Geo: " + currentCountry + "\n🌐 Proxy: " + currentProxy + "\n📦 Pool: " + VERIFIED_PROXIES.size());
        });
    }

    private void fetchGeoInfo(String p) {
        if (p.equals("Direct")) return;
        scraperExecutor.execute(() -> {
            try {
                JSONObject j = new JSONObject(new BufferedReader(new InputStreamReader(new URL("http://ip-api.com/json/"+p.split(":")[0]).openStream())).readLine());
                currentCountry = j.optString("country", "Analyzing") + " 🌍";
                updateDashboard("");
            } catch (Exception e) {}
        });
    }

    private void applyProxySettings(String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !p.equals("Direct")) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder().addProxyRule(p).build(), r -> {}, () -> {});
        }
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP HYPER" : "LAUNCH HYPER SPEED");
        if (isBotRunning) startNewSession();
        else mainHandler.removeCallbacksAndMessages(null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("BOT_CHANNEL", "Titan Bot Service", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
