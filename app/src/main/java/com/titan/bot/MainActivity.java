package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import android.view.View;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // === الواجهة ===
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    
    // === المحركات ===
    private Handler mHandler = new Handler(Looper.getMainLooper());
    // زدنا عدد خيوط البحث للتعامل مع المصادر الضخمة
    private ExecutorService scrapExec = Executors.newFixedThreadPool(100); 
    private ExecutorService validExec = Executors.newFixedThreadPool(2000); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    
    // === البيانات ===
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    private Set<String> BLACKLIST = Collections.synchronizedSet(new HashSet<>());
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // تفعيل أقصى أداء للهاتف
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            setContentView(R.layout.activity_main);
            
            // تشغيل محرك البحث المطور (V17)
            startAdvancedScraping(); 

            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            serverCountView = findViewById(R.id.serverCountView);
            linkIn = findViewById(R.id.linkInput);
            controlBtn = findViewById(R.id.controlButton);

            web1 = findViewById(R.id.webview_1);
            web2 = findViewById(R.id.webview_2);
            web3 = findViewById(R.id.webview_3);

            if (controlBtn != null) {
                controlBtn.setOnClickListener(v -> toggleSystem());
            }

            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
            
            if(web1 != null) setupSniperWebView(web1);
            if(web2 != null) setupSniperWebView(web2);
            if(web3 != null) setupSniperWebView(web3);

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::V17Sniper");
            
            aiStatusView.setText("🔥 V17: HIGH-QUALITY ENGINE STARTED");

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupSniperWebView(WebView wv) {
        if (wv == null) return;
        try {
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            // تقليل استهلاك البيانات لتسريع التحميل
            s.setBlockNetworkImage(false); 
            s.setLoadsImagesAutomatically(true); 
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            
            // منع النوافذ المنبثقة
            s.setSupportMultipleWindows(false);
            
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return false; // إجبار الرابط داخل التطبيق
                }

                @Override
                public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) {
                        // إذا فشل الخادم، احظره فوراً وانتقل للتالي
                        String proxy = (String) v.getTag();
                        if (proxy != null) BLACKLIST.add(proxy);
                        
                        v.loadUrl("about:blank");
                        if (isRunning) mHandler.postDelayed(() -> runSingleBot(v), 100); // انتقال فوري
                    }
                }
                
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    handler.proceed(); 
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    if (url.equals("about:blank")) return;

                    injectAntiFingerprint(v);

                    if (url.contains("google.com")) {
                        injectGoogleCookies(v);
                        // الانتقال للرابط الهدف بسرعة
                        mHandler.postDelayed(() -> navigateToTarget(v), 1000); 
                    } else {
                        // وصلنا للهدف
                        mHandler.post(() -> aiStatusView.setText("✅ HIT: " + PROXY_POOL.size() + " IPs Left"));
                        simulateInteraction(v);
                    }
                }
            });

        } catch (Exception e) {}
    }

    private void navigateToTarget(WebView v) {
        String targetUrl = "";
        if(linkIn != null) targetUrl = linkIn.getText().toString().trim();
        
        if(!targetUrl.isEmpty()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Requested-With", ""); 
            headers.put("Referer", "https://www.google.com/");
            if (v != null) v.loadUrl(targetUrl, headers);
        }
    }

    // === تقنيات التخفي ===
    private void injectGoogleCookies(WebView v) {
        String js = "(function() { document.cookie = 'CONSENT=YES+US.en+202201; path=/; domain=.google.com'; })();";
        v.evaluateJavascript(js, null);
    }

    private void injectAntiFingerprint(WebView v) {
        // تمويه WebGL و Canvas ليبدو كهاتف حقيقي مختلف في كل مرة
        String js = 
            "(function() {" +
            "   try {" +
            "       Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
            "       var noise = Math.floor(Math.random() * 100);" +
            "       var getParameter = WebGLRenderingContext.prototype.getParameter;" +
            "       WebGLRenderingContext.prototype.getParameter = function(parameter) {" +
            "           if (parameter === 37445) return 'Google Inc.';" + // Unmasked Vendor
            "           if (parameter === 37446) return 'Google SwiftShader';" + // Unmasked Renderer
            "           return getParameter(parameter);" +
            "       };" +
            "   } catch(e) {}" +
            "})();";
        v.evaluateJavascript(js, null);
    }

    private void simulateInteraction(WebView v) {
        v.evaluateJavascript("(function(){" +
            "   setInterval(function(){ window.scrollBy(0, 50); }, 200);" +
            "   setTimeout(function(){ document.body.click(); }, 2000);" +
            "})()", null);
    }

    private void toggleSystem() {
        isRunning = !isRunning;
        if (controlBtn != null) controlBtn.setText(isRunning ? "🛑 STOP" : "🚀 LAUNCH V17");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            if (web1 != null) runSingleBot(web1);
            if (web2 != null) mHandler.postDelayed(() -> runSingleBot(web2), 1000);
            if (web3 != null) mHandler.postDelayed(() -> runSingleBot(web3), 2000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (wv == null || !isRunning) return;
        
        if (PROXY_POOL.isEmpty()) {
            mHandler.postDelayed(() -> runSingleBot(wv), 2000);
            return;
        }

        try {
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
            wv.clearHistory();

            // اختيار عشوائي ذكي
            int index = rnd.nextInt(PROXY_POOL.size());
            String proxy = PROXY_POOL.get(index);

            // تحقق سريع من القائمة السوداء
            if (BLACKLIST.contains(proxy)) {
                PROXY_POOL.remove(index);
                runSingleBot(wv);
                return;
            }

            wv.setTag(proxy);
            updateUI();

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                try {
                    ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                        .addProxyRule(proxy).build(), r -> {}, () -> {});
                } catch (Exception e) {}
            }
            
            if (wv.getSettings() != null) {
                // تدوير User-Agent ليبدو كأجهزة مختلفة
                String[] agents = {
                    "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                    "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
                };
                wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
                wv.loadUrl("https://www.google.com"); 
            }
            
            totalJumps++;
            // تقليل وقت الانتظار لزيادة عدد الزيارات
            mHandler.postDelayed(() -> runSingleBot(wv), (20 + rnd.nextInt(10)) * 1000);

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 500);
        }
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("💎 Elite IPs: " + PROXY_POOL.size());
            dashView.setText("⚡ Jumps: " + totalJumps);
        });
    }

    // === محرك البحث المتقدم (Advanced Scraping Engine) ===
    private void startAdvancedScraping() {
        // مصادر API متنوعة (HTTP, SOCKS4, SOCKS5) لضمان عدم التكرار
        String[] sources = {
            // Proxyscrape API (High Volume)
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=5000&country=all&ssl=all&anonymity=elite",
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=socks4&timeout=5000&country=all",
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=socks5&timeout=5000&country=all",
            // Geonode Free List (Quality)
            "https://proxylist.geonode.com/api/proxy-list?limit=500&page=1&sort_by=lastChecked&sort_type=desc&protocols=http%2Chttps",
            // GitHub Raw Lists (The Huge Ones)
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks4.txt",
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt",
            "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt",
            "https://raw.githubusercontent.com/Zaeem20/FREE_PROXIES_LIST/master/http.txt",
            "https://raw.githubusercontent.com/Anonym0usWork1220/Free-Proxies/main/proxy_files/http_proxies.txt"
        };

        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        // تنظيف الذاكرة إذا امتلأت
                        if (PROXY_POOL.size() > 8000) PROXY_POOL.clear();
                        
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(10000); 
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            // تنظيف السطر واستخراج IP:PORT فقط
                            if (l.contains(":")) {
                                // استخراج البروكسي من JSON إذا لزم الأمر
                                String cleanProxy = extractProxy(l);
                                if(cleanProxy != null) validateEliteProxy(cleanProxy); 
                            }
                        }
                        r.close();
                        Thread.sleep(300000); // تحديث كل 5 دقائق
                    } catch (Exception e) {
                        try { Thread.sleep(30000); } catch (Exception ex) {}
                    }
                }
            });
        }
    }
    
    // دالة مساعدة لاستخراج البروكسي من النصوص المعقدة
    private String extractProxy(String line) {
        try {
            // بحث بسيط عن نمط IP:PORT
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)");
            java.util.regex.Matcher m = p.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {}
        return null;
    }

    // === الفلتر الصارم (The Gatekeeper) ===
    private void validateEliteProxy(String a) {
        if (BLACKLIST.contains(a)) return;
        
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                long startTime = System.currentTimeMillis();
                
                HttpURLConnection c = (HttpURLConnection) new URL("http://www.google.com/generate_204").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                
                // 🔥 الشرط الصارم: يجب أن يستجيب خلال 3 ثوانٍ فقط 🔥
                c.setConnectTimeout(3000); 
                c.setReadTimeout(3000);
                
                if (c.getResponseCode() == 204) { // 204 تعني اتصال ناجح وسريع جداً
                    long latency = System.currentTimeMillis() - startTime;
                    if (latency < 3000 && !PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }
        }
