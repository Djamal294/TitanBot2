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
    private TextView dashView, serverCountView;
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
        setContentView(R.layout.activity_main);
        
        // تفعيل الكوكيز (ضروري للأرباح)
        CookieManager.getInstance().setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
        }

        dashView = findViewById(R.id.dashboardView);
        serverCountView = findViewById(R.id.serverCountView);
        linkIn = findViewById(R.id.linkInput);
        controlBtn = findViewById(R.id.controlButton);
        webContainer = findViewById(R.id.webContainer);

        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
        setupTripleLayout();
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
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // حل مشكلة TIMED_OUT الظاهرة في صورتك
                if (isRunning && req.isForMainFrame()) {
                    mHandler.post(() -> runSingleBot(v)); // تبديل فوري للبروكسي
                }
            }
        });
        return wv;
    }

    private void toggleZenithV5() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP V5 GHOST" : "🚀 LAUNCH ZENITH V5");
        if (isRunning) { runSingleBot(web1); runSingleBot(web2); runSingleBot(web3); }
    }

    private void runSingleBot(WebView wv) {
        if (!isRunning || PROXY_POOL.isEmpty()) return;
        String proxy = PROXY_POOL.remove(0);
        updateUI();

        // تفعيل البروكسي (يتطلب مكتبة webkit في الجرادل)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                .addProxyRule(proxy).build(), r -> {}, () -> {});
        }

        wv.loadUrl(linkIn.getText().toString().trim());
        totalJumps++;
        mHandler.postDelayed(() -> runSingleBot(wv), (40 + rnd.nextInt(30)) * 1000);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 V5 POOL: " + PROXY_POOL.size());
            dashView.setText("💰 Master Jumps: " + totalJumps);
        });
    }

    private void startInfinityScraping() {
        String[] sources = {"https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=1500&country=all"};
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
                c.setConnectTimeout(1000);
                if (c.getResponseCode() == 200) { if (!PROXY_POOL.contains(a)) { PROXY_POOL.add(a); updateUI(); } }
            } catch (Exception e) {}
        });
    }
            }
