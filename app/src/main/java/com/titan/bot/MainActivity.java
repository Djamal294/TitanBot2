package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.*;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
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
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView myBrowser;
    private Button controlButton;
    private EditText linkInput, manualProxyInput;
    private TextView dashboardView;
    private Switch proxyModeSwitch;
    private Handler handler = new Handler();
    private Random random = new Random();
    private int visitCounter = 0, clickCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        manualProxyInput = findViewById(R.id.manualProxyInput);
        proxyModeSwitch = findViewById(R.id.proxyModeSwitch);
        controlButton = findViewById(R.id.controlButton);
        myBrowser = findViewById(R.id.myBrowser);

        setupTitanEngine();
        startProxySystem();
    }

    private void setupTitanEngine() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT); // السماح بالتحميل من الإنترنت دوماً
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // محاكاة متصفح حقيقي متطور
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36");

        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    // سكرول بشري هادئ جداً بعد تحميل الصفحة بـ 10 ثوانٍ
                    handler.postDelayed(() -> {
                        myBrowser.loadUrl("javascript:window.scrollBy({top: 500, behavior: 'smooth'});");
                    }, 10000 + random.nextInt(5000));
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // إذا فشل الاتصال بالإنترنت، انتظر قليلاً ثم جرب بروكسي آخر (Self-Healing)
                if (isBotRunning && request.isForMainFrame()) {
                    handler.postDelayed(() -> startNewSession(), 5000);
                }
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        
        // تنظيف شامل لمنع التتبع الرقمي
        CookieManager.getInstance().removeAllCookies(null);
        WebStorage.getInstance().deleteAllData();

        // منطق البروكسي الهجين (يدوي/تلقائي)
        if (proxyModeSwitch.isChecked() && !manualProxyInput.getText().toString().isEmpty()) {
            String[] list = manualProxyInput.getText().toString().split("\n");
            currentProxy = list[random.nextInt(list.length)].trim();
        } else if (!VERIFIED_PROXIES.isEmpty()) {
            currentProxy = VERIFIED_PROXIES.remove(0);
        } else {
            currentProxy = "Direct (Searching...)";
        }
        
        applyProxy(currentProxy);

        // تصحيح الرابط لمنع تحميل الملفات المحلية الخاطئة
        String url = linkInput.getText().toString().trim();
        if (url.isEmpty() || url.contains("emulated")) {
            url = "https://www.google.com"; // رابط افتراضي إذا كان المدخل خاطئاً
        } else if (!url.startsWith("http")) {
            url = "https://" + url;
        }

        visitCounter++;
        updateUI();
        myBrowser.loadUrl(url);

        // --- تهدئة السرعة: إرسال زيارة واحدة كل 150 إلى 300 ثانية (2.5 - 5 دقائق) ---
        int humanDelay = 150000 + random.nextInt(150000); 
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startNewSession, humanDelay);
    }

    private void applyProxy(String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !p.contains("Direct")) {
            ProxyConfig config = new ProxyConfig.Builder().addProxyRule(p).addDirect().build();
            ProxyController.getInstance().setProxyOverride(config, r -> {}, () -> {});
        }
    }

    private void startProxySystem() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (true) {
                try {
                    // سحب بروكسيات جديدة من مصادر عالمية
                    URL url = new URL("https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt");
                    BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
                    String l;
                    while ((l = r.readLine()) != null) {
                        if (l.contains(":") && VERIFIED_PROXIES.size() < 100) validate(l.trim());
                    }
                    Thread.sleep(600000); // تحديث كل 10 دقائق
                } catch (Exception e) {}
            }
        });
    }

    private void validate(String a) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String[] p = a.split(":");
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])));
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(proxy);
                c.setConnectTimeout(5000); // زيادة المهلة للبروكسيات البعيدة
                if (c.getResponseCode() == 200) {
                    VERIFIED_PROXIES.add(a);
                    updateUI();
                }
            } catch (Exception e) {}
        });
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP TITAN" : "LAUNCH TITAN BOT");
        if (isBotRunning) {
            startNewSession();
        } else {
            myBrowser.loadUrl("about:blank");
            handler.removeCallbacksAndMessages(null);
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(r -> {}, () -> {});
            }
        }
    }

    private void updateUI() {
        runOnUiThread(() -> dashboardView.setText("🛡️ Mode: Gologin Stealth\n📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + "\n🌐 Proxy: " + currentProxy + " | Verified Vault: " + VERIFIED_PROXIES.size()));
    }
            }
