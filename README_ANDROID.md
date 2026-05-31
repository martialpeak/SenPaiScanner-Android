# SenPai Scanner — Android v2.0

**یه اسکنر سبک برای پیدا کردن بهترین IPهای Cloudflare**
**A lightweight scanner to find the best Cloudflare IPs**

---

## 🔧 Build / ساخت

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

---

## 📡 حالت‌های اسکن / Scan Modes

| Mode | توضیح | Description |
|------|-------|-------------|
| **TCP** | فقط اتصال پایه — سریع‌ترین | Raw socket connect — fastest |
| **TLS** | TLS handshake کامل | Full TLS handshake |
| **HTTP** | درخواست HTTP واقعی + کشف datacenter ← **پیشنهادی** | Real HTTP + colo detection ← **recommended** |

---

## ✨ ویژگی‌های v2 / What's new in v2

- **🐛 Bug fix: TCP mode** — نتایج TCP حالا درست به عنوان healthy نمایش داده میشن
- **🐛 Bug fix: HTTP timeout** — connect timeout قبلاً `timeoutMs/4` بود، الان درست شد
- **⚡ OkHttpClient cache** — یه client مشترک با connection pool — خیلی سریع‌تر
- **🔍 Healthy Only toggle** — فقط IPهای سالم نمایش بده
- **📤 Share** — اشتراک‌گذاری IPها از طریق هر اپی
- **📊 CSV Export** — خروجی کامل با latency, loss, colo, status
- **⭐ Quality stars** — ستاره‌بندی بر اساس latency
- **🌐 Bilingual** — فارسی/انگلیسی خودکار بر اساس زبان گوشی
- **❓ Help dialog** — راهنمای دو زبانه داخل برنامه
- **🔗 Shadowsocks support** — ConfigParser حالا ss:// رو هم parse میکنه

---

## ⭐ معیار کیفیت / Quality Rating

| ستاره | Latency |
|-------|---------|
| ★★★   | < 80ms  |
| ★★☆   | 80–200ms |
| ★☆☆   | > 200ms  |
| ✗     | Unhealthy |

---

## 📋 فیلدهای خروجی / Output fields

`ip`, `port`, `latency_ms`, `loss_%`, `colo`, `http_status`, `tls_ok`

---

## 🏗 معماری / Architecture

```
MainActivity  ──→  MainViewModel  ──→  ScanEngine (Coroutines + Semaphore)
                                             ↓
                                      Prober (TCP / TLS / HTTP)
                                             ↓
                                      IpSource  (Cloudflare CIDR ranges)
                                      ConfigParser (VLESS / Trojan / VMess / ss)
```

---

## 🔐 نکات امنیتی / Security notes

- TLS certificate validation is **disabled** intentionally — this is an IP scanner, not a browser
- برای اسکن، گواهینامه‌ها validate نمیشن — این یه scanner هست نه مرورگر
- Never use `trustAll` SSL in production apps
