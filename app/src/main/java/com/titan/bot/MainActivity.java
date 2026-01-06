package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private ExecutorService bgExecutor = Executors.newFixedThreadPool(4); // تحديد عدد المهام لمنع الانهيار
    private Random random = new Random();
    private int visitCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    private String currentCountry = "Waiting...";
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    // ميزة محاكاة كافة الأجهزة (مدمجة)
    private String[] DEVICE_PROFILES = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    };

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

        initSettings();
        startHarvesting(); // يبدأ العمل في الخلفية بهدوء
    }

    private void initSettings() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    // حقن بصمة GoLogin المتطورة
                    myBrowser.loadUrl("javascript:(function(){" +
                        "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                        "Object.defineProperty(navigator,'deviceMemory',{get:()=>8});" +
                        "})()");
                }
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        
        // مسح الكوكيز لضمان زيارة جديدة تماماً
        CookieManager.getInstance().removeAllCookies(null);

        // اختيار البروكسي
        if (proxyModeSwitch.isChecked() && !manualProxyInput.getText().toString().isEmpty()) {
            String[] list = manualProxyInput.getText().toString().split("\n");
            currentProxy = list[random.nextInt(list.length)].trim();
        } else if (!VERIFIED_PROXIES.isEmpty()) {
            currentProxy = VERIFIED_PROXIES.remove(0);
        }

        applyProxySettings(currentProxy);
        updateDashboard();

        // محاكاة الجهاز الشاملة
        String ua = DEVICE_PROFILES[random.nextInt(DEVICE_PROFILES.length)];
        myBrowser.getSettings().setUserAgentString(ua);

        String url = linkInput.getText().toString().trim();
        if (url.isEmpty()) return;
        if (!url.startsWith("http")) url = "https://" + url;

        // تزييف المصدر (Referrer)
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.youtube.com/");

        visitCounter++;
        myBrowser.loadUrl(url, headers);

        // توقيت متذبذب (30-60 ثانية)
        mainHandler.postDelayed(this::startNewSession, 30000 + random.nextInt(30000));
    }

    private void startHarvesting() {
        bgExecutor.execute(() -> {
            while (true) {
                try {
                    // فحص مصدر واحد في المرة الواحدة لضمان الاستقرار
                    URL url = new URL("https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt");
                    BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
                    String l;
                    while ((l = r.readLine()) != null && VERIFIED_PROXIES.size() < 50) {
                        checkProxy(l.trim());
                    }
                    Thread.sleep(60000); // استراحة دقيقة بين دورات الجلب
                } catch (Exception e) {}
            }
        });
    }

    private void checkProxy(String proxyAddr) {
        bgExecutor.execute(() -> {
            try {
                String[] parts = proxyAddr.split(":");
                HttpURLConnection conn = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], Integer.parseInt(parts[1])))
                );
                conn.setConnectTimeout(3000);
                if (conn.getResponseCode() == 200) {
                    VERIFIED_PROXIES.add(proxyAddr);
                    updateDashboard();
                }
            } catch (Exception e) {}
        });
    }

    private void applyProxySettings(String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !p.equals("Direct")) {
            ProxyConfig config = new ProxyConfig.Builder().addProxyRule(p).build();
            ProxyController.getInstance().setProxyOverride(config, r -> {}, () -> {});
        }
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP TITAN" : "START TITAN");
        if (isBotRunning) startNewSession();
        else mainHandler.removeCallbacksAndMessages(null);
    }

    private void updateDashboard() {
        mainHandler.post(() -> {
            dashboardView.setText("🛡️ Mode: Omni-Stealth Stable\n" +
                "📊 Visits: " + visitCounter + "\n" +
                "🌐 Proxy: " + currentProxy + "\n" +
                "📦 Pool: " + VERIFIED_PROXIES.size());
        });
    }
                                      }
