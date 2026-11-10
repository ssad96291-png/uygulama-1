# İşlem ve doğrulama günlüğü

Bu kayıt kullanıcının 9 Kasım 2026 tarihli isteğiyle oluşturuldu. İlk bölüm,
mevcut Git commit'i, doğrulama dosyaları ve sohbetin doğrulanmış sonuçlarından
geriye dönük derlendi; geçmişteki her terminal komutunun eksiksiz dökümü değildir.
Bundan sonra her anlamlı işlem, sonucu ve sıradaki iş burada kaydedilir.
Tarih/saat yazılırken `Europe/Istanbul` kullanılır. Güncel durum [HANDOFF.md](HANDOFF.md)
içindedir; yeni kayıtlar dosyanın sonuna eklenir.

## 2026-11-09 — Önceki geliştirme ve ilk yedeğin özeti

| İşlem | Sonuç / kanıt |
| --- | --- |
| Android projesi ve araç zinciri kuruldu | Kotlin/Compose, Android SDK 36, minSdk 29, JDK 21, Go 1.25.5, Gradle 8.13, NDK 28.2.13676358; tekrarlanabilir adımlar README ve `tools/build-debug.sh` içinde. |
| Yerel VPN ve politika motoru geliştirildi | Uygulama başına Wi-Fi/mobil erişim, iki yönlü hız, kota ve dönemler, toplam kota, geçici izin, bildirim eşikleri, kullanım ve CSV, yedek/geri yükleme, gelişmiş görünüm, yerel bakım hesabı eklendi. Kendi sunucusu, reklam ve üyelik entegrasyonu yok. |
| Android VPN sınırı değerlendirildi | Aynı Android profili içinde ikinci bağımsız VPN ile eşzamanlı çalışma desteklenmiyor; uygulama/README bunu açıklar. |
| Native ve Android uyumluluk çalışması yapıldı | Korunan ve fiziksel ağa bağlanan TCP/UDP çıkışları, IPv4/IPv6, TUN dosya tanıtıcısı yaşam döngüsü, SDK sandbox UID eşleme, hızlı durdur/başlat ve tema sistem çubukları düzeltildi. |
| 0.1.2 otomatik doğrulaması tamamlandı | 44 core + 4 Android JVM testi; API 36 emülatöründe 1 VPN trafik + 3 JSON cihaz testi geçti. Lint 0 hata. Native 13 test fonksiyonu ve 15 alt test başarılı sonuçları kaydedildi. Ayrıntılar `docs/TESTING.md` ve `docs/validation/0.1.2/`. |
| APK üretildi ve denetlendi | `dev.akiskontrol.app`, 0.1.2 / versionCode 3; 76.694.248 bayt; SHA-256 `09881ca6c3379acf04d8e52cad6a7db2068b8c10a599a2c640343ffa6e94a5d3`. Debug imzası, 16 KB hizalama ve native ELF hizalamaları kontrol edildi. Fiziksel cihaz matrisi tamamlanmadı. |
| Kaynak ve APK yedeklemesi istendi | Kaynak, wrapper, lisanslar, doğrulama özetleri ve yalnızca güncel 0.1.2 APK kontrol edildi; parola/imzalama anahtarı, yerel önbellekler ve kullanıcının örnek dosyaları yedeğe alınmadı. |
| GitHub'a ilk commit push edildi | `7c26265` — `Add Akış 0.1.2 Android app, test APK and validation records`; uzak `main` ile commit eşleşti. API üzerinden Release yayınlama erişimi bulunmadığından APK `downloads/` içine kondu. |
| GitHub indirme bağlantısı doğrulandı | APK HTTP 200 ile yeniden indirildi; boyut ve SHA-256 yerel dosyayla aynı çıktı. Depo sayfası kullanıcıda zaman aşımına uğrayınca doğrudan `raw.githubusercontent.com` bağlantısı da indirilerek doğrulandı. |

## 2026-11-09 — Kullanıcı video ve örnek uygulama incelemesi

