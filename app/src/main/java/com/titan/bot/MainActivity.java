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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Switch;
import android.view.ViewGroup;
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
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView;
    private LinearLayout webContainer;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(20); 
    private ExecutorService validExec = Executors.newFixedThreadPool(60); 
    private ExecutorService aiExec = Executors.newSingleThreadExecutor(); 
    
    private Random rnd = new Random();
    private int vCount = 0;
    private boolean isRunning = false;
    private String detectedSecurity = "Analyzing...";
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        dashView = findViewById(R.id.dashboardView);
        aiStatusView = findViewById(R.id.aiStatusView); // أضف هذا الـ TextView في الـ XML
        linkIn = findViewById(R.id.linkInput);
        controlBtn = findViewById(R.id.controlButton);
        webContainer = findViewById(R.id.webContainer);

        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
        setupTripleLayout();

        startScraping();
        controlBtn.setOnClickListener(v -> toggleTripleEngine());
    }

    private void setupTripleLayout() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    // ميزة 1: الذكاء الاصطناعي لفحص حماية الموقع
    private void runAIDiagnostic(String targetUrl) {
        aiExec.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 Chrome/126.0.0.0");
                conn.connect();
                
                String server = conn.getHeaderField("Server");
                if (server != null && server.toLowerCase().contains("cloudflare")) {
                    detectedSecurity = "⚠️ Cloudflare Protection Detected";
                } else if (conn.getResponseCode() == 403) {
                    detectedSecurity = "🚫 High Firewall (403 Forbidden)";
                } else {
                    detectedSecurity = "✅ Standard Protection - All Systems Go";
                }
                
                mHandler.post(() -> aiStatusView.setText("🤖 AI Intel: " + detectedSecurity));
            } catch (Exception e) {
                detectedSecurity = "🛡️ Stealth Mode Forced (Offline Target)";
            }
        });
    }

    private WebView initWeb() {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // ميزة 2: تطبيق "أوامر" الذكاء الاصطناعي لكسر البصمة
                v.loadUrl("javascript:(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'deviceMemory',{get:()=>8});" +
                    "var pc = window.RTCPeerConnection || window.webkitRTCPeerConnection;" +
                    "if(pc) pc.prototype.createOffer = function(){ return new Promise(function(res,rej){ rej(); }); };" +
                    "})()");
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (isRunning && req.isForMainFrame()) {
                    mHandler.post(() -> runSingleBot(v)); // التبديل الفوري عند الكشف
                }
            }
        });
        return wv;
    }

    private void toggleTripleEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP AI SYSTEM" : "🚀 LAUNCH TRIPLE AI");
        if (isRunning) {
            runAIDiagnostic(linkIn.getText().toString()); // تشغيل فحص الذكاء الاصطناعي أولاً
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 5000);
            mHandler.postDelayed(() -> runSingleBot(web3), 10000);
        } else {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    private void runSingleBot(WebView wv) {
        if (!isRunning || PROXY_POOL.isEmpty()) return;

        String proxy = PROXY_POOL.remove(0);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder().addProxyRule(proxy).build(), r -> {}, () -> {});
        }

        // تخصيص هوية مختلفة لكل بوت لكسر الحماية
        wv.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0.0.0 Safari/537.36");
        
        wv.loadUrl(linkIn.getText().toString().trim());
        vCount++;
        dashView.setText("🔥 Triple AI Active | Visits: " + vCount + "\n📦 Pool: " + PROXY_POOL.size());

        // زمن عشوائي (15-40 ثانية)
        int next = (15 + rnd.nextInt(26)) * 1000;
        mHandler.postDelayed(() -> runSingleBot(wv), next);
    }

    // استكمال دوال الجلب والفحص (Scraper & Validator) بنفس القوة السابقة
    private void startScraping() {
        String[] urls = {"https://api.proxyscrape.com/v2/?request=getproxies&protocol=http","https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt"};
        for (String uStr : urls) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(uStr);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { if (l.contains(":")) validate(l.trim()); }
                        Thread.sleep(120000);
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validate(String a) {
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                c.setConnectTimeout(3000);
                if (c.getResponseCode() == 200) { if (!PROXY_POOL.contains(a)) PROXY_POOL.add(a); }
            } catch (Exception e) {}
        });
    }
            }
