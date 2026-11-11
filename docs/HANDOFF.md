# Güncel devir kaydı

Son güncelleme: **2026-11-11T13:02+03:00**. Kullanıcı limit nedeniyle bu oturumda yalnız arşiv/GitHub kaydı istedi; bu oturum durdu. Her devam isteğinde önce uzak main, WORK_LOG ve konuşma arşivini oku. Tamamlanan işlemleri anında commit/push et; diğer Codex’in işini koru.

## Güncel görev ve kaynak

Kullanıcı [01 / Açık ve sade](designs/01-acik-ve-sade.png) tasarımını seçti. Kaynak **0.1.4 / versionCode 5**: mint uygulama listesi, tam ekran ağ düzenleyicisi, iki ağın bağımsız taslakları, yeniden oluşturma ve listeye dönüşte durum koruması hazır. Standart 360 dp telefonda yatay satırlar; dar/büyük yazılı ekranda yığılmış satırlar kullanılıyor. Uygulama başlığı form ile kaydırılıyor; geri ve Kaydet sabit.

Güncel uygulama düzeltmesi `8ddfe06`, yeni cihaz testleri `7946d33`; diğer Codex’in derleme/60 JVM/lint başarı kaydı `240c0b2`. Bu oturum son fetch ile bunları aldı. Kısa kullanılabilir yükseklikte Geri/Kaydet birleşik satıra geçiyor. Bu yeni düzenin cihaz doğrulaması henüz kaydedilmedi.

## Doğrulananlar ve kalan iş

- 44 core + 16 app = **60 JVM testi** geçti; lint ve uygulama/test/probe APK derlemeleri başarılı.
- **3 Android JSON testi** ve **1 mint arayüz testi** iki çalıştırmada geçti. Arayüz testi `c5c2cc5`, 174,528 s; uygulama kodu `781c416`.
- Eski erişilebilirlik önbelleği, testin API 34+ taze sorgularında genel UiAutomation.clearCache API'siyle düzeltildi. Bütün değer/kaydetme assertions korundu.
- Kanıt: `docs/validation/0.1.4-ui/second-session-android-ui-passed.txt`; önceki başarısız kayıtlar tanı geçmişidir.
- **Sonraki adım (yeni devam isteğinde):** uzak main’i getir ve diğer Codex’in iki mint cihaz testinin sonucuna bak. Sonuç yoksa yeni kısa ekran düzenini yatay klavyeyle cihazda doğrula, gerçek son PNG/XML kaydını ekle. `mint-landscape-ime-before.png` eski `781c416` kusur kanıtıdır; güncel düzeltmenin başarısızlığı diye sunma. Portre `mint-apps-light.*` ve `mint-editor-light.*` aynı eski uygulama kaynağının gerçek ekranlarıdır.

## Yeni devam oturumu

11 Kasım yeni sohbeti yatay klavye düzenini üstlendi. Güncel main `cb3f115` temiz kopyalandı. Bu yeni makinede önceki araçlar/çıktılar yoktu; `/workspace/toolchains/install-android.sh` ile yeniden hazırlanıyor. Kısa kullanılabilir yükseklikte birleşik Geri/Kaydet satırı kaynakta hazır; derleme, 60 JVM ve lint kontrolü geçti (0 hata, 10 sürüm/KTX uyarısı, 2 öneri). Uygulama kaynağı `8ddfe06`, yeni cihaz testi `7946d33`. Sırada iki mint cihaz testi var; henüz cihazda sınanmadı. Yeni sohbet: [devam kaydı](conversations/2026-11-11-resume.md).

## Önceki makine (tarihsel yollar)

Depo `/workspace/uygulama-1`, dal `work`, hedef `main`. `source /workspace/toolchains/android-env.sh`. Emülatör `emulator-5554`, API 36, yazılım emülasyonu; `ro.hw_timeout_multiplier=10`. Bu makinede eski `781c416` uygulamasının mint testi geçti; etkin instrumentation testi yok. Emülatörün ekran/rotasyon/klavye ayarları eski değerlere geri alındı, uygulama kapatıldı. Yeni `8ddfe06` kaynak burada kurulmadı. Yeni günlükler `/workspace/toolchains/mint-followup/`; depodaki kanıtlar `docs/validation/0.1.4-ui/`. Başka makinede bu yolların varlığını varsayma.

## Yayımlanmış sürüm ve imza sınırı

Telefon için yayımlanmış APK hâlâ **0.1.3 / versionCode 4**: [APK](../downloads/akis-0.1.3-debug.apk), SHA-256 `32d33ea29485daa4091d6b279d94b6a73246530e0585732854e14ca4e9dd76d9`. Eski 0.1.2 ile aynı imzayı taşır. 0.1.3 native/VPN sonuçları `docs/validation/0.1.3/` altında tarihsel kanıttır.

Bu ortamın yeni debug sertifikası yayımlanmış APK'dan farklıdır; 0.1.4 APK'yı telefonun üzerine güncellenebilir diye sunma. Eski sertifika SHA-256 `09b153dbc484daf6723476aad94d4e4e1dbf614712670a502a0390ef9bffa156`. Özel anahtar Git'e/sohbete konmaz; veri korunmadan kaldırıp yeniden kurmayı çözüm olarak önerme. 0.1.4 tam VPN/fiziksel Samsung/Play Store doğrulaması yapılmadı.

## Konuşma arşivi

- [İlk Codex 10 Kasım](conversations/2026-11-10.md)
- [Bu oturumun 10 Kasım bölümü](conversations/2026-11-10-setup-followup.md)
- [11 Kasım bu oturum ve limit nedeniyle kapanış](conversations/2026-11-11.md)
- [Diğer Codex’in 11 Kasım devamı](conversations/2026-11-11-resume.md)

Bu oturumun bulut taslağı kaydı `INVALID_ARGUMENT` nedeniyle doğrulanamamıştı; başka oturumun taslak kaydıyla karıştırma. Bu platform kaydı sorunu Android kaynak geliştirmesinden ayrıdır.
