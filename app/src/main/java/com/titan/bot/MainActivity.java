package com.titan.bot;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
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
    private LinearLayout webContainer;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    // زيادة عدد الخيوط للتعامل مع الكم الهائل من المصادر
    private ExecutorService scrapExec = Executors.newFixedThreadPool(50); 
    private ExecutorService validExec = Executors.newFixedThreadPool(800); // 800 خيط للفحص السريع
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    
    // استخدام Set لمنع التكرار بشكل أسرع
    private Set<String> CHECKED_HISTORY = Collections.synchronizedSet(new HashSet<>());
    private CopyOnWriteArrayList<String> BLACKLIST = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    
    private PowerManager.WakeLock wakeLock;
    private String currentProxy1 = "", currentProxy2 = "", currentProxy3 = "";
    private Runnable timeoutRunnable1, timeoutRunnable2, timeoutRunnable3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            
            mHandler.postDelayed(() -> {
                try {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::Nuclear");

                    dashView = findViewById(R.id.dashboardView);
                    aiStatusView = findViewById(R.id.aiStatusView);
                    serverCountView = findViewById(R.id.serverCountView);
                    linkIn = findViewById(R.id.linkInput);
                    controlBtn = findViewById(R.id.controlButton);
                    webContainer = findViewById(R.id.webContainer);

                    CookieManager.getInstance().setAcceptCookie(true);
                    CookieManager.getInstance().removeAllCookies(null);

                    if (webContainer != null) {
                        web1 = initWeb(1); web2 = initWeb(2); web3 = initWeb(3);
                        setupTripleLayout();
                        startNuclearScraping(); // تشغيل المصادر الجديدة
                        controlBtn.setOnClickListener(v -> toggleEngine());
                        aiStatusView.setText("☢️ Nuclear Core: Harvesting 20+ Sources...");
                    }
                } catch (Exception e) {}
            }, 1000); 
        } catch (Exception e) {}
    }

    private void setupTripleLayout() {
        if (webContainer == null || web1 == null) return;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    public class WebAppInterface {
        Context mContext; int webId;
        WebAppInterface(Context c, int id) { mContext = c; webId = id; }
        @JavascriptInterface
        public void reportBadProxy(String reason) {
            mHandler.post(() -> handleBadProxy(webId, reason));
        }
    }

    private void handleBadProxy(int id, String reason) {
        String badProxy = (id == 1) ? currentProxy1 : (id == 2) ? currentProxy2 : currentProxy3;
        if (!badProxy.isEmpty() && !BLACKLIST.contains(badProxy)) {
            BLACKLIST.add(badProxy);
            PROXY_POOL.remove(badProxy);
            aiStatusView.setText("⚡ Kill Switch: " + badProxy + " [" + reason + "]");
            updateUI();
        }
        WebView wv = (id == 1) ? web1 : (id == 2) ? web2 : web3;
        if(wv != null) runSingleBot(wv, id);
    }

    private WebView initWeb(int id) {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        wv.addJavascriptInterface(new WebAppInterface(this, id), "TitanGuard");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Runnable timeoutTask = () -> handleBadProxy(id, "Timeout");
                if (id == 1) timeoutRunnable1 = timeoutTask;
                else if (id == 2) timeoutRunnable2 = timeoutTask;
                else timeoutRunnable3 = timeoutTask;
                mHandler.postDelayed(timeoutTask, 20000);
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                if (id == 1) mHandler.removeCallbacks(timeoutRunnable1);
                else if (id == 2) mHandler.removeCallbacks(timeoutRunnable2);
                else mHandler.removeCallbacks(timeoutRunnable3);
                
                v.evaluateJavascript(
                    "javascript:(function() { var text = document.body.innerText; if(text.includes('Anonymous Proxy') || text.includes('Access Denied')) { window.TitanGuard.reportBadProxy('Blocked Content'); } })()", null);
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (isRunning && req.isForMainFrame()) handleBadProxy(id, "Net Error: " + err.getErrorCode());
            }
        });
        return wv;
    }

    private void toggleEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP NUCLEAR" : "🚀 START NUCLEAR");
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            runSingleBot(web1, 1);
            mHandler.postDelayed(() -> runSingleBot(web2, 2), 2000);
            mHandler.postDelayed(() -> runSingleBot(web3, 3), 4000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    private void runSingleBot(WebView wv, int id) {
        if (!isRunning || wv == null) return;
        if (PROXY_POOL.isEmpty()) {
            mHandler.postDelayed(() -> runSingleBot(wv, id), 2000);
            return;
        }
        
        String proxy;
        try { proxy = PROXY_POOL.get(rnd.nextInt(Math.min(PROXY_POOL.size(), 20))); } 
        catch (Exception e) { proxy = PROXY_POOL.get(0); }

        if (id == 1) currentProxy1 = proxy;
        else if (id == 2) currentProxy2 = proxy;
        else currentProxy3 = proxy;

        updateUI();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyConfig proxyConfig = new ProxyConfig.Builder().addProxyRule(proxy).build();
                ProxyController.getInstance().setProxyOverride(proxyConfig, r -> {}, () -> {});
            } catch (Exception e) { runSingleBot(wv, id); return; }
        }

        wv.clearHistory(); wv.clearCache(true);
        CookieManager.getInstance().removeAllCookies(null);
        wv.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        String url = linkIn.getText().toString().trim();
        if(url.isEmpty()) url = "https://www.google.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.google.com/");
        wv.loadUrl(url, headers);
        totalJumps++;
        mHandler.postDelayed(() -> runSingleBot(wv, id), 40000);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🚀 FAST: " + PROXY_POOL.size() + " | ☠️ Banned: " + BLACKLIST.size());
            dashView.setText("💰 Visits: " + totalJumps);
        });
    }

    // --- القائمة النووية للمصادر ---
    private void startNuclearScraping() {
        String[] sources = {
            // المصادر الضخمة (APIs & GitHub)
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=3000&country=all&ssl=all&anonymity=all",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt", // يحتوي على http أيضاً
            "https://raw.githubusercontent.com/sunny9577/proxy-scraper/master/proxies.txt",
            "https://raw.githubusercontent.com/rdavydov/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt",
            "https://raw.githubusercontent.com/muroso/proxy-list/master/http.txt",
            "https://raw.githubusercontent.com/Zaeem20/FREE_PROXIES_LIST/master/http.txt",
            "https://raw.githubusercontent.com/Anonym0usWork1221/Free-Proxies/main/proxy_files/http_proxies.txt",
            "https://raw.githubusercontent.com/officialputuid/KangProxy/KangProxy/http/http.txt",
            "https://raw.githubusercontent.com/roosterkid/openproxylist/main/HTTPS_RAW.txt",
            "https://raw.githubusercontent.com/yemixzy/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/mmpx12/proxy-list/master/http.txt",
            "https://raw.githubusercontent.com/proxy4parsing/proxy-list/main/http.txt",
            "https://raw.githubusercontent.com/vakhov/fresh-proxy-list/master/http.txt",
            "https://www.proxy-list.download/api/v1/get?type=http",
            "https://www.proxy-list.download/api/v1/get?type=https",
            "https://api.openproxylist.xyz/http.txt",
            "https://alexa.design/2020/wp-content/uploads/2020/05/http_proxies.txt"
        };

        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(10000);
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            String clean = l.trim();
                            // التحقق مما إذا كنا قد فحصنا هذا البروكسي سابقاً لتوفير الموارد
                            if (clean.contains(":") && !CHECKED_HISTORY.contains(clean)) { 
                                CHECKED_HISTORY.add(clean);
                                validateFastProxy(clean); 
                            }
                        }
                        r.close();
                        Thread.sleep(60000); // تكرار كل دقيقة لجلب التحديثات
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validateFastProxy(String a) {
        validExec.execute(() -> {
            if (BLACKLIST.contains(a)) return;
            try {
                String[] p = a.split(":");
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])));
                
                // استخدام رابط خفيف جداً للفحص الأولي
                URL testUrl = new URL("http://www.gstatic.com/generate_204");
                long startTime = System.currentTimeMillis();
                
                HttpURLConnection c = (HttpURLConnection) testUrl.openConnection(proxy);
                c.setConnectTimeout(3000); 
                c.setReadTimeout(3000);
                c.connect();
                
                int code = c.getResponseCode();
                long time = System.currentTimeMillis() - startTime;

                // نقبل البروكسي إذا كان سريعاً (أقل من 3 ثواني)
                if ((code == 204 || code == 200) && time < 3000) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
                c.disconnect();
            } catch (Exception e) {}
        });
    }
                }
