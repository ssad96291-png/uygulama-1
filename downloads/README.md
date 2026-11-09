# Test APK'sı

[Akış 0.1.3 APK'yı indir](https://raw.githubusercontent.com/ssad96291-png/uygulama-1/main/downloads/akis-0.1.3-debug.apk).

Android 10 ve üzeri için debug imzalı alfa sürümü. Paket `dev.akiskontrol.app`, sürüm kodu `4`. 0.1.2 ile aynı sertifikayı kullanır; mevcut kurulum üzerine güncellenebilir.

SHA-256:

```text
32d33ea29485daa4091d6b279d94b6a73246530e0585732854e14ca4e9dd76d9
```

54 JVM testi, native TLS/race testleri ve Android 16 emülatöründe 6 cihaz testi geçti. Eski 0.1.2'de tekrarlanan sahte IPv6 bağlantısı hatası 0.1.3'te giderildi; Wi-Fi/mobil geçiş kuralları da sınandı. [Doğrulama kaydı](../docs/validation/0.1.3/verification.json).

Samsung telefon ve gerçek operatör ağında tekrar kontrol bekleniyor; [test sınırları](../docs/TESTING.md). 0.1.2 dosyası yalnız eski hata karşılaştırması için saklanıyor. İmzalama anahtarı depoda değildir; başka ortamda APK üretirken [imza sürekliliği notunu](../docs/HANDOFF.md) oku.
