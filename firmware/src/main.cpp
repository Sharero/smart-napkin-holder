#include <Arduino.h>
#include <HTTPClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <time.h>

#include "config.h"

bool candidateLow, stableLow, ready = false, dirty = true;
uint32_t changedAt, lastAttempt = 0, lastSent = 0, lastWifi = 0;

bool sendState() {
  HTTPClient http;
  WiFiClient plain;
  WiFiClientSecure secure;
  String url = String(SERVER_URL) + "/api/telemetry";
  bool begun;
  if (url.startsWith("https://")) {
    if (strlen(ROOT_CA) == 0 || time(nullptr) < 1700000000) return false;
    secure.setCACert(ROOT_CA);
    begun = http.begin(secure, url);
  } else
    begun = http.begin(plain, url);
  if (!begun) return false;
  http.setConnectTimeout(3000);
  http.setTimeout(3000);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", String("Bearer ") + DEVICE_TOKEN);
  String payload = String("{\"device_id\":\"") + DEVICE_ID +
                   "\",\"low\":" + (stableLow ? "true" : "false") + "}";
  int code = http.POST(payload);
  http.end();
  Serial.printf("Telemetry: %d, low=%d\n", code, stableLow);
  return code == 200;
}

void setup() {
  Serial.begin(115200);
  pinMode(SENSOR_PIN, INPUT_PULLUP);
  candidateLow = digitalRead(SENSOR_PIN) != PAPER_PRESENT_LEVEL;
  stableLow = candidateLow;
  changedAt = millis();
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  configTime(0, 0, "pool.ntp.org", "time.google.com");
}

void loop() {
  uint32_t now = millis();
  bool low = digitalRead(SENSOR_PIN) != PAPER_PRESENT_LEVEL;
  if (low != candidateLow) {
    candidateLow = low;
    changedAt = now;
  }
  if ((!ready || stableLow != candidateLow) && now - changedAt >= STABLE_MS) {
    stableLow = candidateLow;
    ready = true;
    dirty = true;
  }
  if (WiFi.status() != WL_CONNECTED && now - lastWifi >= 10000) {
    lastWifi = now;
    WiFi.reconnect();
  }
  if (ready && WiFi.status() == WL_CONNECTED && now - lastAttempt >= 5000 &&
      (dirty || now - lastSent >= HEARTBEAT_MS)) {
    lastAttempt = now;
    if (sendState()) {
      dirty = false;
      lastSent = millis();
    }
  }
  delay(20);
}