- Sohbete eklenen büyük video, ek aktarma aracının **32 MiB** sınırı nedeniyle
  alınamadı. Dosyayı açmış gibi bildirilmedi. GitHub Releases üzerinden paylaşım
  yolu anlatıldı.
- Kullanıcı `releases/tag/sa` bağlantısını verdi. Tek video eki indirildi:
  154.754.846 bayt, yaklaşık 4 dakika 31,6 saniye, 648×1404 HEVC; ses kanalı yok.
  Dosya özeti GitHub'daki değerle eşleşti:
  `1b0e2822815af4d6f40ca637cd52a46177690aab8f21520525b29a7e6473164e`.
  Görüntüler açılıp okunabildi. Video dosyası kaynak deposuna eklenmedi.
- Kullanıcı Wi-Fi'de diğer uygulamaların erişemediğini, mobilde yalnız Speedtest'in
  çalıştığını bildirdi. Tam videodan zaman damgalı gözlemler çıkarıldı:

| Zaman | Gözlem |
| --- | --- |
| 00:00–00:58 | Wi-Fi ve VPN açık; Speedtest'e 1 MB/s indirme, 2 MB/s yükleme sınırı veriliyor; test “Connecting…” ekranında kalıyor. |
| 01:00–01:06 | Koruma kapatılıp tekrar açılıyor; Akış “Wi-Fi kuralları etkin” gösteriyor. |
| 02:15 | Samsung Browser Wi-Fi erişimi açık, hızları sınırsız, kotası yok. |
| 02:34–02:36 | Wi-Fi kapatılıyor, 5G'ye geçiliyor, VPN anahtarı kalıyor. |
| 04:07 | SM-S921B cihazdaki mobil Speedtest 7,52 Mbps indirme, 15,9 Mbps yükleme tamamlıyor; ayarlanan sınırlara uygun. |
| 04:17–04:25 | Samsung Browser Google ve Amazon için “Bağlantı sıfırlandı” gösteriyor. Bu hata tek başına DNS bozukluğu kanıtı değil. |

- `Speed Limiter_22.0_com.speedlimiter.zip` karşılaştırma için güvenli biçimde
  listelendi/manifesti incelendi; APK çalıştırılmadı ve kod/görseller kopyalanmadı.
  Paket `com.speedlimiter`, versionCode 22 / sürüm 22.0, minSdk 24 / targetSdk 36;
  `VpnService`, otomatik bağlantı ve hızlı ayarlar servisleri var. APK'da `.so`
  dosyası görülmemesi tek başına ağ mimarisini belirlemiyor. Reklam/Billing
  bileşenleri örnekte var; Akış'a eklenmedi.

## 2026-11-09 — Bağlantı regresyonu çalışması, checkpoint

Durum: **devam ediyor; yeni APK ve Android cihaz sonuçları henüz tamamlanmadı.**

- Önceki cihaz testlerinin doğrudan IP kullandığı, Android sistem DNS'i ve tarayıcı
  davranışını kapsamadığı belirlendi. İlk DNS şüphesi doğrulanmış neden olarak
  sunulmadı; bağlantı sıfırlama, IPv6/TLS/soket/sahiplik yolları da araştırıldı.
- AOSP sahiplik incelemesinde `getConnectionOwnerUid` yolunun IPv4/IPv6 ve UDP
  uzak wildcard durumlarını zaten ele aldığı görüldü. Kanıtsız genişletilmiş
  sahiplik eşlemesi veya bilinmeyen UID'ye toplu izin verilmedi.
- Somut kusur saptandı: 0.1.2 fiziksel ağdan bağımsız IPv6 TUN adresi/rotası
  yayımlıyor. Pinned tun2socks/gVisor kodunda TCP el sıkışması korunan çıkış soketi
  bağlanmadan tamamlanıyor. IPv4-only ağda bu, uygulamanın IPv6'yı başarılı sanıp
  sonradan reset almasına ve IPv4'e dönüşün gerçekleşmemesine yol açabilir.
