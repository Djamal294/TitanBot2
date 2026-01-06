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
    private EditText linkInput;
    private TextView dashboardView;
    private Handler handler = new Handler();
    private Random random = new Random();
    
    private int visitCounter = 0;
    private int clickCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    
    // قائمة البروكسيات المفحوصة (النظيفة فقط)
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        controlButton = findViewById(R.id.controlButton);
        myBrowser = findViewById(R.id.myBrowser);

        setupGologinSettings();
        startAdvancedProxyHunter();
    }

    private void setupGologinSettings() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // محاكاة بصمة متصفح حقيقي (Gologin Concept)
        String[] userAgents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        };
        s.setUserAgentString(userAgents[random.nextInt(userAgents.length)]);

        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    // سكرول متذبذب (محاكاة بشرية)
                    handler.postDelayed(() -> {
                        int scroll = 200 + random.nextInt(500);
                        myBrowser.loadUrl("javascript:window.scrollBy({top: "+scroll+", behavior: 'smooth'});");
                    }, 3000 + random.nextInt(4000));

                    // نقرات ضئيلة جداً (فرصة 5% فقط) لتجنب كشف أدسنس
                    if (random.nextInt(100) < 5) {
                        handler.postDelayed(() -> {
                            myBrowser.loadUrl("javascript:(function(){ " +
                                "var ads = document.querySelectorAll('iframe, a[href*=\"ad\"]'); " +
                                "if(ads.length > 0) ads[0].click(); " +
                                "})()");
                            clickCounter++;
                            updateUI();
                        }, 15000 + random.nextInt(20000));
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (isBotRunning && request.isForMainFrame()) {
                    // إذا فشل التحميل، انتقل فوراً لبروكسي آخر
                    startNewSession();
                }
            }
        });

        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        
        CookieManager.getInstance().removeAllCookies(null);
        
        if (!VERIFIED_PROXIES.isEmpty()) {
            currentProxy = VERIFIED_PROXIES.remove(0);
            applyProxy(currentProxy);
        }

        String url = linkInput.getText().toString();
        if (!url.startsWith("http")) url = "https://" + url;

        visitCounter++;
        updateUI();
        myBrowser.loadUrl(url);

        // مؤقت متذبذب للزيارة القادمة (بين 50 و 130 ثانية)
        int delay = 50000 + random.nextInt(80000);
        handler.postDelayed(this::startNewSession, delay);
    }

    private void startAdvancedProxyHunter() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (true) {
                try {
                    URL url = new URL("https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt");
                    BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains(":") && VERIFIED_PROXIES.size() < 100) {
                            validateAndAddProxy(line.trim());
                        }
                    }
                } catch (Exception e) {}
                try { Thread.sleep(600000); } catch (InterruptedException e) {}
            }
        });
    }

    // فحص البروكسي قبل استخدامه والتخلص من المعطل
    private void validateAndAddProxy(String proxyAddr) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String[] parts = proxyAddr.split(":");
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
                HttpURLConnection conn = (HttpURLConnection) new URL("https://www.google.com").openConnection(proxy);
                conn.setConnectTimeout(3000);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    VERIFIED_PROXIES.add(proxyAddr);
                    updateUI();
                }
            } catch (Exception e) {
                // البروكسي معطل، لا يتم إضافته (يتخلص منه البوت تلقائياً)
            }
        });
    }

    private void applyProxy(String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyConfig config = new ProxyConfig.Builder().addProxyRule(p).addDirect().build();
            ProxyController.getInstance().setProxyOverride(config, r -> {}, () -> {});
        }
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP TITAN" : "LAUNCH TITAN BOT");
        if (isBotRunning) startNewSession();
        else {
            myBrowser.loadUrl("about:blank");
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void updateUI() {
        runOnUiThread(() -> {
            dashboardView.setText("🛡️ Mode: Gologin Stealth\n📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + 
                                 "\n🌐 Proxy: " + currentProxy + " | Verified Vault: " + VERIFIED_PROXIES.size());
        });
    }
            }
