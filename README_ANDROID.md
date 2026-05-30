# SenPai Scanner — اپ اندروید

اپ اندروید SenPai Scanner — پورت کامل فاز ۱ اسکنر برای گوشی‌های بدون روت.

## قابلیت‌ها

- اسکن رندوم IP های Cloudflare (همه ۱۵ رنج IPv4)
- سه مد پروب: TCP / TLS / HTTP
- نمایش لایو نتایج با لیتنسی، packet loss، کلوکد (colo)
- پشتیبانی از CIDR سفارشی
- پارس URL کانفیگ VLESS/Trojan (پورت و مد رو خودکار تنظیم می‌کنه)
- کپی سریع IP های سالم به کلیپ‌بورد
- تم تاریک ترمینالی مشابه نسخه اصلی
- **بدون روت، بدون VPN permission** — فقط INTERNET permission

## محدودیت نسبت به نسخه Go

- فاز ۲ (xray validation) موجود نیست — نیاز به VPN permission داره که بدون روت محدودیت داره
- فاز ۱ (TCP/TLS/HTTP scan) کاملاً یکسان با نسخه اصلی

---

## ساخت APK

### پیش‌نیاز

1. **Android Studio** — از [developer.android.com](https://developer.android.com/studio) دانلود کن
2. **JDK 17** — معمولاً با Android Studio میاد
3. اینترنت برای دانلود dependency ها

### مرحله ۱ — باز کردن پروژه

```
File → Open → پوشه SenPaiAndroid رو انتخاب کن
```

صبر کن Gradle sync بشه (اولین بار چند دقیقه طول می‌کشه).

### مرحله ۲ — Build APK

```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

یا با Gradle مستقیم:

```bash
cd SenPaiAndroid
./gradlew assembleDebug
```

APK آماده اینجاست:
```
app/build/outputs/apk/debug/app-debug.apk
```

### مرحله ۳ — نصب روی گوشی

**با ADB:**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**مستقیم:**
فایل APK رو به گوشی منتقل کن و نصب کن (باید "نصب از منابع ناشناس" فعال باشه).

---

## ساخت Release APK (بهینه‌تر)

```bash
./gradlew assembleRelease
```

برای sign کردن باید keystore بسازی:
```bash
keytool -genkey -v -keystore senpaiscanner.keystore -alias senpaiscanner -keyalg RSA -keysize 2048 -validity 10000
```

---

## ساختار پروژه

```
app/src/main/java/com/senpaiscanner/
├── model/
│   └── ScanResult.kt       — data classes
├── scanner/
│   ├── IpSource.kt         — تولید رندوم IP از رنج‌های CF
│   ├── Prober.kt           — پروب TCP/TLS/HTTP (معادل prober.go)
│   ├── ScanEngine.kt       — موتور concurrent (معادل engine.go)
│   └── ConfigParser.kt     — پارس URL کانفیگ
└── ui/
    ├── MainActivity.kt     — اکتیویتی اصلی
    ├── MainViewModel.kt    — ViewModel
    └── ResultsAdapter.kt   — RecyclerView adapter
```