- Android düzeltmesi hazırlandı: fiziksel ağın kullanılabilir IPv6 adresi ve
  varsayılan rotası kontrol ediliyor; adres ailesi değişiminde TUN önce yenilenip
  sonra eskisi kapatılıyor; aynı `Network` için `LinkProperties` değişimleri de
  yeniden yapılandırılıyor. `IpSupportTest` için beş senaryo eklendi.
- Native motora hedef/adres/payload içermeyen, kategori bazında tekrarları
  sınırlanmış tanı kayıtları ve gerçek TLS 1.2/1.3 × IPv4/IPv6 aktarım testleri
  eklendi. Native ajan `go test -race ./mobile` başarısını bildirdi; yeni Android
  APK'nın tamamı için başarı iddiası yapılmadı.
- `tools/probe_dns_server.py` eklendi. Loopback DNS fixture'ı yalnızca
  `*.akis.test.invalid` alanını emülatör host adresine çözüyor; yerel fixture
  yanıtı kontrol edildi. İnternete açık DNS çözümleyici değil.
- Ayrı UID'li probe'a alan adı/DNS ve ulaşılamayan IPv6 bağlantısı senaryoları;
  Android testine VPN öncesi/sırası/sonrası hostname bağlantısı, engel ve tekrar
  izin adımları eklendi. Probe ve instrumentation derlemesi başarılı oldu.
- API 36 yazılım emülatörü DNS fixture ile yeniden başlatıldı. Yavaş framework
  için mevcut test ortamı zaman aşımı ayarları kullanılıyor. Önce eski APK ile
  regresyonu gösterme, ardından yeni APK ile aynı testi geçirme sonucu bekleniyor.
- Native AAR derlemesinde `gomobile init` dolaylı `gobind@latest` yükleyerek Go
  1.26 talep etti. Sabit sürümlü araç zincirini koruyan build betiği düzeltmesi
  ve yeniden derleme yürütülüyor; ağ politikası değiştirilmedi.
- Kullanıcı bütün işlemlerin GitHub'da kaydedilmesini ve her devamda uzak deponun
  kontrol edilmesini istedi. `AGENTS.md`, bu günlük ve `HANDOFF.md` oluşturuldu.
  Uzak ana dal açık refspec ile getirildi; kayıt oluşturulurken yerel HEAD ile
  `origin/main` aynı `7c26265` commit'indeydi. Bu checkpoint'in push işlemi ve
  sonraki test sonuçları takip eden kayıtta belirtilmelidir.

## Yeni kayıt biçimi

Yeni işlemleri bu bölümün altına ekle. Her kayıtta şu bilgileri ver:

- Tarih/saat (`Europe/Istanbul`) ve işlem.
- Değişen dosyalar veya çalıştırılan anlamlı kontrol.
- Sonuç: geçti / başarısız / sürüyor / engelli; varsa kısa hata ve kanıt yolu.
- GitHub checkpoint/dal bilgisi ve kesin sonraki adım.

## 2026-11-09 — İlk checkpoint öncesi derleme güncellemesi

- Yeni geliştirme sürümü **0.1.3 / versionCode 4** olarak ayarlandı; son
  paylaşılan APK hâlâ 0.1.2. Native AAR derlemesi, sabit araç sürümü düzeltmesinden
  sonra tamamlandı; native TLS testleri başarılı.
- **53 JVM testi (44 core + 9 app) geçti.** Tam Gradle kontrolü lint sırasında
  `RouteInfo.getType` için API 33 gereksinimini yakaladı; minSdk 29 uyumluluk
  düzeltmesi devam ediyor. Yeni APK için başarı ilan edilmedi.
- Çoklu hesap/ortam geçişinde debug anahtarı değişirse APK güncellemesinin
  imza uyuşmazlığı nedeniyle kurulamayacağı kaydedildi. Anahtarlar depoya
  eklenmeyecek; sonraki APK'nın sertifikası önceki APK ile karşılaştırılacak.
