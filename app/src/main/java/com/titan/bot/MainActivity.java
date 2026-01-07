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
    private TextView dashView, aiStatusView, serverCountView; // أضفنا عداد الخوادم المنفصل
    private LinearLayout webContainer;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(25); 
    private ExecutorService validExec = Executors.newFixedThreadPool(70); 
    
    private Random rnd = new Random();
    private int vCount = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        getWindow().setFlags(16777216, 16777216); 

        dashView = findViewById(R.id.dashboardView);
        aiStatusView = findViewById(R.id.aiStatusView);
        serverCountView = findViewById(R.id.serverCountView); // يظهر أعلى الشاشة
        linkIn = findViewById(R.id.linkInput);
        controlBtn = findViewById(R.id.controlButton);
        webContainer = findViewById(R.id.webContainer);

        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
        setupTripleLayout();

        startScraping();
        controlBtn.setOnClickListener(v -> toggleUltraEngine());
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
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false); // لتشغيل إعلانات الفيديو تلقائياً
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // ميزة التفاعل الوهمي (Ghost Click) لإجبار الإعلان على الظهور
                v.loadUrl("javascript:(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});" +
                    "window.scrollTo(0, 200);" +
                    "setTimeout(function(){ " +
                    "   var ev = document.createEvent('MouseEvent');" +
                    "   ev.initMouseEvent('click',true,true,window,0,0,0,0,0,false,false,false,false,0,null);" +
                    "   document.body.dispatchEvent(ev);" + // نقرة وهمية لتنشيط السكربت
                    "   window.scrollTo(0, 500);" +
                    "}, 3000);" +
                    "})()");
            }
            
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (isRunning && req.isForMainFrame()) mHandler.post(() -> runSingleBot(v));
            }
        });
        return wv;
    }

    private void toggleUltraEngine() {
        if (PROXY_POOL.size() < 5 && !isRunning) {
            aiStatusView.setText("🤖 AI: Waiting for more proxies...");
            return;
        }
        
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP ATTACK" : "🚀 LAUNCH AD-MAX");
        if (isRunning) {
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 8000);
            mHandler.postDelayed(() -> runSingleBot(web3), 16000);
        }
    }

    private void runSingleBot(WebView wv) {
        if (!isRunning) return;

        // ميزة الإيقاف التلقائي عند نفاذ الخوادم
        if (PROXY_POOL.isEmpty()) {
            isRunning = false;
            controlBtn.setText("🚀 RELOAD PROXIES");
            aiStatusView.setText("⚠️ AI: System Halted - No Proxies Left");
            return;
        }

        String proxy = PROXY_POOL.remove(0);
        updateServerUI();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder().addProxyRule(proxy).build(), r -> {}, () -> {});
        }

        wv.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        
        Map<String, String> h = new HashMap<>();
        h.put("Accept-Language", "en-US,en;q=0.9");
        h.put("Referer", "https://www.bing.com/"); // تغيير المصدر لزيادة الموثوقية
        
        wv.loadUrl(linkIn.getText().toString().trim(), h);
        vCount++;
        dashView.setText("💰 Revenue Bot | Successful Jumps: " + vCount);
        
        // زمن أطول لضمان تفاعل السكربت (35-55 ثانية)
        mHandler.postDelayed(() -> runSingleBot(wv), (35 + rnd.nextInt(21)) * 1000);
    }

    private void updateServerUI() {
        mHandler.post(() -> serverCountView.setText("🌐 PROXY POOL: " + PROXY_POOL.size() + " [ONLINE]"));
    }

    private void startScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=10000&country=all",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt"
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
                c.setConnectTimeout(4000);
                if (c.getResponseCode() == 200) { 
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateServerUI();
                    }
                }
            } catch (Exception e) {}
        });
    }
}
