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
    // تطوير الخوادم: 1000 خيط فحص لزيادة الكمية فوراً
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
            
            // حل مشكلة "التجمد": ننتظر نصف ثانية لضمان بناء الواجهة قبل تشغيل الكود
            mHandler.postDelayed(() -> {
                try {
                    // إعداد نظام العمل في الخلفية لمنع خمول المعالج
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::Run");

                    // ربط العناصر بالواجهة (XML)
                    dashView = findViewById(R.id.dashboardView);
                    aiStatusView = findViewById(R.id.aiStatusView);
                    serverCountView = findViewById(R.id.serverCountView);
                    linkIn = findViewById(R.id.linkInput);
                    controlBtn = findViewById(R.id.controlButton);
                    webContainer = findViewById(R.id.webContainer);

                    // تفعيل الكوكيز للأرباح
                    CookieManager.getInstance().setAcceptCookie(true);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
                    }

                    // بناء البوتات الثلاثة
                    web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
                    setupTripleLayout();
                    
                    // جلب الخوادم فوراً
                    startMegaScraping(); 
                    
                    controlBtn.setOnClickListener(v -> toggleZenithV5());
                    
                    aiStatusView.setText("🤖 AI Intel: System Ready");
                } catch (Exception e) {
                    Toast.makeText(this, "UI Binding Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 500);

        } catch (Exception e) {
            Toast.makeText(this, "Fatal Init: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupTripleLayout() {
        if (webContainer == null) return;
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
                // ذكاء اصطناعي مطور لكسر حماية المواقع الصعبة
                v.evaluateJavascript("(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'languages',{get:()=>['en-US','en','ar']});" +
                    "window.scrollTo(0, "+rnd.nextInt(800)+");" +
                    "setInterval(function(){ window.scrollBy(0, "+(rnd.nextBoolean()?25:-20)+"); }, 2500);" +
                    "document.body.click();" + 
                    "})()", null);
                mHandler.post(() -> aiStatusView.setText("🤖 AI Intel: Bypass Active"));
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // إذا فشل البروكسي، يسحب بروكسي جديد فوراً
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
            mHandler.postDelayed(() -> runSingleBot(web2), 1000);
            mHandler.postDelayed(() -> runSingleBot(web3), 2000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (!isRunning || PROXY_POOL.isEmpty()) {
            if (isRunning) mHandler.postDelayed(() -> runSingleBot(wv), 2000);
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

        // تمويه Gologin الحديث
        String[] agents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        };
        wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
        
        wv.loadUrl(linkIn.getText().toString().trim());
        totalJumps++;
        
        // قفزات ذكية (كل 30-60 ثانية)
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
                        Thread.sleep(25000); 
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validateProxy(String a) {
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                // فحص البروكسي عبر الموقع المستهدف لضمان كسر الحماية
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