- Eski 0.1.2 üzerinde Android DNS/IPv6 regresyon testi, emülatör framework'ünün
  hazır olması beklenerek yürütülüyor; sonuç henüz alınmadı.

## 2026-11-09 19:19 +03 — İlk sürekli yedek ve API 29 düzeltmesi

- `55f554b` checkpoint'i `main` dalına push edildi; `git ls-remote` ile uzak commit doğrulandı. Kaynak değişiklikleri, geçmiş günlüğü ve oturum devam talimatları artık GitHub'da. Yeni APK henüz paylaşılmadı.
- Android lint, `RouteInfo.getType` çağrısının API 33 gerektirdiğini yakaladı. AOSP Android 10 `RouteInfo.isDefaultRoute()` uygulamasının zaten `RTN_UNICAST` ve sıfır önek uzunluğunu kontrol ettiği doğrulandı; fazladan API 33 çağrısı kaldırıldı. API 29 desteği lint bastırılarak geçiştirilmedi.
- 44 core + 9 app JVM testi geçti. Düzeltilmiş kaynakta lint/derleme tekrar doğrulanıyor; native sonuç kaydı alınıyor.
- Emülatör açıldı, Wi-Fi bağlı. Ayrı test oturumu eski **0.1.2** APK ve yeni probe/test APK ile DNS/IPv6 regresyonunu çalıştırıyor; sonucu henüz yok.

## 2026-11-09T19:22+03:00 — Uyumluluk kontrolü geçti, native kanıt kaydı

- API 33 bağımlılığı kaldırıldıktan sonra `:app:testDebugUnitTest :app:lintDebug` başarılı oldu; 0 lint hatası.
- IPv6 desteği hesabı eski site-local `fec0::/10` adreslerini de dışlıyor. AOSP API 29 `LinkAddress` bu adresleri Internet kapsamı yerine `RT_SCOPE_SITE` olarak sınıflandırıyor. ULA `fd00::/8` davranışı korunuyor; bu ölçüt AOSP `isGlobalPreferred` işlevinin birebir kopyası değil. Altıncı IPv6 destek testi eklendi; artık **44 core + 10 app = 54 JVM testi geçti**.
- Native motor için **16 test fonksiyonu ve 19 alt test** race detector ile geçti. TLS 1.2/1.3 × IPv4/IPv6 gerçek şifreli aktarımı dahil sonuç `docs/validation/0.1.3/native-tests.jsonl` içinde. Native AAR üretildi.
- Eski APK karşılaştırmasının ilk adımında VPN kapalıyken DNS+hostname HTTP başarılı, ulaşılamayan IPv6 bağlantısı başarısız. VPN açık kısmı sürüyor. Emülatör Wi-Fi'sinin site-local IPv6 bilgisi taşıdığı ayrıca kaydedildi; fiziksel ağ sonucu olarak sunulmayacak.
- Yeni APK için birleşik derleme/lint/test yeniden çalıştırılıyor. `docs/validation/0.1.3/progress.json` yalnızca ara durumdur; cihaz doğrulaması ve APK paylaşımı henüz tamamlanmadı.

## 2026-11-09T19:39+03:00 — Eski sürüm hatası testle tekrarlandı

