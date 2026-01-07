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
    
    // محرك الجلب والفلترة الفائق (Zenith Threads)
    private ExecutorService scrapExec = Executors.newFixedThreadPool(50); 
    private ExecutorService validExec = Executors.newFixedThreadPool(180); 
    private ExecutorService aiExec = Executors.newSingleThreadExecutor();
    
    private Random rnd = new Random();
    private int successCount = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // تفعيل تسريع العتاد لإظهار الإعلانات فوراً
        getWindow().setFlags(16777216, 16777216); 

        dashView = findViewById(R.id.dashboardView);
        aiStatusView = findViewById(R.id.aiStatusView);
        serverCountView = findViewById(R.id.serverCountView);
        linkIn = findViewById(R.id.linkInput);
        controlBtn = findViewById(R.id.controlButton);
        webContainer = findViewById(R.id.webContainer);

        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
        setupTripleLayout();
        
        startInfinityScraper(); 
        controlBtn.setOnClickListener(v -> toggleZenithEngine());
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
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // تزييف البصمة المتقدم وكسر WebRTC
                v.loadUrl("javascript:(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'deviceMemory',{get:()=>8});" +
                    "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en','de-DE','fr-FR']});" +
                    "window.scrollTo(0, "+rnd.nextInt(300)+");" +
                    "setInterval(function(){ " +
                    "   window.scrollBy(0, "+(rnd.nextBoolean() ? 40 : -10)+");" + // تمرير بشري متذبذب
                    "}, 5000);" +
                    "setTimeout(function(){ document.body.click(); }, 3000);" + // نقرة وهمية لتفعيل الإعلان
                    "})()");

                // مراقب "Anonymous Proxy": الحذف والتبديل الفوري
                v.evaluateJavascript("document.body.innerText.includes('Anonymous Proxy')", value -> {
                    if (Boolean.parseBoolean(value)) mHandler.post(() -> runSingleBot(v));
                });
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // معالجة TIMED_OUT و Connection Failed
                if (isRunning && req.isForMainFrame()) mHandler.post(() -> runSingleBot(v));
            }
        });
        return wv;
    }

    private void toggleZenithEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP ZENITH" : "🚀 LAUNCH ZENITH ELITE");
        if (isRunning) {
            runAIDiagnostic(linkIn.getText().toString());
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
        updateUI();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder().addProxyRule(proxy).build(), r -> {}, () -> {});
        }

        // هوية متصفح حديثة مع Referer مزيف من جوجل
        wv.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.google.com/");
        headers.put("X-Requested-With", "com.android.chrome");

        wv.loadUrl(linkIn.getText().toString().trim(), headers);
        successCount++;
        
        // زمن عشوائي (30-55 ثانية) لضمان احتساب الأرباح
        mHandler.postDelayed(() -> runSingleBot(wv), (30 + rnd.nextInt(26)) * 1000);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 INFINITY POOL: " + PROXY_POOL.size() + " [LIVE]");
            dashView.setText("💰 Zenith Master | Total Jumps: " + successCount);
        });
    }

    private void startInfinityScraper() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=3000&country=all",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://proxyspace.pro/http.txt",
            "https://raw.githubusercontent.com/officialputuid/Proxy-List/master/http.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
            "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { if (l.contains(":")) validateProxy(l.trim()); }
                        Thread.sleep(45000); // تحديث فائق السرعة كل 45 ثانية
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
                c.setConnectTimeout(1000); // فقط البروكسيات الصاروخية لتجنب TIMED_OUT
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }

    private void runAIDiagnostic(String targetUrl) {
        aiExec.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 Chrome/126.0.0.0");
                conn.connect();
                String server = conn.getHeaderField("Server");
                String info = (server != null && server.toLowerCase().contains("cloudflare")) ? 
                    "⚠️ Cloudflare Detected - Forced Stealth" : "✅ Security Analyzed - Launching...";
                mHandler.post(() -> aiStatusView.setText("🤖 AI Intel: " + info));
            } catch (Exception e) {
                mHandler.post(() -> aiStatusView.setText("🤖 AI Intel: Adaptive Stealth Active"));
            }
        });
    }
            }
