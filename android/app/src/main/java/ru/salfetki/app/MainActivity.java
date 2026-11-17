package ru.salfetki.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.work.*;
import java.net.URI;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private TextView status;
    private EditText url, token;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        public void run() {
            refresh();
            handler.postDelayed(this, 10000);
        }
    };
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Sync.prefs(this).getBoolean("demoMode", true)) {
            startActivity(new android.content.Intent(this, DemoActivity.class));
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(32, 48, 32, 32);
        scroll.addView(body);
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText("Умная\nсалфетница");
        title.setTextSize(32);
        title.setTextColor(Color.rgb(20, 90, 75));
        body.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Панель администратора\nОткрыто: проверка каждые 10 секунд. В фоне: от 15 "
                     + "минут, возможны задержки.\n");
        body.addView(hint);
        url = new EditText(this);
        url.setHint("Адрес сервера: http://192.168.1.100:8080");
        url.setSingleLine(true);
        url.setInputType(17);
        url.setText(Sync.prefs(this).getString("url", "http://10.0.2.2:8080"));
        body.addView(url);
        token = new EditText(this);
        token.setHint("Токен администратора");
        token.setSingleLine(true);
        token.setInputType(129);
        token.setText(Sync.prefs(this).getString("token", ""));
        body.addView(token);
        button(body, "Открыть демонстрационный стенд", v -> {
            Sync.prefs(this).edit().putBoolean("demoMode", true).apply();
            startActivity(new android.content.Intent(this, DemoActivity.class));
            finish();
        });
        button(body, "Сохранить и включить наблюдение", v -> save());
        button(body, "Обновить сейчас", v -> refresh());
        button(body, "Демо: пробное уведомление", v -> {
            Sync.notify(this, "Демо • стол №1", true);
            status.setText(Sync.canNotify(this)
                    ? "Демонстрация: мало салфеток у стола №1. Данные сервера не изменены."
                    : "Разрешите уведомления в настройках Android.");
        });
        button(body, "Остановить фоновое наблюдение", v -> {
            WorkManager.getInstance(this).cancelUniqueWork("monitor");
            Sync.prefs(this).edit().putBoolean("monitor", false).apply();
            status.setText("Фоновое наблюдение выключено.");
        });
        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, 24, 0, 0);
        body.addView(status);
        try {
            String cached = Sync.prefs(this).getString("snapshot", "");
            status.setText(cached.isEmpty() ? "Сохраните настройки или нажмите «Демо»."
                                            : "Сохранённые данные:\n" + Sync.render(cached));
        } catch (Exception e) {
            status.setText("Нажмите «Обновить сейчас».");
        }
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 1);
    }
    private void button(LinearLayout body, String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(click);
        body.addView(b);
    }
    private void save() {
        String address = url.getText().toString().trim().replaceAll("/+$", ""),
               secret = token.getText().toString().trim();
        try {
            URI uri = new URI(address);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty()) || uri.getQuery() != null
                || uri.getFragment() != null || secret.length() < 20)
                throw new Exception();
        } catch (Exception e) {
            status.setText("Нужен адрес http(s)://хост:порт без пути и токен от 20 символов.");
            return;
        }
        synchronized (Sync.class) {
            var p = Sync.prefs(this);
            var edit = p.edit();
            if (!address.equals(p.getString("url", "")) || !secret.equals(p.getString("token", "")))
                edit.remove("cursor").remove("snapshot");
            edit.putString("url", address)
                .putString("token", secret)
                .putBoolean("monitor", true)
                .apply();
        }
        PeriodicWorkRequest work =
            new PeriodicWorkRequest.Builder(MonitorWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(
                    new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "monitor", ExistingPeriodicWorkPolicy.UPDATE, work);
        refresh();
    }
    private void refresh() {
        executor.execute(() -> {
            String message;
            try {
                message = Sync.run(this);
            } catch (Exception e) {
                message = "Не удалось обновить: " + e.getMessage()
                    + "\nПоследние данные остаются в памяти приложения.";
            }
            String result = message;
            runOnUiThread(() -> {
                if (!isDestroyed())
                    status.setText(result);
            });
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing() && Sync.prefs(this).getBoolean("monitor", false))
            handler.post(poll);
    }
    @Override
    protected void onPause() {
        handler.removeCallbacks(poll);
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poll);
        executor.shutdown();
        super.onDestroy();
    }
}