- “Devam” sonrası uzak `main` tekrar getirildi: `5fe7039`, yerel/uzak ayrışma yok. Önceki checkpoint push'u doğrulandı.
- Eski 0.1.2 APK'da yeni Android testi **beklenen regresyon nedeniyle başarısız** oldu (146,827 s). VPN kapalı: DNS+HTTP başarılı, ulaşılamayan IPv6 connect başarısız; VPN açık: aynı DNS+HTTP başarılı, fakat aynı ulaşılamayan IPv6 connect **651 ms'de başarılı sanılıyor**. Kaynakta bulunan sahte IPv6 el sıkışması kusuru cihaz testinde tekrarlandı. Kayıt: `docs/validation/0.1.3/regression-before-0.1.2.txt`. Bu sonuç kullanıcının telefonundaki tek nedenin aynı olduğunu kanıtlamaz.
- 0.1.3 için birleşik 54 JVM testi/lint/APK/test paketi derlemesi başarılı. APK imzası önceki sürümle aynı; 16 KB ZIP hizalaması geçti. Aday dosya 76.857.067 bayt, SHA-256 `32d33ea29485daa4091d6b279d94b6a73246530e0585732854e14ca4e9dd76d9`; henüz indirilecek APK olarak yayımlanmadı.
- Test probe'unda yalnız DNS/bağlantı kontrolünü yapay bir byte sayısı gibi raporlama kaldırıldı; gerçek aktarılan byte miktarı ayrı kaldı. Probe yeniden derlendi. Bu değişiklik ana APK'yı değiştirmiyor.
- Yeni 0.1.3 + probe + instrumentation APK'ları kurulup **5 testlik Android paketi** başlatıldı. Sonuç bekleniyor; yeni cihaz sonucuna göre sonraki adım ağ geçişi kontrolü ve teslim.

## 2026-11-09T19:40+03:00 — Bağımsız inceleme ve Wi-Fi/mobil geçiş testi

- `7581c83` checkpoint'i GitHub main üzerinde doğrulandı. Yeni 0.1.3 ve test paketlerinin emülatöre kurulması başarılı; mevcut 5 test çalışıyor.
- Bağımsız kod incelemesinde TUN değişimi ve native tanı kayıtlarında yeni engelleyici kusur bulunmadı. Sınır kaydedildi: fiziksel IPv6 adresi+rota, gerçek Internet erişimini tek başına garanti etmez; global/ULA IPv6 blackhole durumları ve Samsung telefon testi hâlâ ayrıca değerlendirilmelidir.
- Test açıklamasındaki fazla kesin “IPv4-only fixture” ifadesi düzeltildi: Wi-Fi fixture'ı site-local IPv6 taşıyor, test hedefi erişilemez. Eski regresyonun sahte TCP başarısı kanıtı değişmedi.
- `hostnameConnectionsFollowWifiAndMobileRules` cihaz testi eklendi: Wi-Fi engelli/mobil izinli, mobilde alan adıyla erişim ve yanlış IPv6 el sıkışmasının reddi, sonra mobil engelli/Wi-Fi izinli ve Wi-Fi'ye dönüş. Bu ek test henüz çalıştırılmadı; ilk 5 test bitince ayrı çalıştırılacak. Ana APK bu test değişikliğiyle değişmiyor.

## 2026-11-09T19:43+03:00 — Test çalıştırma belgesi ve APK paket kontrolleri

- `cc6321c` GitHub main üzerinde doğrulandı. Yeni Wi-Fi/mobil test paketi derlendi; henüz kurulu çalışan test paketinin bitmesi bekleniyor.
- 0.1.3 üzerinde mevcut gerçek paket/hız/kota/yeniden başlatma cihaz testi geçti; aynı çalıştırmada hostname/IPv6 testi başladı. Tam paket sonucu henüz tamamlanmadı.
- APK manifestinden 0.1.3/versionCode 4 ve üç ABI doğrulandı. Tüm dört 64-bit native kütüphanenin ELF LOAD hizalaması en az 16 KB; APK ZIP hizalaması ve önceki sürümle aynı imza daha önce doğrulanmıştı.
- `docs/TESTING.md` güncellendi: yerel DNS fixture kurulumu, altı güncel cihaz testinin kapsamı, emülatör hücresel ağ gereksinimi ve fiziksel IPv6 erişimi sınırları açıklandı. Yeni cihaz sonuçları tamamlanmadan yayın hazır iddiası yok.

## 2026-11-09T19:45+03:00 — Ağ geçişi doğrulaması sıraya alındı

