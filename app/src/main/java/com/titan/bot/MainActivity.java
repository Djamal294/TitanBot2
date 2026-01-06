package com.titan.bot;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Switch;
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
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView myBrowser;
    private Button controlButton;
    private EditText linkInput, manualProxyInput;
    private TextView dashboardView;
    private Switch proxyModeSwitch;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService masterExecutor = Executors.newFixedThreadPool(3);
    private ExecutorService helperExecutor = Executors.newSingleThreadExecutor();
    private Random random = new Random();
    private int visitCounter = 0;
    private int clickCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    private String currentCountry = "Analyzing...";
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    // ميزة: محاكاة بصمة الجهاز المتغيرة (Hardware Spoofing)
    private String[] DEVICE_PROFILES = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        manualProxyInput = findViewById(R.id.manualProxyInput);
        proxyModeSwitch = findViewById(R.id.proxyModeSwitch);
        controlButton = findViewById(R.id.controlButton);
        myBrowser = findViewById(R.id.myBrowser);

        createNotificationChannel(); 
        initSettings();
        startMasterScraper(); 
        startHelperBot();
    }

    private void initSettings() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    // ميزة GoLogin Stealth: إخفاء حقيقة أن المتصفح هو "بُوت"
                    myBrowser.loadUrl("javascript:(function(){" +
                        "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                        "Object.defineProperty(navigator,'deviceMemory',{get:()=>8});" +
                        "Object.defineProperty(navigator,'hardwareConcurrency',{get:()=>8});" +
                        "})()");
                    
                    // ميزة النقر المتذبذب (3% - 5% فقط) بمستوى بشري احترافي
                    int clickChance = 3 + random.nextInt(3); // يولد نسبة بين 3 و 5 عشوائياً
                    if (random.nextInt(100) < clickChance) {
                        mainHandler.postDelayed(() -> {
                            // البحث عن الروابط أو الإعلانات والنقر عليها بعشوائية
                            myBrowser.loadUrl("javascript:(function(){" +
                                "var links = document.getElementsByTagName('a');" +
                                "if(links.length > 0) { " +
                                "   var target = links[Math.floor(Math.random()*links.length)];" +
                                "   target.style.border = '1px solid red';" + // وهمي للمحاكاة
                                "   target.click(); " +
                                "}" +
                                "})()");
                            clickCounter++;
                            updateDashboard("🎯 Human Click Sim: " + clickChance + "%");
                        }, 7000 + random.nextInt(8000)); // انتظار طويل قبل النقر لمحاكاة القراءة
                    }
                    
                    // تمرير الصفحة لأسفل لمحاكاة التصفح الحقيقي
                    myBrowser.loadUrl("javascript:window.scrollBy({top: 800, behavior: 'smooth'});");
                }
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        CookieManager.getInstance().removeAllCookies(null);

        if (proxyModeSwitch.isChecked() && !manualProxyInput.getText().toString().isEmpty()) {
            String[] list = manualProxyInput.getText().toString().split("\n");
            currentProxy = list[random.nextInt(list.length)].trim();
        } else if (!VERIFIED_PROXIES.isEmpty()) {
            currentProxy = VERIFIED_PROXIES.remove(0);
        }

        applyProxySettings(currentProxy);
        fetchGeoInfo(currentProxy);

        String ua = DEVICE_PROFILES[random.nextInt(DEVICE_PROFILES.length)];
        myBrowser.getSettings().setUserAgentString(ua);

        String url = linkInput.getText().toString().trim();
        if (url.isEmpty()) return;
        if (!url.startsWith("http")) url = "https://" + url;

        visitCounter++;
        updateDashboard("");
        
        // ميزة تزييف المصدر (Referer Spoofing) لزيادة الموثوقية
        Map<String, String> headers = new HashMap<>();
        String[] referers = {"https://www.google.com/", "https://www.facebook.com/", "https://t.co/", "https://www.bing.com/"};
        headers.put("Referer", referers[random.nextInt(referers.length)]);
        myBrowser.loadUrl(url, headers);

        // توقيت عشوائي بين الزيارات (35 - 70 ثانية)
        mainHandler.postDelayed(this::startNewSession, 35000 + random.nextInt(35000));
    }

    private void updateDashboard(String msg) {
        mainHandler.post(() -> {
            String status = msg.isEmpty() ? "🛡️ Stealth: TITAN-ULTRA SAFE" : msg;
            dashboardView.setText(status + 
                "\n📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + 
                "\n🌍 Geo: " + currentCountry + 
                "\n🌐 Proxy: " + currentProxy + 
                "\n📦 Pool: " + VERIFIED_PROXIES.size());
        });
    }

    // --- الدوال الأساسية للبقاء في الخلفية وجلب البروكسي ---
    private void startMasterScraper() { masterExecutor.execute(() -> { while(true) { try { URL url = new URL("https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt"); BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream())); String l; while((l=r.readLine())!=null && VERIFIED_PROXIES.size()<150) checkProxy(l.trim(), masterExecutor); Thread.sleep(180000); } catch(Exception e){} } }); }
    private void startHelperBot() { helperExecutor.execute(() -> { while(true) { try { URL url = new URL("https://api.proxyscrape.com/v2/?request=getproxies&protocol=http"); BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream())); String l; while((l=r.readLine())!=null && VERIFIED_PROXIES.size()<250) checkProxy(l.trim(), helperExecutor); Thread.sleep(350000); } catch(Exception e){} } }); }
    private void checkProxy(String a, ExecutorService e) { e.execute(() -> { try { String[] p = a.split(":"); HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))); c.setConnectTimeout(4000); if(c.getResponseCode()==200) { if(!VERIFIED_PROXIES.contains(a)) VERIFIED_PROXIES.add(a); updateDashboard(""); } } catch(Exception e1){} }); }
    private void fetchGeoInfo(String p) { if(p.equals("Direct")) return; masterExecutor.execute(() -> { try { String ip = p.split(":")[0]; JSONObject j = new JSONObject(new BufferedReader(new InputStreamReader(new URL("http://ip-api.com/json/"+ip).openStream())).readLine()); currentCountry = j.optString("country", "Global") + " 🌍"; updateDashboard(""); } catch(Exception e){} }); }
    private void applyProxySettings(String p) { if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !p.equals("Direct")) { ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder().addProxyRule(p).build(), r -> {}, () -> {}); } }
    private void toggleBot() { isBotRunning = !isBotRunning; controlButton.setText(isBotRunning ? "STOP TITAN (ULTRA SAFE)" : "START TITAN"); if(isBotRunning) { startNewSession(); showNotification("TitanBot Ultra يعمل بأمان في الخلفية..."); } else { mainHandler.removeCallbacksAndMessages(null); stopNotification(); } }
    private void createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { NotificationManager m = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); m.createNotificationChannel(new NotificationChannel("BOT_CHANNEL", "Titan Bot Service", NotificationManager.IMPORTANCE_LOW)); } }
    private void showNotification(String t) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { Notification.Builder b = new Notification.Builder(this, "BOT_CHANNEL").setContentTitle("TitanBot Ultra PRO").setContentText(t).setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true); ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, b.build()); } }
    private void stopNotification() { ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(1); }
            }
