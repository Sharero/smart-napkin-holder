package ru.salfetki.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class DemoActivity extends Activity {
    private final int ink = Color.rgb(22, 48, 44), green = Color.rgb(20, 112, 88);
    private LinearLayout body;
    private boolean low;
    private long updated;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final ArrayList<String> history = new ArrayList<>();
    private boolean running;
    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        var p = getSharedPreferences("demo", 0);
        low = p.getBoolean("low", false);
        updated = p.getLong("updated", System.currentTimeMillis());
        String saved = p.getString("history", "");
        if (!saved.isEmpty())
            history.addAll(Arrays.asList(saved.split("\\n")));
        if (history.isEmpty())
            event("Демонстрационный стенд готов к работе");
        getWindow().setStatusBarColor(Color.rgb(244, 247, 245));
        getWindow().setNavigationBarColor(Color.rgb(244, 247, 245));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        render();
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 1);
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold)
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(5), 0, dp(5));
        parent.addView(t);
        return t;
    }
    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(16), dp(20), dp(16));
        c.setBackground(background(color, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        body.addView(c, lp);
        return c;
    }
    private void action(
        LinearLayout parent, String label, boolean primary, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : green);
        b.setBackground(background(primary ? green : Color.rgb(229, 240, 234), 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(10), 0, 0);
        parent.addView(b, lp);
        b.setOnClickListener(listener);
    }
    private String time(long at) {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(at));
    }
    private void event(String message) {
        history.add(0, time(System.currentTimeMillis()) + "  ·  " + message);
        while (history.size() > 12) history.remove(history.size() - 1);
        persist();
    }
    private void persist() {
        getSharedPreferences("demo", 0)
            .edit()
            .putBoolean("low", low)
            .putLong("updated", updated)
            .putString("history", android.text.TextUtils.join("\n", history))
            .apply();
    }
    private void change(boolean value) {
        if (low == value)
            return;
        low = value;
        updated = System.currentTimeMillis();
        event(low ? "Стол 01: достигнут минимальный остаток" : "Стол 01: запас салфеток пополнен");
        if (low) {
            Sync.notify(this, "Демо • стол 01", true);
            event(Sync.canNotify(this) ? "Отправлено тестовое уведомление Android"
                                       : "Уведомление недоступно: проверьте разрешение Android");
        } else
            getSystemService(NotificationManager.class).cancel("Демо • стол 01".hashCode());
        render();
    }
    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 247, 245));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(28), dp(24), dp(32));
        scroll.addView(body);
        setContentView(scroll);
        text(body, "SALFETKI  /  УМНОЕ ОБСЛУЖИВАНИЕ", 11, green, true);
        text(body, "Всё под контролем", 29, ink, true);
        text(body, "Панель администратора · Основной зал", 14, Color.rgb(104, 120, 114), false);
        TextView badge = text(body, "●  Деморежим · показания моделируются", 12, green, true);
        badge.setContentDescription("Демонстрация без подключения оборудования");
        LinearLayout summary = card(green);
        text(summary, low ? "01 требует внимания" : "Все салфетницы готовы", 23, Color.WHITE, true);
        text(summary, "3 устройства на демонстрационном стенде", 13, Color.rgb(211, 237, 226),
            false);
        text(summary, low ? "Пополните салфетки у стола 01" : "Порог остатка не достигнут", 15,
            Color.WHITE, false);
        device("01", "Стол у окна", low, true);
        device("02", "Центральный стол", false, false);
        device("03", "Зона выдачи", false, false);
        LinearLayout controls = card(Color.WHITE);
        text(controls, "Показать работу системы", 20, ink, true);
        text(controls,
            running ? "Сценарий запущен. Через 5 секунд датчик сообщит о низком остатке."
                    : "Смоделируйте сигнал датчика и пополнение. Уведомление появится на телефоне.",
            14, Color.rgb(104, 120, 114), false);
        action(controls, running ? "Остановить сценарий" : "Запустить демонстрацию", true, v -> {
            if (running) {
                stop();
                render();
                return;
            }
            change(false);
            running = true;
            event("Запущен сценарий расхода салфеток");
            render();
            timer.postDelayed(() -> {
                running = false;
                change(true);
            }, 5000);
        });
        action(controls, low ? "Салфетки пополнены" : "Смоделировать низкий остаток", false, v -> {
            stop();
            change(!low);
        });
        LinearLayout journal = card(Color.WHITE);
        text(journal, "Журнал событий", 20, ink, true);
        for (String item : history) text(journal, item, 13, Color.rgb(84, 104, 96), false);
        action(body, "Настроить подключение к ESP32", false, v -> {
            stop();
            Sync.prefs(this).edit().putBoolean("demoMode", false).apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        text(body, "ИК-датчик определяет порог остатка. Точное количество салфеток не измеряется.",
            12, Color.rgb(104, 120, 114), false);
    }
    private void device(String number, String place, boolean warning, boolean interactive) {
        LinearLayout c = card(warning ? Color.rgb(255, 243, 221) : Color.WHITE);
        text(c, "СТОЛ " + number + "   ·   " + place, 12, Color.rgb(104, 120, 114), true);
        text(c, warning ? "Пора пополнить" : "Салфетки есть", 22,
            warning ? Color.rgb(158, 91, 17) : ink, true);
        text(c,
            "● Симулятор на связи  ·  "
                + (interactive ? "Сигнал в " + time(updated) : "Остаток выше порога"),
            12, green, false);
    }
    private void stop() {
        timer.removeCallbacksAndMessages(null);
        running = false;
    }
    @Override
    protected void onPause() {
        stop();
        super.onPause();
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (body != null)
            render();
    }
    @Override
    protected void onDestroy() {
        stop();
        super.onDestroy();
    }
}
