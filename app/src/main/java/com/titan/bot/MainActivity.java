package com.titan.bot;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.MotionEvent;
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
    private ExecutorService scrapExec = Executors.newFixedThreadPool(200); 
    private ExecutorService validExec = Executors.newFixedThreadPool(500); // تقليل العدد لزيادة الدقة
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    
    // القائمة السوداء المؤقتة للجلسة الحالية
    private CopyOnWriteArrayList<String> BLACKLIST = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    
    private PowerManager.WakeLock wakeLock;
    private String currentProxy1 = "", currentProxy2 = "", currentProxy3 = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            
            mHandler.postDelayed(() -> {
                try {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::SmartFilter");

                    dashView = findViewById(R.id.dashboardView);
                    aiStatusView = findViewById(R.id.aiStatusView);
                    serverCountView = findViewById(R.id.serverCountView);
                    linkIn = findViewById(R.id.linkInput);
                    controlBtn = findViewById(R.id.controlButton);
                    webContainer = findViewById(R.id.webContainer);

                    CookieManager.getInstance().removeAllCookies(null);

                    if (webContainer != null) {
                        // تعريف واجهة الجافا سكريبت لاستقبال إشارات الحظر
                        web1 = initWeb(1); web2 = initWeb(2); web3 = initWeb(3);
                        setupTripleLayout();
                        startMegaScraping(); 
                        controlBtn.setOnClickListener(v -> toggleEngine());
                        aiStatusView.setText("🤖 AI Sentinel: Monitoring Proxy Quality...");
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

    // واجهة للتواصل بين صفحة الويب وكود الجافا
    public class WebAppInterface {
        Context mContext;
        int webId;

        WebAppInterface(Context c, int id) {
            mContext = c;
            webId = id;
        }

        @JavascriptInterface
        public void reportBadProxy(String reason) {
            // يتم استدعاء هذه الدالة من داخل الصفحة إذا اكتشفت "Anonymous Proxy"
            mHandler.post(() -> {
                String badProxy = (webId == 1) ? currentProxy1 : (webId == 2) ? currentProxy2 : currentProxy3;
                if (!badProxy.isEmpty()) {
                    BLACKLIST.add(badProxy); // إضافة للقائمة السوداء
                    PROXY_POOL.remove(badProxy); // حذف من القائمة النشطة
                    aiStatusView.setText("⛔ AI Blocked: " + badProxy + " (" + reason + ")");
                    updateUI();
                    
                    // إعادة التشغيل ببروكسي جديد فوراً
                    if (webId == 1) runSingleBot(web1, 1);
                    else if (webId == 2) runSingleBot(web2, 2);
                    else runSingleBot(web3, 3);
                }
            });
        }
    }

    private WebView initWeb(int id) {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        
        // ربط الواجهة
        wv.addJavascriptInterface(new WebAppInterface(this, id), "TitanGuard");

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // هذا الكود يفحص محتوى الصفحة بحثاً عن رسائل الحظر
                String checkScript = 
                    "javascript:(function() {" +
                    "  var text = document.body.innerText;" +
                    "  if(text.includes('Anonymous Proxy') || text.includes('Access Denied') || text.includes('Forbidden') || text.includes('VPN detected')) {" +
                    "     window.TitanGuard.reportBadProxy('Detected in Content');" +
                    "  } else {" +
                    // محاكاة التصفح فقط إذا لم يكن هناك حظر
                    "    window.scrollTo(0, 100);" +
                    "  }" +
                    "})()";
                
                v.evaluateJavascript(checkScript, null);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (isRunning && req.isForMainFrame()) {
                    // إذا فشل الاتصال (ERR_CONNECTION_RESET)، اعتبر البروكسي سيئاً
                    mHandler.post(() -> {
                        String badProxy = (id == 1) ? currentProxy1 : (id == 2) ? currentProxy2 : currentProxy3;
                        if (!badProxy.isEmpty()) {
                            PROXY_POOL.remove(badProxy);
                            BLACKLIST.add(badProxy); // حظر
                            updateUI();
                        }
                        runSingleBot(v, id); // المحاولة ببروكسي آخر
                    });
                }
            }
        });
        return wv;
    }

    private void toggleEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP AI ENGINE" : "🚀 START AI ENGINE");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            runSingleBot(web1, 1);
            mHandler.postDelayed(() -> runSingleBot(web2, 2), 2000);
            mHandler.postDelayed(() -> runSingleBot(web3, 3), 4000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            CookieManager.getInstance().removeAllCookies(null);
        }
    }

    private void runSingleBot(WebView wv, int id) {
        if (!isRunning || wv == null) return;
        
        if (PROXY_POOL.isEmpty()) {
            mHandler.postDelayed(() -> runSingleBot(wv, id), 3000);
            return;
        }

        // سحب بروكسي عشوائي لتجنب استخدام نفس البروكسي المحروق بالتتابع
        int index = rnd.nextInt(PROXY_POOL.size());
        String proxy = PROXY_POOL.get(index);
        
        // حفظ البروكسي الحالي لمعرفة من سنحظر إذا فشل
        if (id == 1) currentProxy1 = proxy;
        else if (id == 2) currentProxy2 = proxy;
        else currentProxy3 = proxy;

        updateUI();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyConfig proxyConfig = new ProxyConfig.Builder()
                    .addProxyRule(proxy)
                    .build();
                ProxyController.getInstance().setProxyOverride(proxyConfig, r -> {}, () -> {});
            } catch (Exception e) {
                // فشل في إعداد البروكسي، جرب غيره
                runSingleBot(wv, id);
                return;
            }
        }

        wv.clearHistory();
        wv.clearCache(true);
        CookieManager.getInstance().removeAllCookies(null);

        // استخدام User-Agent حديث جداً
        String userAgent = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36";
        wv.getSettings().setUserAgentString(userAgent);

        String url = linkIn.getText().toString().trim();
        if(url.isEmpty()) url = "https://www.google.com";

        // إضافة Referer قوي
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.google.com/");
        
        wv.loadUrl(url, headers);
        totalJumps++;
        
        // وقت بقاء أطول قليلاً لمحاكاة الواقع
        mHandler.postDelayed(() -> runSingleBot(wv, id), (30 + rnd.nextInt(20)) * 1000);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 Clean IPs: " + PROXY_POOL.size() + " | ☠️ Banned: " + BLACKLIST.size());
            dashView.setText("💰 Visits: " + totalJumps);
        });
    }

    private void startMegaScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=1500&country=all&ssl=all&anonymity=elite", // طلبنا Elite فقط
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            if (l.contains(":") && !BLACKLIST.contains(l.trim())) validateProxy(l.trim()); 
                        }
                        Thread.sleep(60000); 
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validateProxy(String a) {
        validExec.execute(() -> {
            // لا تفحص إذا كان في القائمة السوداء
            if (BLACKLIST.contains(a)) return;

            try {
                String[] p = a.split(":");
                // الفحص عبر موقع صارم (ip-api) للتأكد من أنه لا يسرب الـ IP
                // هذا الفحص "ثقيل" لكنه يضمن جودة أعلى
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])));
                URL testUrl = new URL("http://www.google.com"); // جوجل سريع ومستقر للفحص المبدئي
                HttpURLConnection c = (HttpURLConnection) testUrl.openConnection(proxy);
                c.setConnectTimeout(2000); 
                c.setReadTimeout(2000);
                
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a) && !BLACKLIST.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
                c.disconnect();
            } catch (Exception e) {}
        });
    }
                               }
