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
import android.net.http.SslError;
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
    // === الواجهة ===
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    private LinearLayout webContainer;
    
    // === المحرك الخلفي ===
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(20); 
    private ExecutorService validExec = Executors.newFixedThreadPool(800); 
    
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
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::SmartCookies");

            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            serverCountView = findViewById(R.id.serverCountView);
            linkIn = findViewById(R.id.linkInput);
            controlBtn = findViewById(R.id.controlButton);
            webContainer = findViewById(R.id.webContainer);

            if (webContainer != null) {
                // السماح بالكوكيز ضروري جداً لهذه الخطة
                CookieManager.getInstance().setAcceptCookie(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
                
                web1 = initSmartWeb(); 
                web2 = initSmartWeb(); 
                web3 = initSmartWeb();
                
                setupTripleLayout();
                startMegaScraping(); 
                
                controlBtn.setOnClickListener(v -> toggleSystem());
                aiStatusView.setText("🍪 SMART COOKIES SYSTEM: READY");
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupTripleLayout() {
        if (webContainer == null || web1 == null) return;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    // === المتصفح الذكي (Smart Web) ===
    private WebView initSmartWeb() {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // تفعيل التخزين المحلي لزرع البيانات
        s.setDatabaseEnabled(true);
        
        // إعدادات التخفي والحماية
        s.setAllowFileAccess(false);
        s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadsImagesAutomatically(true);

        wv.setWebViewClient(new WebViewClient() {
            Runnable timeoutRunnable = () -> {
                mHandler.post(() -> aiStatusView.setText("⏳ Timeout -> Resetting..."));
                wv.stopLoading();
                handleFailure(wv, "Timeout");
            };

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mHandler.removeCallbacks(timeoutRunnable);
                mHandler.postDelayed(timeoutRunnable, 35000); 
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                mHandler.removeCallbacks(timeoutRunnable);
                if (url.equals("about:blank")) return;

                // 1. تزوير البصمة (WebGL & Headers)
                injectStealthScripts(v);

                // 2. منطق الإحماء (Warm-up Logic)
                // إذا كنا في صفحة جوجل (الإحماء)، نزرع البيانات ثم نذهب للهدف
                if (url.contains("google.com") || url.contains("bing.com")) {
                    injectFakeHistory(v); // زرع بيانات مزيفة
                    mHandler.postDelayed(() -> {
                         // الانتقال للرابط الأصلي (Target)
                         String targetUrl = linkIn.getText().toString().trim();
                         if(targetUrl.isEmpty()) targetUrl = "https://www.google.com";
                         
                         // إخفاء الهيدر عند الانتقال للهدف
                         Map<String, String> headers = new HashMap<>();
                         headers.put("X-Requested-With", "");
                         headers.put("Referer", "https://www.google.com/"); // خدعة قوية: المصدر هو جوجل
                         v.loadUrl(targetUrl, headers);
                         
                         mHandler.post(() -> aiStatusView.setText("🚀 Moved to Target from Warm-up"));
                    }, 4000); // انتظار 4 ثواني في جوجل لكسب الثقة
                } else {
                    // نحن في صفحة الهدف الآن
                    checkBanStatus(v, url);
                }
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    mHandler.removeCallbacks(timeoutRunnable);
                    handleFailure(v, "Conn Error");
                }
            }
            
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });
        return wv;
    }

    // === حقن "تاريخ مزيف" وكوكيز وهمية (الميزة الجديدة) ===
    private void injectFakeHistory(WebView v) {
        String js = 
            "(function() {" +
            "   try {" +
            "       // زرع بيانات في التخزين المحلي لتبدو كمستخدم قديم\n" +
            "       localStorage.setItem('user_consent', 'true');" +
            "       localStorage.setItem('theme', 'dark');" +
            "       localStorage.setItem('session_id', '" + rnd.nextLong() + "');" +
            "       localStorage.setItem('last_visit', new Date().getTime().toString());" +
            "       // إنشاء كوكيز وهمية آمنة\n" +
            "       document.cookie = 'CONSENT=YES+US.en+202201; path=/; domain=.google.com';" +
            "       document.cookie = 'NID=" + rnd.nextInt(1000) + "=variant; path=/; domain=.google.com';" +
            "   } catch(e) {}" +
            "})();";
        v.evaluateJavascript(js, null);
    }

    private void injectStealthScripts(WebView v) {
        String js = 
            "(function() {" +
            "   try {" +
            "       Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
            "       Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});" +
            "       Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});" +
            "       var getParameter = WebGLRenderingContext.prototype.getParameter;" +
            "       WebGLRenderingContext.prototype.getParameter = function(parameter) {" +
            "           if (parameter === 37445) return 'Intel Inc.';" + 
            "           if (parameter === 37446) return 'Intel(R) Iris(TM) Plus Graphics 640';" + 
            "           return getParameter(parameter);" +
            "       };" +
            "   } catch(e) {}" +
            "})();";
        v.evaluateJavascript(js, null);
    }

    private void checkBanStatus(WebView v, String url) {
        v.evaluateJavascript(
            "(function() { " +
            "   var content = document.body.innerText.toLowerCase(); " +
            "   if (content.includes('anonymous proxy') || content.includes('access denied')) { " +
            "       return 'BLOCKED';" +
            "   } else { " +
            "       return 'OK';" +
            "   } " +
            "})();",
            value -> {
                if (value != null && value.contains("BLOCKED")) {
                    handleFailure(v, "Banned"); 
                } else {
                    v.setTag(0); 
                    simulateHumanBehavior(v);
                    mHandler.post(() -> aiStatusView.setText("🟢 $$$ Success: " + url));
                }
            }
        );
    }

    private void handleFailure(WebView v, String reason) {
        mHandler.post(() -> aiStatusView.setText("⛔ " + reason + " -> Cleaning..."));
        v.stopLoading();
        v.loadUrl("about:blank");
        mHandler.postDelayed(() -> runSingleBot(v), 1500);
    }

    private void simulateHumanBehavior(WebView v) {
        v.evaluateJavascript("(function(){" +
            "   var sc=0; var intr = setInterval(function(){ " +
            "       window.scrollBy(0, 30 + Math.random()*30); " +
            "       sc++; if(sc>50) clearInterval(intr);" +
            "   }, 400);" +
            "   setTimeout(function(){ if(document.body) document.body.click(); }, 3000);" +
            "})()", null);
    }

    private void toggleSystem() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP" : "🚀 START SMART BOT");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 2500);
            mHandler.postDelayed(() -> runSingleBot(web3), 5000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (wv == null) return;
        wv.setTag(0);

        if (!isRunning || PROXY_POOL.isEmpty()) {
            if (isRunning) mHandler.postDelayed(() -> runSingleBot(wv), 3000);
            return;
        }

        try {
            // 1. تنظيف شامل للبقايا القديمة (للبدء بهوية جديدة نظيفة)
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
            wv.clearCache(true);
            wv.clearHistory();

            String proxy = PROXY_POOL.remove(0);
            updateUI();

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                try {
                    ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                        .addProxyRule(proxy).build(), r -> {}, () -> {});
                } catch (Exception e) {}
            }

            // هوية عشوائية
            String[] agents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
            };
            wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
            
            // === التعديل الجوهري: ابدأ بـ Google بدلاً من الرابط المباشر ===
            // هذا ما سيخلق "الكوكيز الحقيقية" قبل الهجوم
            wv.loadUrl("https://www.google.com"); 
            
            totalJumps++;
            // نزيد الوقت قليلاً لأننا نمر بمرحلتين (جوجل + الموقع)
            mHandler.postDelayed(() -> runSingleBot(wv), (40 + rnd.nextInt(20)) * 1000);

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 2000);
        }
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 Proxies: " + PROXY_POOL.size());
            dashView.setText("💰 Hits: " + totalJumps);
        });
    }

    private void startMegaScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=2000&country=all",
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/officialputuid/KangProxy/KangProxy/http/http.txt"
        };

        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        if (PROXY_POOL.size() > 5000) { Thread.sleep(20000); continue; }
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(6000);
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            if (l.contains(":")) validateProxy(l.trim()); 
                        }
                        r.close();
                        Thread.sleep(600000); 
                    } catch (Exception e) {
                        try { Thread.sleep(30000); } catch (Exception ex) {}
                    }
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
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
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
