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
import android.view.View;
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
    // تعريف عناصر الواجهة
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    private LinearLayout webContainer;
    
    // محركات الخيوط (Threads) لضمان عدم تشنج الهاتف
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(150); // جلب فائق السرعة
    private ExecutorService validExec = Executors.newFixedThreadPool(500); // فحص بروكسيات ضخم
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // تفعيل الكوكيز (السر في قبول الأرباح)
        CookieManager.getInstance().setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
        }

        // ربط العناصر بالكود
        dashView = findViewById(R.id.dashboardView);
        aiStatusView = findViewById(R.id.aiStatusView);
        serverCountView = findViewById(R.id.serverCountView);
        linkIn = findViewById(R.id.linkInput);
        controlBtn = findViewById(R.id.controlButton);
        webContainer = findViewById(R.id.webContainer);

        // إنشاء محركات العرض
        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
        setupTripleLayout();
        
        // تشغيل نظام جلب البروكسيات فور فتح التطبيق
        startInfinityScraping(); 
        
        controlBtn.setOnClickListener(v -> toggleZenithV5());
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
        
        // إعدادات كسر الحماية (Stealth Settings)
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // حل الشاشة البيضاء
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // حقن كود المحاكاة البشرية (تجاوز الـ Anti-Bot)
                v.evaluateJavascript("(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'platform',{get:()=>'Win32'});" +
                    "window.scrollTo(0, "+rnd.nextInt(700)+");" +
                    "setInterval(function(){ window.scrollBy(0, "+(rnd.nextBoolean()?15:-10)+"); }, 5000);" +
                    "})()", null);
                
                aiStatusView.setText("🤖 AI Intel: Traffic Verified - Human Mode");
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // إعادة المحاولة فوراً عند حدوث خطأ أو انتهاء مهلة البروكسي (TIMED_OUT)
                if (isRunning && req.isForMainFrame()) {
                    mHandler.post(() -> runSingleBot(v));
                }
            }
        });
        
        wv.setWebChromeClient(new WebChromeClient());
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
        if (!isRunning || PROXY_POOL.isEmpty()) {
            if (isRunning) mHandler.postDelayed(() -> runSingleBot(wv), 3000);
            return;
        }

        String proxy = PROXY_POOL.remove(0);
        updateUI();

        // تفعيل البروكسي المطور (Proxy Override)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                .addProxyRule(proxy).build(), r -> {}, () -> {});
        }

        // تغيير هوية المتصفح (User-Agent Rotation)
        String[] agents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        };
        wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
        
        // إعداد ترويسات الطلب لتبدو كأنها قادمة من جوجل
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.google.com/");
        headers.put("X-Requested-With", "com.android.chrome");

        String targetUrl = linkIn.getText().toString().trim();
        if (!targetUrl.startsWith("http")) targetUrl = "https://" + targetUrl;
        
        wv.loadUrl(targetUrl, headers);
        totalJumps++;
        
        // توقيت القفزة القادمة (بشري: بين 40 و 80 ثانية)
        mHandler.postDelayed(() -> runSingleBot(wv), (40 + rnd.nextInt(40)) * 1000);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 V5 INFINITY POOL: " + PROXY_POOL.size() + " [GHOST]");
            dashView.setText("💰 Zenith Master | Total Jumps: " + totalJumps);
        });
    }

    private void startInfinityScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=1500&country=all",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://proxyspace.pro/http.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            if (l.contains(":")) validateProxy(l.trim()); 
                        }
                        Thread.sleep(60000); // تحديث كل دقيقة
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validateProxy(String proxyAddr) {
        validExec.execute(() -> {
            try {
                String[] parts = proxyAddr.split(":");
                HttpURLConnection conn = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], Integer.parseInt(parts[1])))
                );
                conn.setConnectTimeout(1500); // فلترة البروكسيات السريعة فقط
                if (conn.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(proxyAddr)) {
                        PROXY_POOL.add(proxyAddr);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }
}
