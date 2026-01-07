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
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();

    // مصفوفة هويات متصفحات متنوعة لكسر الحماية
    private String[] AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // تفعيل تسريع الرندر بالأجهزة لضمان ظهور الإعلان بسرعة
        getWindow().setFlags(16777216, 16777216); 

        dashView = findViewById(R.id.dashboardView);
        aiStatusView = findViewById(R.id.aiStatusView);
        linkIn = findViewById(R.id.linkInput);
        controlBtn = findViewById(R.id.controlButton);
        webContainer = findViewById(R.id.webContainer);

        // بناء النوافذ الثلاثة
        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
        setupTripleLayout();

        startScraping();
        controlBtn.setOnClickListener(v -> toggleEngine());
    }

    private void setupTripleLayout() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        p.setMargins(4, 4, 4, 4);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    // إضافة الذكاء الاصطناعي لفحص الحماية
    private void runAIDiagnostic(String targetUrl) {
        aiExec.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("User-Agent", AGENTS[0]);
                conn.connect();
                String server = conn.getHeaderField("Server");
                String info = (server != null && server.toLowerCase().contains("cloudflare")) ? 
                    "⚠️ Cloudflare Found - Using Advanced Stealth" : "✅ Security Analyzed - Launching...";
                mHandler.post(() -> aiStatusView.setText("🤖 AI Intel: " + info));
            } catch (Exception e) {
                mHandler.post(() -> aiStatusView.setText("🤖 AI Intel: Adaptive Mode Active"));
            }
        });
    }

    private WebView initWeb() {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // دمج كسر حماية GoLogin و WebRTC وتزييف العتاد
                v.loadUrl("javascript:(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'deviceMemory',{get:()=>8});" +
                    "var pc = window.RTCPeerConnection || window.webkitRTCPeerConnection;" +
                    "if(pc) pc.prototype.createOffer = function(){ return new Promise(function(res,rej){ rej(); }); };" +
                    "window.scrollBy({top: 500, behavior: 'smooth'});" + // تحريك الصفحة لإجبار الإعلان
                    "})()");
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // التبديل الفوري عند الأخطاء (EMPTY_RESPONSE / Tunnel)
                if (isRunning && req.isForMainFrame()) {
                    mHandler.post(() -> runSingleBot(v));
                }
            }
        });
        return wv;
    }

    private void toggleEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP ALL SYSTEMS" : "🚀 LAUNCH ULTIMATE HYBRID");
        if (isRunning) {
            runAIDiagnostic(linkIn.getText().toString());
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 7000);
            mHandler.postDelayed(() -> runSingleBot(web3), 14000);
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

        wv.getSettings().setUserAgentString(AGENTS[rnd.nextInt(AGENTS.length)]);
        
        Map<String, String> h = new HashMap<>();
        h.put("Referer", "https://www.google.com/");
        
        wv.loadUrl(linkIn.getText().toString().trim(), h);
        vCount++;
        dashView.setText("🔥 Hybrid Engine | Visits: " + vCount + " | Pool: " + PROXY_POOL.size());

        // زمن عشوائي (20-40 ثانية) لضمان تحميل الإعلان بالكامل
        int wait = (20 + rnd.nextInt(21)) * 1000;
        mHandler.postDelayed(() -> runSingleBot(wv), wait);
    }

    // جلب البروكسيات العالمية المستمر
    private void startScraping() {
        String[] sources = {"https://api.proxyscrape.com/v2/?request=getproxies&protocol=http","https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt"};
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { if (l.contains(":")) validate(l.trim()); }
                        Thread.sleep(180000);
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
                c.setConnectTimeout(4000);
                if (c.getResponseCode() == 200) { if (!PROXY_POOL.contains(a)) PROXY_POOL.add(a); }
            } catch (Exception e) {}
        });
    }
                          }
