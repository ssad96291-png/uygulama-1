# Güncel devir kaydı

Son güncelleme: **2026-11-11T04:31+03:00**. Her devam isteğinde AGENTS, uzak main, son WORK_LOG ve konuşma arşivini oku. Kullanıcı iki Codex kullanıyor; tamamlanan her anlamlı işlemi anında commit/push et, force push yapma.

## Güncel görev ve kaynak

Kullanıcı [01 / Açık ve sade](designs/01-acik-ve-sade.png) tasarımını seçti. Kaynak **0.1.4 / versionCode 5**: mint uygulama listesi, tam ekran ağ düzenleyicisi, iki ağın bağımsız taslakları, yeniden oluşturma ve listeye dönüşte durum koruması hazır. Standart 360 dp telefonda yatay satırlar; dar/büyük yazılı ekranda yığılmış satırlar kullanılıyor. Uygulama başlığı form ile kaydırılıyor; geri ve Kaydet sabit.

Son uygulama/test kaynak değişikliği `781c416`; `db691cf` bunun başarılı derleme kaydı. Bu oturum cihaz testi tanısını ve son açık tema ekranlarını tamamlıyor. Diğer Codex'in kaynak/test düzeltmeleri alındı; iki oturumun örtüşen işi atılmadı.

## Doğrulananlar ve kalan iş

- 44 core + 16 app = **60 JVM testi** geçti; lint ve uygulama/test/probe APK derlemeleri başarılı.
- **3 Android JSON testi** ve **1 mint arayüz testi** iki çalıştırmada geçti. Arayüz testi `c5c2cc5`, 174,528 s; uygulama kodu `781c416`.
- Eski erişilebilirlik önbelleği, testin API 34+ taze sorgularında genel UiAutomation.clearCache API'siyle düzeltildi. Bütün değer/kaydetme assertions korundu.
- Kanıt: `docs/validation/0.1.4-ui/second-session-android-ui-passed.txt`; önceki başarısız kayıtlar tanı geçmişidir.
- **Kalan iş:** açık tema uygulama listesi/düzenleyici ekranlarını ve dar/yatay klavyeli görünümü doğrulayıp PNG'leri arşivle; ardından son devir notunu güncelle. Kaynak kodunu gereksiz tekrar değiştirme veya geçmiş testleri yeni neden olmadan yineleme.

## Bu makine

Depo `/workspace/uygulama-1`, dal `work`, hedef `main`. `source /workspace/toolchains/android-env.sh`. Emülatör `emulator-5554`, API 36, yazılım emülasyonu; `ro.hw_timeout_multiplier=10`. Cihazdaki son mint testi tamamlandı ve geçti; etkin instrumentation testi yok. Yeni günlükler `/workspace/toolchains/mint-followup/`; depodaki kanıtlar `docs/validation/0.1.4-ui/`. Başka makinede bu yolların varlığını varsayma.

## Yayımlanmış sürüm ve imza sınırı

Telefon için yayımlanmış APK hâlâ **0.1.3 / versionCode 4**: [APK](../downloads/akis-0.1.3-debug.apk), SHA-256 `32d33ea29485daa4091d6b279d94b6a73246530e0585732854e14ca4e9dd76d9`. Eski 0.1.2 ile aynı imzayı taşır. 0.1.3 native/VPN sonuçları `docs/validation/0.1.3/` altında tarihsel kanıttır.

Bu ortamın yeni debug sertifikası yayımlanmış APK'dan farklıdır; 0.1.4 APK'yı telefonun üzerine güncellenebilir diye sunma. Eski sertifika SHA-256 `09b153dbc484daf6723476aad94d4e4e1dbf614712670a502a0390ef9bffa156`. Özel anahtar Git'e/sohbete konmaz; veri korunmadan kaldırıp yeniden kurmayı çözüm olarak önerme. 0.1.4 tam VPN/fiziksel Samsung/Play Store doğrulaması yapılmadı.

## Konuşma arşivi

- [İlk Codex 10 Kasım](conversations/2026-11-10.md)
- [Bu oturumun 10 Kasım bölümü](conversations/2026-11-10-setup-followup.md)
- [11 Kasım devamı](conversations/2026-11-11.md)

Bu oturumun bulut taslağı kaydı `INVALID_ARGUMENT` nedeniyle doğrulanamamıştı; başka oturumun taslak kaydıyla karıştırma. Bu platform kaydı sorunu Android kaynak geliştirmesinden ayrıdır.