- `b912073` GitHub main üzerinde doğrulandı. Ek test paketi derlemesi başarılı; ana APK özeti hâlâ `32d33ea29485daa4091d6b279d94b6a73246530e0585732854e14ca4e9dd76d9`.
- İlk beş testin tamamının geçmesini bekleyen `run-handoff-test.py` başlatıldı. Başarı gelince yalnız test/probe paketlerini güncelleyip Wi-Fi→mobil→Wi-Fi regresyonunu çalıştıracak; ilk paket başarısızsa ilerlemeyecek. Çalışan süreç ve log konumları devir notuna eklendi; başka oturum aynı emülatörde testi çakıştırmamalı.

## 2026-11-09T19:46+03:00 — 0.1.3 ilk beş Android testini geçti

- `67816c3` ara kaydı GitHub main üzerinde doğrulandı.
- 0.1.3 üzerinde **OK (5 tests)**, 389,009 saniye: gerçek TCP/UDP, Wi-Fi engeli, iki protokolde hız, kota, durdur/başlat, hostname DNS/HTTP, sahte IPv6 bağlantısının önlenmesi ve üç JSON/yedek testi geçti. Android 16/API 36 yazılım emülatörü; bekleme ayarları 120.000 ms / çarpan 10, trafik başarı koşulları değiştirilmedi.
- Eski sürüm aynı IPv6 regresyonunda başarısızken yeni sürüm geçti. Test paketi, 14 gerçek probe sonucu, lint ve ELF hizalama kanıtları `docs/validation/0.1.3/` altına alındı.
- Ek Wi-Fi→mobil→Wi-Fi testi otomatik olarak sıradaki aşamaya geçti; test/probe paketleri güncelleniyor. Ana APK aynı imzalı ve aynı SHA-256 değerindeki dosya. Telefon üzerindeki Samsung doğrulaması hâlâ bekliyor.

## 2026-11-09T19:51+03:00 — Altı Android testi tamamlandı, 0.1.3 teslimi hazırlanıyor

- `8d00a84` checkpoint'i GitHub main üzerinde doğrulandı. Ek Wi-Fi→mobil→Wi-Fi testi **OK (1 test)** ile 198,864 saniyede geçti. Wi-Fi engeli, mobil izinli hostname aktarımı, ulaşılamayan IPv6 hedefinin reddi, mobil engeli ve Wi-Fi'ye dönüşte izinli aktarım doğrulandı. Beş ek probe sonucu kaydedildi.
- Aynı 0.1.3 ana APK üzerinde toplam **6 Android testi** iki çalıştırmada geçti (5 + 1); 54 JVM ve native 16 fonksiyon/19 alt test sonuçlarıyla `verification.json` oluşturuldu. Ana APK değişmedi.
- APK ve SHA-256 dosyası `downloads/` içine hazırlandı: 76.857.067 bayt, `32d33ea29485daa4091d6b279d94b6a73246530e0585732854e14ca4e9dd76d9`. İmza 0.1.2 ile aynı, ZIP ve 64-bit ELF hizalamaları 16 KB uyumlu.
- README, indirme açıklaması ve test belgesi 0.1.3'e güncellendi. Samsung SM-S921B ve gerçek operatör yeniden testi bekliyor; tüm cihazlarda sorunsuzluk iddiası yok. GitHub push ve gerçek indirme doğrulaması sıradaki son adım.

## 2026-11-09T19:54+03:00 — 0.1.3 GitHub teslimi ve indirme doğrulaması tamamlandı

