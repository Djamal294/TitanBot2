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
import android.widget.Toast;
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
    private ExecutorService scrapExec = Executors.newFixedThreadPool(100); 
    private ExecutorService validExec = Executors.newFixedThreadPool(400); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            
            // ربط العناصر بالواجهة
            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            serverCountView = findViewById(R.id.serverCountView);
            linkIn = findViewById(R.id.linkInput);
            controlBtn = findViewById(R.id.controlButton);
            webContainer = findViewById(R.id.webContainer);

            // إعدادات الكوكيز للأرباح
            CookieManager.getInstance().setAcceptCookie(true);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
            }

            // إنشاء المحركات الثلاثة
            web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
            setupTripleLayout();
            
            // بدء جلب البروكسيات فوراً
            startInfinityScraping(); 
            
            controlBtn.setOnClickListener(v -> toggleZenithV5());

        } catch (Exception e) {
            Toast.makeText(this, "Initialization Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // إضافة الذكاء الاصطناعي في الحركة (التمويه البشري)
                v.evaluateJavascript("(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'platform',{get:()=>'Win32'});" +
                    "window.scrollTo(0, "+rnd.nextInt(600)+");" +
                    "setInterval(function(){ window.scrollBy(0, "+(rnd.nextBoolean()?15:-10)+"); }, 4000);" +
                    "})()", null);
                mHandler.post(() -> aiStatusView.setText("🤖 AI Intel: Activity Simulated"));
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (isRunning && req.isForMainFrame()) {
                    mHandler.post(() -> runSingleBot(v)); // تبديل فوري عند الخطأ
                }
            }
        });
        return wv;
    }

    private void toggleZenithV5() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP V5 GHOST" : "🚀 LAUNCH ZENITH V5");
        if (isRunning) {
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 5000);
            mHandler.postDelayed(() -> runSingleBot(web3), 10000);
        }
    }

    private void runSingleBot(WebView wv) {
        if (!isRunning || PROXY_POOL.isEmpty()) return;
        String proxy = PROXY_POOL.remove(0);
        updateUI();

        // نظام البروكسي المطور مع حماية ضد الانهيار (Try-Catch)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                    .addProxyRule(proxy).build(), r -> {}, () -> {});
            } catch (Exception e) {
                mHandler.post(() -> aiStatusView.setText("🤖 AI Intel: Proxy Shield Active"));
            }
        }

        // تمويه كروم وقولوقين (User-Agent Rotation)
        String[] agents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        };
        wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
        
        wv.loadUrl(linkIn.getText().toString().trim());
        totalJumps++;
        
        // القفزة القادمة
        mHandler.postDelayed(() -> runSingleBot(wv), (45 + rnd.nextInt(30)) * 1000);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 V5 POOL: " + PROXY_POOL.size() + " [GHOST]");
            dashView.setText("💰 Master Jumps: " + totalJumps);
        });
    }

    private void startInfinityScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=1500&country=all",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { if (l.contains(":")) validateProxy(l.trim()); }
                        Thread.sleep(60000); 
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
                c.setConnectTimeout(1500);
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a)) { PROXY_POOL.add(a); updateUI(); }
                }
            } catch (Exception e) {}
        });
    }
        }
