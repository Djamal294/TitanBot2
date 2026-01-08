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
import android.graphics.Bitmap; // مهم

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
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(100); 
    private ExecutorService validExec = Executors.newFixedThreadPool(2000); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
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
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            setContentView(R.layout.activity_main);
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
            
            if(web1 != null) setupIronCageWebView(web1);
            if(web2 != null) setupIronCageWebView(web2);
            if(web3 != null) setupIronCageWebView(web3);

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::V18IronCage");
            
            aiStatusView.setText("🛡️ V18: IRON CAGE ACTIVE");

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 🔥 إعدادات القفص الحديدي 🔥
    private void setupIronCageWebView(WebView wv) {
        if (wv == null) return;
        try {
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setSupportMultipleWindows(false); // ممنوع النوافذ الجديدة
            s.setJavaScriptCanOpenWindowsAutomatically(false); // ممنوع فتح روابط تلقائياً
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            
            wv.setWebViewClient(new WebViewClient() {
                
                // 🔒 هذا الكود هو الحارس الذي يمنع الخروج لكروم
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    // إذا كان الرابط يبدأ بـ http أو https، افتحه داخل التطبيق بالقوة
                    if (url.startsWith("http") || url.startsWith("https")) {
                        view.loadUrl(url);
                        return true; 
                    }
                    // إذا كان رابط تطبيق خارجي (مثل market://)، امنعه
                    return true; 
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    // 🕵️‍♂️ كاشف الحظر المبكر
                    if (url.contains("google.com/sorry") || url.contains("captcha")) {
                        handleBannedProxy(view);
                    }
                }

                @Override
                public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) {
                        handleBannedProxy(v);
                    }
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    if (url.equals("about:blank")) return;

                    // 🕵️‍♂️ كاشف الحظر المتأخر
                    if (url.contains("google.com/sorry") || url.contains("recaptcha")) {
                        handleBannedProxy(v);
                        return;
                    }

                    injectStealthScripts(v);

                    if (url.contains("google.com")) {
                        injectGoogleCookies(v);
                        mHandler.postDelayed(() -> navigateToTarget(v), 1200); 
                    } else {
                        mHandler.post(() -> aiStatusView.setText("🟢 TARGET HIT"));
                        simulateHumanBehavior(v);
                    }
                }
            });

        } catch (Exception e) {}
    }

    // دالة التعامل مع البروكسي المحظور (لكي لا ترى صفحة الخطأ)
    private void handleBannedProxy(WebView v) {
        String badProxy = (String) v.getTag();
        if (badProxy != null) {
            BLACKLIST.add(badProxy);
            mHandler.post(() -> aiStatusView.setText("⛔ Banned/Captcha Detected -> Skipping"));
        }
        v.stopLoading();
        v.loadUrl("about:blank");
        if (isRunning) mHandler.postDelayed(() -> runSingleBot(v), 200); // تغيير فوري
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

    private void injectGoogleCookies(WebView v) {
        String js = "(function() { document.cookie = 'CONSENT=YES+US.en+202201; path=/; domain=.google.com'; })();";
        v.evaluateJavascript(js, null);
    }

    private void injectStealthScripts(WebView v) {
        String js = "(function() { try { Object.defineProperty(navigator, 'webdriver', {get: () => undefined}); } catch(e) {} })();";
        v.evaluateJavascript(js, null);
    }

    private void simulateHumanBehavior(WebView v) {
        v.evaluateJavascript("(function(){" +
            "   setInterval(function(){ window.scrollBy(0, 40); }, 300);" +
            "   setTimeout(function(){ document.body.click(); }, 2500);" +
            "})()", null);
    }

    private void toggleSystem() {
        isRunning = !isRunning;
        if (controlBtn != null) controlBtn.setText(isRunning ? "🛑 STOP" : "🚀 LAUNCH V18");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            if (web1 != null) runSingleBot(web1);
            if (web2 != null) mHandler.postDelayed(() -> runSingleBot(web2), 1500);
            if (web3 != null) mHandler.postDelayed(() -> runSingleBot(web3), 3000);
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

            int index = rnd.nextInt(PROXY_POOL.size());
            String proxy = PROXY_POOL.get(index);

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
                String[] agents = {
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
                };
                wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
                wv.loadUrl("https://www.google.com"); 
            }
            
            totalJumps++;
            mHandler.postDelayed(() -> runSingleBot(wv), (20 + rnd.nextInt(10)) * 1000);

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 500);
        }
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("💎 Clean IPs: " + PROXY_POOL.size());
            dashView.setText("⚡ Hits: " + totalJumps);
        });
    }

    private void startAdvancedScraping() {
        // مصادر مختارة بعناية لتقليل الحظر
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=4000&country=all&ssl=all&anonymity=elite",
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt"
        };

        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        if (PROXY_POOL.size() > 6000) PROXY_POOL.clear();
                        
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(8000); 
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            if (l.contains(":")) validateEliteProxy(l.trim()); 
                        }
                        r.close();
                        Thread.sleep(300000); 
                    } catch (Exception e) {
                        try { Thread.sleep(30000); } catch (Exception ex) {}
                    }
                }
            });
        }
    }

    private void validateEliteProxy(String a) {
        if (BLACKLIST.contains(a)) return;
        
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("http://www.google.com/generate_204").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                
                c.setConnectTimeout(4000); 
                c.setReadTimeout(4000);
                
                if (c.getResponseCode() == 204) { 
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }
                }
