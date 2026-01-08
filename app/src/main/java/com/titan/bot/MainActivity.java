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
    // === العناصر ===
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    
    // === المحركات الخلفية ===
    private Handler mHandler = new Handler(Looper.getMainLooper());
    // 1. محرك الجلب (The Harvester)
    private ExecutorService scrapExec = Executors.newFixedThreadPool(50); 
    // 2. محرك الفحص (The Judge)
    private ExecutorService validExec = Executors.newFixedThreadPool(1000); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    
    // === الذاكرة الذكية ===
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    // 3. القائمة السوداء (بوت الباند)
    private Set<String> BLACKLIST = Collections.synchronizedSet(new HashSet<>());
    
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );

            setContentView(R.layout.activity_main);
            
            // تشغيل محرك الجلب العملاق
            startMassiveScraping(); 

            // ربط العناصر
            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            serverCountView = findViewById(R.id.serverCountView);
            linkIn = findViewById(R.id.linkInput);
            controlBtn = findViewById(R.id.controlButton);

            // ربط المتصفحات (XML Inflation)
            web1 = findViewById(R.id.webview_1);
            web2 = findViewById(R.id.webview_2);
            web3 = findViewById(R.id.webview_3);

            if (controlBtn != null) {
                controlBtn.setOnClickListener(v -> toggleSystem());
            }

            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
            
            // تهيئة المتصفحات
            if(web1 != null) setupSmartWebView(web1);
            if(web2 != null) setupSmartWebView(web2);
            if(web3 != null) setupSmartWebView(web3);

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::V14Enterprise");
            
            aiStatusView.setText("🛡️ V14: BLACKLIST ENGINE ACTIVE");

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupSmartWebView(WebView wv) {
        if (wv == null) return;
        try {
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setAllowFileAccess(false);
            s.setGeolocationEnabled(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setLoadsImagesAutomatically(true);
            
            wv.setWebViewClient(new WebViewClient() {
                // عند حدوث خطأ اتصال (فشل البروكسي)
                @Override
                public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) {
                        String currentProxy = (String) v.getTag();
                        if (currentProxy != null) {
                            // إضافة للقائمة السوداء فوراً
                            BLACKLIST.add(currentProxy);
                            mHandler.post(() -> aiStatusView.setText("⛔ Banned Bad Proxy"));
                        }
                        v.loadUrl("about:blank");
                        if (isRunning) {
                            // المحاولة مرة أخرى فوراً
                            mHandler.postDelayed(() -> runSingleBot(v), 500); 
                        }
                    }
                }
                
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    handler.proceed(); 
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    if (url.equals("about:blank")) return;

                    injectStealthScripts(v);

                    if (url.contains("google.com")) {
                        injectFakeHistory(v); 
                        mHandler.postDelayed(() -> navigateToTarget(v), 2500); 
                    } else {
                        // هنا يعمل "بوت الباند"
                        // يفحص محتوى الصفحة بحثاً عن رسائل الحظر
                        checkBanStatus(v);
                    }
                }
            });

        } catch (Exception e) {}
    }

    private void navigateToTarget(WebView v) {
        String targetUrl = "";
        if(linkIn != null) targetUrl = linkIn.getText().toString().trim();
        if(targetUrl.isEmpty()) targetUrl = "https://www.google.com";
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Requested-With", ""); 
        headers.put("Referer", "https://www.google.com/");
        
        if (v != null) v.loadUrl(targetUrl, headers);
    }

    // === بوت الباند (The Ban Bot) ===
    private void checkBanStatus(WebView v) {
        v.evaluateJavascript(
            "(function() { " +
            "   var body = document.body.innerText.toLowerCase(); " +
            "   if (body.includes('access denied') || " +
            "       body.includes('forbidden') || " +
            "       body.includes('security check') || " +
            "       body.includes('blocked') || " +
            "       body.includes('captcha')) { " +
            "       return 'BANNED'; " +
            "   } return 'OK'; " +
            "})();",
            result -> {
                if (result != null && result.contains("BANNED")) {
                    // تم اكتشاف حظر!
                    String currentProxy = (String) v.getTag();
                    if (currentProxy != null) {
                        BLACKLIST.add(currentProxy); // حظر الخادم للأبد
                        mHandler.post(() -> aiStatusView.setText("🚫 Proxy Blacklisted!"));
                    }
                    // إعادة المحاولة بخادم جديد
                    v.loadUrl("about:blank");
                    mHandler.postDelayed(() -> runSingleBot(v), 1000);
                } else {
                    // خادم نظيف
                    mHandler.post(() -> aiStatusView.setText("🟢 Safe Hit"));
                    simulateHumanBehavior(v);
                }
            }
        );
    }

    // === دوال الذكاء ===
    private void injectFakeHistory(WebView v) {
        String js = "(function() { try { localStorage.setItem('user_consent', 'true'); document.cookie = 'CONSENT=YES+US.en+202201; path=/; domain=.google.com'; } catch(e) {} })();";
        v.evaluateJavascript(js, null);
    }

    private void injectStealthScripts(WebView v) {
        String js = 
            "(function() {" +
            "   try {" +
            "       Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
            "       Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});" +
            "   } catch(e) {}" +
            "})();";
        v.evaluateJavascript(js, null);
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
        if (controlBtn != null) controlBtn.setText(isRunning ? "🛑 STOP" : "🚀 LAUNCH V14");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            if (web1 != null) runSingleBot(web1);
            if (web2 != null) mHandler.postDelayed(() -> runSingleBot(web2), 2000);
            if (web3 != null) mHandler.postDelayed(() -> runSingleBot(web3), 4000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (wv == null || !isRunning) return;
        
        if (PROXY_POOL.isEmpty()) {
            mHandler.postDelayed(() -> runSingleBot(wv), 3000);
            return;
        }

        try {
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
            wv.clearHistory();

            // سحب بروكسي عشوائي (لتجنب التكرار)
            int index = rnd.nextInt(PROXY_POOL.size());
            String proxy = PROXY_POOL.get(index);

            // فحص القائمة السوداء قبل الاستخدام
            if (BLACKLIST.contains(proxy)) {
                PROXY_POOL.remove(index); // حذفه من القائمة النشطة
                runSingleBot(wv); // جرب غيره فوراً
                return;
            }

            // حفظ البروكسي الحالي في الوسم لمعرفة من نحظر لاحقاً
            wv.setTag(proxy);
            updateUI();

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                try {
                    ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                        .addProxyRule(proxy).build(), r -> {}, () -> {});
                } catch (Exception e) {}
            }
            
            if (wv.getSettings() != null) {
                // تغيير عشوائي للمتصفح
                String[] agents = {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
                };
                wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
                wv.loadUrl("https://www.google.com"); 
            }
            
            totalJumps++;
            // التوقيت العشوائي
            mHandler.postDelayed(() -> runSingleBot(wv), (30 + rnd.nextInt(20)) * 1000);

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 1000);
        }
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 Good IPs: " + PROXY_POOL.size());
            dashView.setText("💰 Hits: " + totalJumps);
        });
    }

    // === محرك الجلب العملاق (The Harvester) ===
    private void startMassiveScraping() {
        // مصادر ضخمة (يتم تحديثها يومياً)
        String[] sources = {
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/officialputuid/KangProxy/KangProxy/http/http.txt",
            "https://raw.githubusercontent.com/roosterkid/openproxylist/main/HTTPS_RAW.txt",
            "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
            "https://raw.githubusercontent.com/sunny9577/proxy-scraper/master/proxies.txt",
            "https://raw.githubusercontent.com/almroot/proxylist/master/list.txt",
            "https://raw.githubusercontent.com/opsxcq/proxy-list/master/list.txt",
            "https://raw.githubusercontent.com/proxy4parsing/proxy-list/main/http.txt",
            "https://raw.githubusercontent.com/mmpx12/proxy-list/master/http.txt",
            "https://raw.githubusercontent.com/vakhov/fresh-proxy-list/master/http.txt",
            "https://raw.githubusercontent.com/mertguvencli/http-proxy-list/main/proxy-list/data.txt",
            "https://raw.githubusercontent.com/hendrikbgr/Free-Proxy-Repo/master/proxy_list.txt",
            "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
            "https://raw.githubusercontent.com/asimo17/proxy-list/master/proxies.txt",
            "https://raw.githubusercontent.com/B4RC0DE-TM/proxy-list/main/HTTP.txt",
            "https://raw.githubusercontent.com/saisuiu/Lionkings-Http-Proxys-Proxies/main/free.txt"
        };

        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        // حد أقصى للحفاظ على الذاكرة
                        if (PROXY_POOL.size() > 10000) { Thread.sleep(60000); continue; }
                        
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(10000); 
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            if (l.contains(":")) validateProxy(l.trim()); 
                        }
                        r.close();
                        // انتظر 10 دقائق قبل إعادة الفحص
                        Thread.sleep(600000); 
                    } catch (Exception e) {
                        try { Thread.sleep(30000); } catch (Exception ex) {}
                    }
                }
            });
        }
    }

    // === محرك الفلترة (The Judge) ===
    private void validateProxy(String a) {
        // إذا كان محظوراً، لا تتعب نفسك بفحصه
        if (BLACKLIST.contains(a)) return;

        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                // المهلة المطلوبة: 20 ثانية (20000ms)
                c.setConnectTimeout(20000); 
                c.setReadTimeout(20000);
                
                // إذا استجاب خلال 20 ثانية فهو جيد
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a) && !BLACKLIST.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }
                        }
                        
