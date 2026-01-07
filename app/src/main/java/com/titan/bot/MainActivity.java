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
    private TextView dashView, aiStatusView, serverCountView;
    private LinearLayout webContainer;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(25); 
    private ExecutorService validExec = Executors.newFixedThreadPool(80); 
    
    private Random rnd = new Random();
    private int successCount = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(16777216, 16777216); 

        dashView = findViewById(R.id.dashboardView);
        aiStatusView = findViewById(R.id.aiStatusView);
        serverCountView = findViewById(R.id.serverCountView);
        linkIn = findViewById(R.id.linkInput);
        controlBtn = findViewById(R.id.controlButton);
        webContainer = findViewById(R.id.webContainer);

        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
        setupTripleLayout();
        startScraping();
        controlBtn.setOnClickListener(v -> toggleMasterEngine());
    }

    private void setupTripleLayout() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        p.setMargins(2, 2, 2, 2);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    private WebView initWeb() {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // نظام محاكاة اللمس البشري وتصحيح التوقيت
                v.loadUrl("javascript:(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "setInterval(function(){ " +
                    "   var x = Math.floor(Math.random() * window.innerWidth);" +
                    "   var y = Math.floor(Math.random() * window.innerHeight);" +
                    "   var el = document.elementFromPoint(x, y);" +
                    "   if(el) el.click();" + 
                    "}, 4000);" +
                    "window.scrollBy(0, 100);" +
                    "})()");

                // كشف رسائل الحظر آلياً لتطهير البروكسي
                v.evaluateJavascript("document.body.innerText.includes('Anonymous Proxy')", value -> {
                    if (Boolean.parseBoolean(value)) {
                        mHandler.post(() -> runSingleBot(v)); // إعادة التشغيل فوراً ببروكسي جديد
                    }
                });
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (isRunning && req.isForMainFrame()) {
                    mHandler.post(() -> runSingleBot(v));
                }
            }
        });
        return wv;
    }

    private void toggleMasterEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP MASTER" : "🚀 LAUNCH MASTER VIP");
        if (isRunning) {
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 5000);
            mHandler.postDelayed(() -> runSingleBot(web3), 10000);
        }
    }

    private void runSingleBot(WebView wv) {
        if (!isRunning || PROXY_POOL.isEmpty()) return;

        String proxy = PROXY_POOL.remove(0);
        updateServerCount();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder().addProxyRule(proxy).build(), r -> {}, () -> {});
        }

        wv.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        
        Map<String, String> h = new HashMap<>();
        h.put("X-Requested-With", "com.android.chrome");
        
        wv.loadUrl(linkIn.getText().toString().trim(), h);
        successCount++;
        dashView.setText("💰 Master Engine | Jumps: " + successCount);
        
        mHandler.postDelayed(() -> runSingleBot(wv), (35 + rnd.nextInt(25)) * 1000);
    }

    private void updateServerCount() {
        mHandler.post(() -> serverCountView.setText("🌐 PROXY POOL: " + PROXY_POOL.size() + " [ONLINE]"));
    }

    private void startScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=3000&country=US,GB,DE,FR,CA", // التركيز على دول الـ CPM العالي
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
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
                c.setConnectTimeout(2000); // فلترة قاسية جداً لضمان السرعة ومنع TIMED_OUT
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateServerCount();
                    }
                }
            } catch (Exception e) {}
        });
    }
            }
