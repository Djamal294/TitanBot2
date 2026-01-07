package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.*;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
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
    private ExecutorService scrapExec = Executors.newFixedThreadPool(200); 
    private ExecutorService validExec = Executors.newFixedThreadPool(1000); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            
            // حل مشكلة الانهيار: ننتظر استقرار الواجهة قبل ربط العناصر
            mHandler.postDelayed(() -> {
                try {
                    // إعداد نظام الخلفية
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::Run");

                    // ربط العناصر
                    dashView = findViewById(R.id.dashboardView);
                    aiStatusView = findViewById(R.id.aiStatusView);
                    serverCountView = findViewById(R.id.serverCountView);
                    linkIn = findViewById(R.id.linkInput);
                    controlBtn = findViewById(R.id.controlButton);
                    webContainer = findViewById(R.id.webContainer);

                    if (webContainer != null) {
                        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
                        setupTripleLayout();
                        startMegaScraping(); 
                        controlBtn.setOnClickListener(v -> toggleZenithV5());
                        aiStatusView.setText("🤖 AI Intel: System Stabilized");
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "UI Binding Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 1000); // تأخير ثانية واحدة لضمان بناء الـ XML بالكامل

        } catch (Exception e) {
            Toast.makeText(this, "Fatal Launch Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupTripleLayout() {
        if (webContainer == null || web1 == null) return;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    private WebView initWeb() {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // ذكاء اصطناعي مطور لكسر حماية المواقع
                v.evaluateJavascript("(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en','ar']});" +
                    "window.scrollTo(0, "+rnd.nextInt(1000)+");" +
                    "setInterval(function(){ window.scrollBy(0, "+(rnd.nextBoolean()?20:-15)+"); }, 3000);" +
                    "document.body.click();" + 
                    "})()", null);
                mHandler.post(() -> aiStatusView.setText("🤖 AI Intel: Traffic Authenticated"));
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

    private void toggleZenithV5() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP V5 GHOST" : "🚀 LAUNCH ZENITH V5");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 1500);
            mHandler.postDelayed(() -> runSingleBot(web3), 3000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (!isRunning || PROXY_POOL.isEmpty() || wv == null) {
            if (isRunning) mHandler.postDelayed(() -> runSingleBot(wv), 3000);
            return;
        }

        String proxy = PROXY_POOL.remove(0);
        updateUI();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                    .addProxyRule(proxy).build(), r -> {}, () -> {});
            } catch (Exception e) {}
        }

        String[] agents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        };
        wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
        
        wv.loadUrl(linkIn.getText().toString().trim());
        totalJumps++;
        
        mHandler.postDelayed(() -> runSingleBot(wv), (30 + rnd.nextInt(30)) * 1000);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 V5 POOL: " + PROXY_POOL.size() + " [MEGA]");
            dashView.setText("💰 Master Jumps: " + totalJumps);
        });
    }

    private void startMegaScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=500",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://proxyspace.pro/http.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
            "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { if (l.contains(":")) validateProxy(l.trim()); }
                        Thread.sleep(20000); 
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validateProxy(String a) {
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                c.setConnectTimeout(400);
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
}
