package ru.salfetki.app;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

final class Sync {
    static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", 0);
    }
    static boolean canNotify(Context c) {
        return (Build.VERSION.SDK_INT < 33
                   || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                       == PackageManager.PERMISSION_GRANTED)
            && c.getSystemService(NotificationManager.class).areNotificationsEnabled()
            && (c.getSystemService(NotificationManager.class).getNotificationChannel("low") == null
                || c.getSystemService(NotificationManager.class)
                        .getNotificationChannel("low")
                        .getImportance()
                    != NotificationManager.IMPORTANCE_NONE);
    }
    static void notify(Context c, String device, boolean demo) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
            new NotificationChannel("low", "Мало салфеток", NotificationManager.IMPORTANCE_HIGH));
        if (!canNotify(c))
            return;
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(device.hashCode(),
            new Notification.Builder(c, "low")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(demo ? "Демонстрация" : "Мало салфеток")
                .setContentText(device + ": пополните салфетницу")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
    }
    static synchronized String run(Context c) throws Exception {
        var p = prefs(c);
        String base = p.getString("url", ""), token = p.getString("token", "");
        if (base.isEmpty() || token.isEmpty())
            throw new Exception("Сначала сохраните адрес и токен");
        long cursor = p.getLong("cursor", 0);
        HttpURLConnection conn =
            (HttpURLConnection) new URL(base + "/api/sync?after=" + cursor).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int status = conn.getResponseCode();
            if (status != 200)
                throw new Exception("Ошибка сервера HTTP " + status);
            String raw;
            try (var in = conn.getInputStream()) {
                var out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                raw = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
            JSONObject data = new JSONObject(raw);
            JSONArray devices = data.getJSONArray("devices"), events = data.getJSONArray("events");
            for (int i = 0; i < devices.length(); i++) {
                JSONObject d = devices.getJSONObject(i);
                boolean changedToLow = false;
                for (int j = 0; j < events.length(); j++) {
                    JSONObject e = events.getJSONObject(j);
                    if (e.getString("device").equals(d.getString("id")) && e.getBoolean("low"))
                        changedToLow = true;
                }
                if (changedToLow && d.getBoolean("low"))
                    notify(c, d.getString("id"), false);
            }
            var edit =
                p.edit().putString("snapshot", raw).putLong("synced", System.currentTimeMillis());
            if (canNotify(c))
                edit.putLong("cursor", data.getLong("cursor"));
            edit.apply();
            return render(raw);
        } finally {
            conn.disconnect();
        }
    }
    static String render(String raw) throws JSONException {
        JSONArray rows = new JSONObject(raw).getJSONArray("devices");
        if (rows.length() == 0)
            return "Салфетницы ещё не подключены. Запустите симулятор или ESP32.";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject d = rows.getJSONObject(i);
            result.append(d.getString("id"))
                .append("\n")
                .append(d.getBoolean("low") ? "⚠ Мало салфеток" : "✓ Салфетки есть")
                .append(d.getBoolean("online") ? " • На связи" : " • Нет связи")
                .append("\nПоследний сигнал: ")
                .append(
                    new java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date(d.getLong("seen") * 1000)))
                .append("\n\n");
        }
        return result.toString();
    }
}