- Kaynak, 0.1.3 APK, test kanıtları ve devir notu `14b4be9` commit'iyle GitHub `main` dalına yüklendi. Uzak commit `git ls-remote` ile eşleştirildi. GitHub'ın 50 MB öneri uyarısı yüklemeyi engellemedi.
- APK doğrudan raw bağlantısından yeniden indirildi: **HTTP 200**, **76.857.067 bayt**, SHA-256 `32d33ea29485daa4091d6b279d94b6a73246530e0585732854e14ca4e9dd76d9`. `cmp` ile yerel test edilen dosyayla birebir aynı olduğu doğrulandı; bağlantı [burada](https://raw.githubusercontent.com/ssad96291-png/uygulama-1/main/downloads/akis-0.1.3-debug.apk).
- `verification.json` ve ara durum dosyası teslim edildi olarak güncellendi. Tüm test süreçleri tamamlandı; sıradaki iş kullanıcının Samsung telefonunda 0.1.3 denemesinden gelen sonucu değerlendirmek. Aynı imza sayesinde mevcut uygulama kaldırılmadan güncellenebilir.

## 2026-11-09T20:14+03:00 — Görsel tasarım alternatifleri hazırlığı

- Kullanıcı Akış için birkaç uygulama tasarımını resim olarak istedi. Mevcut APK üzerinde arayüz uygulaması istenmedi; bu aşama görsel konsept hazırlığıdır.
- AGENTS ve güncel devir kaydı okundu, uzak main açık refspec ile getirildi. Yerel/uzak aynı `978cdb6` commit'inde; başka oturumun ek değişikliği ve kirli çalışma dosyası yoktu.
- Üç yön planlandı: açık Sade Mint, grafit/camgöbeği Koyu Kontrol, sıcak krem/indigo Renkli Kartlar. Uygulama bazlı Wi-Fi/mobil izinleri, hız sınırı ve kota arayüzü gösterilecek. Görsel üretimi bu kayıt anında bekliyor; tamamlandı veya teslim edildi olarak işaretlenmedi.
- Yalnız bu günlük ve HANDOFF güncellendi. Uygulama kodu, APK ve önceki test kanıtları değişmedi; 0.1.3 için Samsung telefonda yeniden test sonucu ayrıca bekleniyor.

## 2026-11-09T20:19+03:00 — Üç görsel tasarım tamamlandı ve yedeklendi

- Kök ajan üç tasarım görselinin başarıyla üretildiğini ve kullanıcıya sohbette sunulduğunu doğruladı: açık mint uygulama listesi/kural düzenleyici, koyu grafit/camgöbeği kontrol paneli/gelişmiş düzenleyici, krem/menekşe büyük kartlar/basit düzenleyici.
- Uzak main yeniden getirildi; yerel/uzak aynı `a7a29e0` commit'inde ve çalışma ağacı temizdi. Görseller özgün dosyalar değiştirilmeden `docs/designs/` altına kopyalandı; her kopya byte düzeyinde doğrulandı.
  - `01-acik-ve-sade.png` — 1465127 bayt; SHA-256 `96e07d6d0e914956b490cde8901421d56acbc726e06b76107cfc5af6345736cf`.
  - `02-koyu-ve-ayrintili.png` — 1691963 bayt; SHA-256 `9692b2cdd0bfdfe329ea8f31e444853a151092d18e18b5d3e65f91d3bec9419c`.
  - `03-renkli-ve-kolay.png` — 1601608 bayt; SHA-256 `4b1abdfb8eab4643be9f6291647dc7d423f0604b4fc3d77cd8a8638c63d53842`.
- `docs/designs/README.md` seçenekleri ve iki ekranlık kapsamlarını gösterir. HANDOFF güncellendi: kullanıcı seçimi/revizyonu bekleniyor; uygulama kodu veya APK değiştirilmedi. 0.1.3'ün önceki testleri yeniden çalıştırılmadı; Samsung fiziksel test sonucu ayrıca bekleniyor.

## 2026-11-10T07:43+03:00 — Açık mint seçimi ve anında GitHub kaydı

- Kullanıcı `01 / AÇIK VE SADE` tasarımını uygulama talimatı verdi; iki Codex arasında devam için her anlamlı işlemden sonra anında commit/push ve kullanıcı/asistan mesajları ile görsellerin arşivini istedi.
- Uzak main getirildi: `dc0ff63`; yerel/uzak ayrışma 0/0, çalışma ağacı temizdi. Seçilen tasarım depodaki `docs/designs/01-acik-ve-sade.png` ile görsel olarak eşleşiyor; yeni yüklemenin ham dosyası erişilebilir değil.
- AGENTS, tasarım seçimi ve HANDOFF güncellendi. Konuşma arşivi ve arayüz dosyaları hazırlanıyor; yeni uygulama kodu henüz doğrulanmadı.

## 2026-11-10T07:44+03:00 — Açık mint tema ve konuşma arşivi checkpoint

- Ortak tema beyaz/mint renkler, daha sade tipografi ve ince kenarlıklı panellerle güncellendi. Yeni kurulumlarda açık tema varsayılan; mevcut kullanıcının kayıtlı tema tercihi korunuyor. Geliştirme sürümü 0.1.4/versionCode 5 oldu.
- Erişilebilir kullanıcı/asistan metinleri `docs/conversations/` altında, önceki kurulumda incelenen üç ekran görüntüsü de media dizininde arşivlendi. Başka hesapların erişilemeyen geçmişi ve yeni ekin byte düzeyinde kopyası varmış gibi gösterilmedi.
- Bu kaynak checkpoint’i henüz derlenmedi/test edilmedi. Uygulama listesi ve tam ekran kural düzenleyicisi paralel dosyalarda hazırlanıyor.

## 2026-11-10T13:02+03:00 — Uygulama listesi ve tam ekran kural düzenleyici checkpoint

- “Devam” isteğinde uzak main yeniden kontrol edildi: `3c9cc63`, ayrışma 0/0. Yarım kalan yerel arayüz dosyaları korundu; diğer oturumdan yeni uzak commit yoktu.
- Akış marka satırı, koruma kartı, günlük ağ kullanımı, satır düzeninde Wi-Fi/mobil anahtarları ve toplu düzenleme eklendi. Mevcut filtreler, geçici izinler ve geri alma menüden erişilebilir.
- Uygulamaya özel düzenleyici tam ekran; ağ sekmeleri, hız birimleri, günlük/haftalık/aylık kota, gerçek kullanım çubuğu ve gelişmiş ayarlar korunuyor. Alt gezinme mint seçimiyle güncellendi; açılış ekranı Uygulamalar oldu.
- Bu entegrasyon checkpoint’i henüz derlenmedi/test edilmedi. Sıradaki adım derleme, inceleme düzeltmeleri ve emülatör doğrulaması.

## 2026-11-10T13:04+03:00 — Düzenleyici gezinme durumunun korunması

- Düzenlenen uygulama kimliği `rememberSaveable` ile saklanıyor; Android ekran yeniden oluşturulunca aynı uygulamanın düzenleyicisi açılıyor. Taslak alanların da geri yüklenmesi ve sekme erişilebilirliği üzerinde inceleme sürüyor.
- İlk derleme/doğrulama çalıştırması devam ediyor; henüz yeni tasarımın tamamı için başarılı test sonucu verilmedi.

## 2026-11-10T13:06+03:00 — İlk derleme geçti; taslak koruma ve erişilebilirlik

- İlk mint arayüz derlemesi `BUILD SUCCESSFUL in 1m 21s`: mevcut 54 JVM testi, lint, debug APK ve Android test APK derlemesi geçti (`/workspace/toolchains/mint-ui/build.log`). Native motor kaynakları değiştirilmedi.
- İki ağdaki tamamlanmamış/hatalı girişler dahil düzenleme taslakları artık Android yeniden oluşturulmasında saklanıyor. Sekmeler seçili durumunu erişilebilirlik hizmetlerine bildiriyor; dokunma alanı en az 48 dp.
- Dönüşüm, geçersiz giriş, bağımsız ağlar ve taslak geri yükleme için 6 yeni JVM testi eklendi. Bu son iyileştirmeler/yeni testler henüz çalıştırılmadı; sonraki birleşik doğrulamada ölçülecek.
