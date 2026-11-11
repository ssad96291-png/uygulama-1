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

## 2026-11-10T13:10+03:00 — 60 JVM testi geçti; gerçek arayüz testi eklendi

- `:core:test` 44/44, `:app:testDebugUnitTest` 16/16: toplam 60 test, sıfır hata/atlama. Lint ve 0.1.4 APK derlemesi geçti (`build-final.log`, 30 s).
- Gerçek Uygulamalar → ağ düzenleyici akışı için `MintUiDeviceTest` eklendi: bağımsız ağ taslakları, Activity yeniden oluşturma, kaydetme, geçersiz girişin reddi ve geri dönüş. Bu cihaz testi henüz çalıştırılmadı.
- İlk mint APK emülatöre kuruldu/açıldı (43 s yazılım emülatörü). İlk UI hiyerarşisi alındı; düzenleyiciye geçiş anındaki dump null root verdi, uygulama hatası olup olmadığı cihaz kaydı/sonraki görüntüyle inceleniyor.

## 2026-11-10T15:36+03:00 — İkinci Codex: güncel mint çalışmasından devam

- Kullanıcı her anlamlı işlemin hemen GitHub'a kaydını, kullanıcı/asistan yazışmaları ve görsellerin arşivini yeniden istedi. Bu kural mevcut AGENTS içinde zaten var.
- Yerel temiz `dc0ff63` kopyası, uzak main incelendikten sonra `3da6a52` commit'ine fast-forward edildi. Diğer Codex'in mint arayüzü, taslak koruması ve cihaz testi korundu.
- Bu oturum tamamlanmamış `MintUiDeviceTest` doğrulamasını ve ekran kanıtlarını üstleniyor; kaynak değişikliği gerekirse önce uzak dal tekrar kontrol edilecek. Diğer oturumun sahiplik notu kilit kabul edilmiyor.
- Önceki bu-makine kurulum sonuçları eski `dc0ff63` içindi. Şimdiki kaynak için gerekli derleme ve yeni arayüz testi henüz çalıştırılmadı. Konuşmanın bu oturuma ait tam erişilebilir bölümü ayrı arşiv dosyasına hazırlanıyor.

## 2026-11-10T15:37+03:00 — Bu oturumun erişilebilir konuşma arşivi

- Kullanıcının isteğiyle bu oturumdaki erişilebilir doğal dil mesajları, asistan ilerleme/sonuç metinleri ve güncel kullanıcı isteği `docs/conversations/2026-11-10-setup-followup.md` içine sıra ve konuşmacıyla kaydedildi. Diğer Codex'in konuşması değiştirilmedi.
- Seçilen mint görseli mevcut özgün depo dosyasına ve SHA-256 özetine bağlandı; bu oturuma yeni dosya eki gelmediği açıkça belirtildi. Kurulumdaki UI XML kontrolleri yeni PNG gibi sunulmadı.
- Yeni tasarımın bu makinedeki Gradle doğrulaması sürüyor. İncelemede tam ekran düzenleyiciden listeye dönünce arama/filtre durumunun kaybolduğu belirlendi; düzeltme henüz uygulanmadı.

## 2026-11-10T18:32+03:00 — İlk Codex: diğer oturum kayıtları alındı, UI testi tanısı

- Uzak `be0839d` (ikinci Codex’in iki belge kaydı) temiz yerel kopyaya fast-forward edildi. İkinci oturumun arama/filtre korunması incelemesi korundu; bu oturum o uygulama listesi değişikliğini üstlenmiyor.
- API 36 cihaz paketi 4 test çalıştırdı: 3 JSON testi geçti; MintUiDeviceTest ilk hız alanını `12` olarak okuma adımında başarısız oldu (190,933 s). Kanıt `docs/validation/0.1.4-ui/android-tests-initial-failure.txt`. İlk değerin görüntülenmesi/erişilebilirlik seçimi inceleniyor; uygulama kuralı hatası henüz kanıtlanmadı.
- İlk mint koyu tema/başlangıç görüntüleri sohbet media dizinine özetleriyle eklendi. Hedef açık tema son görüntüleri henüz kaydedilmedi.
- Bu oturum test alanı erişimi tanısı ve açık ekran kanıtları üzerinde çalışıyor; diğer oturum kaynak değişikliklerini korumak için her push öncesi uzak main yeniden kontrol edilecek.

## 2026-11-10T18:36+03:00 — UI testi seçici hatasının nedeni doğrulandı

- Android UI XML: `İndirme değeri` content-description düğümü düzenlenebilir alanın çocuğu; gerçek `android.widget.EditText` ve metin onun üst düğümünde. Eski test yalnız alt düğüm aradığı için ilk değer kontrolüne ulaşamıyordu.
- Test yardımcısı etiketin en yakın EditText üst düğümünü bulacak şekilde düzeltildi; tüm değer/kaydetme/koruma doğrulamaları tutuldu. İlk kural fixture doğrulaması ve gerçek son gözlemi bildiren hata metni eklendi. Uygulama sayısal dönüşümünde kusur saptanmadı.
- Düzeltme henüz cihazda çalıştırılmadı; yalnız etkilenen UI testi yeniden derlenip sınanacak.

## 2026-11-10T18:41+03:00 — Kaynak sürüm belgesi ve liste durumunun korunması

- README/TESTING yeni 0.1.4 kaynak arayüzünü, 60 JVM testini ve cihaz testi durumunu 0.1.3 dağıtımından ayıracak şekilde güncellendi; yayımlanmamış APK yeni indirme gibi sunulmadı.
- Diğer Codex’in saptadığı listeye dönüşte arama/filtre kaybı, uzak dal tekrar kontrol edildikten sonra bu oturumda ortak gezinme katmanına SaveableStateHolder eklenerek giderildi. Ekranların rememberSaveable durumları düzenleyiciye gidip dönerken korunuyor. Bu yeni katman henüz derlenmedi/test edilmedi.
- Hâlen çalışan cihaz testi önceki kurulu APK üzerinde; yeni kaynakla karıştırılmayacak. Düzeltilmiş seçici yeni testte alanlara ulaşabiliyor, nihai runner sonucu bekleniyor.

## 2026-11-10T18:43+03:00 — Activity yeniden oluşturma test izlemesi düzeltildi

- Üst EditText seçicisiyle ilk değer ve iki ağdaki taslak girişleri geçildi. İkinci deneme, `activity.recreate()` sonrasında launch odaklı Instrumentation.ActivityMonitor yeni örneği bildirmediği için başarısız oldu (231,127 s). Görselde mobil 5 KB/sn taslağı duruyordu; bu ekran tek başına test başarısı diye sunulmadı.
- Test artık AndroidX ActivityLifecycleMonitorRegistry üzerinde RESUMED aşamasındaki farklı MainActivity örneğini ana thread’den bekliyor. Yeni örnek, iki ağ değeri, kaydetme ve geçersiz giriş doğrulamaları korundu. SaveableStateHolder için listeye dönüşte aramanın korunduğu da doğrulanacak.
- Başarısız runner kaydı ve test sırasında incelenen gerçek ekran `docs/validation/0.1.4-ui/` içine eklendi. Yeniden doğrulama bekleniyor.

## 2026-11-10T18:59+03:00 — İkinci Codex: eşzamanlı düzeltmeler birleştirildi, dar ekran düzeltmesi

- “Devam” ile uzak main tekrar incelendi: `b7d9d41`. Diğer oturum SaveableStateHolder ve cihaz testinin EditText/lifecycle seçicilerini düzeltmiş. Bu oturumun örtüşen iki yerel taslağı `/workspace/toolchains/mint-followup/local-list-state.patch` ile korundu, aynı düzeltmenin uzak sürümü fast-forward ile alındı.
- Önceki `3da6a52` kaynak derlemesi bu makinede 1m 3s içinde geçti: 60 JVM testi, lint, uygulama/test/probe APK. Bu sonuç aşağıdaki yeni değişikliğin cihaz doğrulaması değildir.
- Dar/yatay ekranda klavye açıkken sabit başlığın form alanını tüketmemesi için uygulama kimliği başlığı kaydırılan alana taşındı. Geri ve Kaydet sabit; portre yatay girintisi ve başlık-sekme aralığı korundu.
- Cihaz testi düzenleyici varlığını kayabilen başlık yerine sabit Geri kontrolüyle izliyor; geçersiz taslağı iptal edince aramanın da korunduğunu kontrol ediyor. Bu checkpoint henüz derlenmedi/test edilmedi.

## 2026-11-10T19:00+03:00 — İkinci Codex: birleşik kaynak derlemesi geçti

- `966a330` kaynak koduyla Gradle uygulama/test/probe APK, lint ve JVM kontrolleri `BUILD SUCCESSFUL in 1m` verdi. 44 core + 16 app = 60 test; başarısız/atlanmış test yok.
- Kanıt `docs/validation/0.1.4-ui/second-session-build.txt`, sayımlar `second-session-progress.json`. Bu sürümün cihaz testi ve dar ekran kontrolü sırada; native motor kaynakları değişmediği için aynı Go testleri tekrar edilmedi.

## 2026-11-10T19:06+03:00 — İkinci Codex: cihaz testi seçici tanısı

- Güncel `966a330` APK üzerinde 4 cihaz testi çalıştı: 3 JSON testi geçti, mint UI testi mobil erişim anahtarının content-description seçicisinde başarısız oldu. Runner kaydı `second-session-android-initial.txt`.
- Test sırasında alınan gerçek görüntü `second-session-mobile-selector.png`: mobil sekmesi seçili, alanlar 21/9 KB/sn; görüntü test başarısı değildir. Test finally aşamasında önceki koyu tema tercihini geri yükleyebildiğinden açık mint teslim ekranı olarak sunulmaz.
- İlk testten sonra UI dump launcher'a ait çıktı; bunu uygulama seçicisinin kanıtı gibi kullanmadık. Uygulama yeniden açılıp erişilebilirlik düğümü doğrudan incelenecek.

## 2026-11-10T19:12+03:00 — Görünür kontrol önkoşulu ve standart telefon satırları

- Gerçek UI ağacında mobil erişim etiketi hem açık hem kapalı anahtarda doğru bulundu (`second-session-mobile-on.xml`, `second-session-mobile-off.xml`). Ağ etiketi/dönüşüm kusuru kanıtlanmadı; başarısız test anının XML'i önceki sürümde alınmamıştı.
- Test ağ kontrollerinden önce klavyeyi kapatıp formu yukarı kaydırıyor; mevcut değer, iki ağ, yeniden oluşturma, kaydetme/iptal assertions korunuyor. Sonraki görünür düğüm hatasında test bitmeden UI ağacı kaydedilecek. Bu değişiklik henüz cihazda doğrulanmadı.
- 360 dp telefonun iç genişliği 320/312 dp olduğundan eski 340/332 dp eşikleri standart telefonlarda da iki satıra düşüyordu. Eşikler 308/300 dp yapıldı; 320 dp dar ekran ve büyük yazıda alt satır korunurken standart telefon referanstaki yatay uygulama/Wi-Fi/mobil düzenini kullanacak.

## 2026-11-10T19:13+03:00 — Kompakt satır sürümünün derlemesi geçti

- `781c416` kaynakla Gradle 24 saniyede başarılı: uygulama/Android test APK, lint ve 60 JVM sonucu başarılı. Değişmeyen core görevleri önceki geçerli çıktıyı kullandı; değişen uygulama testleri çalıştı. Kanıt `second-session-build-compact.txt`.
- Yalnız etkilenen mint cihaz testi yeniden çalıştırılacak; önceki üç JSON testi kaynakları değişmediği için aynı kontroller tekrar edilmiyor.

## 2026-11-11T04:31+03:00 — Devam: hata anındaki erişilebilirlik ağacı korundu

- Uzak main `db691cf`, yerel/uzak 0/0 ve çalışma ağacı temiz. Son mint testi 182,147 saniyede aynı mobil erişim etiketi sorgusunda başarısız olmuş. Sonuç ve gerçek hata anındaki XML depoya alındı.
- XML aynı anda Wi-Fi seçimi, Wi-Fi erişim etiketi ve 7 yükleme değeriyle 21 indirme değeri gösteriyor. Önceki canlı görüntü ve manuel XML mobile geçişin çalıştığını gösteriyordu. Eski AccessibilityCache verisi olasılığı inceleniyor; henüz kesin kök neden ilan edilmedi.
- Yeni konuşma bölümü kaydedildi ve HANDOFF artık güncel 0.1.4 kaynak/test durumunu önde gösterecek şekilde yenilendi. Başarısız testler başarı gibi sunulmadı.

## 2026-11-11T04:32+03:00 — UiAutomator eski önbellek davranışı ve desteklenen düzeltme

- Kurulu UiAutomator 2.3.0 bytecode incelemesi: UiDevice.findObject pencere köklerini okurken önbelleği temizlemiyor; UiObject2 metin okurken yalnız kendi düğümünü yeniliyor. Kütüphanenin özel temizleme yolu API 33+ için işlem yapmıyor. Bu davranış hata XML'indeki karışık eski/yeni değerlerle uyumlu.
- Android SDK'nın sınıfa özel API kaydı `android.app.UiAutomation.clearCache()` için API 34 başlangıcını doğruladı. Test artık API 34+ üzerinde her taze seçici sorgusundan önce bu genel API'yi kullanıyor; önceki API'lerde mevcut davranış korunuyor.
- Görünme/kaybolma kontrolleri aynı taze sorgu yolunu kullanıyor. Hiçbir veri, ağ, kaydetme/iptal veya yeniden oluşturma doğrulaması kaldırılmadı. Yalnız test kodu değişti; yeniden çalışma sonucu bekleniyor.

## 2026-11-11T04:33+03:00 — Önbellek düzeltmesinin derlemesi ve lint geçti

- `c5c2cc5` test değişikliğiyle Android test APK ve lint kontrolü 25 saniyede geçti. Uygulama kaynakları/APK değişmedi; gereksiz JVM/native tekrarına gidilmedi. Kanıt `second-session-cache-fix-build.txt`.
- Yalnız yeni test APK'sı kurulup mint arayüz testi yeniden başlatıldı. Sonuç bekleniyor.

## 2026-11-11T04:37+03:00 — Mint arayüz testi geçti

- `c5c2cc5` test paketi, değişmeyen `781c416` uygulama APK'sı üzerinde **OK (1 test)**, 174,528 saniye verdi. İki ağın değerleri, ayrı taslaklar, Activity yeniden oluşturma, iki ağın birlikte kaydı, diğer kota alanlarının korunması, geçersiz giriş reddi, iptal ve arama koruması geçti.
- Önceki üç Android JSON kontrolüyle toplam dört cihaz kontrolü iki çalıştırmada başarılıdır; tam VPN paketi/fiziksel telefon kapsamı değildir. Başarısız denemeler silinmedi. Kanıt `second-session-android-ui-passed.txt`.
- TESTING ve README güncellendi. Son açık tema ekranları ve dar/yatay klavyeli görünüm kontrolü sırada.

## 2026-11-11T04:41+03:00 — Açık mint uygulama listesi gerçek ekranı

- Uygulamanın kendi Ayarlar ekranından açık tema seçildi. 360×800 dp (720×1600 px, density 320) telefon görünümünde gerçek ekran alındı. Koruma kapalı/0 B ve tek probe uygulaması emülatörün gerçek durumudur; referanstaki örnek veriler taklit edilmedi.
- Wi-Fi ve mobil anahtarlarının UI bounds üst/alt değerleri eşit; 360 dp üzerinde yatay satır doğrulandı. Görsel ve XML `mint-apps-light.*`. PNG SHA-256 `16c2117d31663c167b64c99f6b44119bc0d11d5411c19842af5ecee7b33e7a8e`. Kaynak uygulama `781c416`; bu bir maket değildir.
- Düzenleyici ve yatay klavye kontrolü sonraki adım. Ekran boyutu geçici olarak uzatıldı; kontrol bitince eski emülatör boyutu/rotasyonu geri alınacak.

## 2026-11-11T04:45+03:00 — Açık mint düzenleyici gerçek ekranı

- Gerçek probe uygulamasının Wi-Fi düzenleyicisi 360×800 dp ekranda incelendi: başlık, ağ sekmeleri, erişim anahtarı, hız alanları, kota seçenekleri ve sabit Kaydet görülebiliyor. Varsayılan sınırsız değerler korunuyor; görüntü için kural kaydedilmedi.
- Görsel/XML `docs/validation/0.1.4-ui/mint-editor-light.*`; PNG SHA-256 `53c862f23161fb9c9ccd08fcb4f9fe14ddb856ecdc6589b15ddded79b6ca79c5`. Kaynak `781c416`.
- Açık tema portre ekranları tamamlandı; yatay klavye kontrolü ve geçici ekran ayarlarının geri alınması kaldı.

## 2026-11-11T04:46+03:00 — README güncel mint ekranları

- README artık gerçek 0.1.4 açık liste/düzenleyici görüntülerini gösteriyor; önceki koyu görsel arşivde korundu. Görsellerin sürümü ile yayımlanmış 0.1.3 APK bağlantısı açıkça ayrıldı.
- Yatay emülatör görünümü açıldı; geçici dönüşte ilk UI ağacı boş geldi, yerleşim oturduktan sonraki sorgu başarılı. Klavyeli kontrol sürüyor.

## 2026-11-11T04:48+03:00 — Gerçek yatay klavyede form alanı yetersiz

- 640×360 dp yatay ekranda AOSP sayısal klavye açıkken sabit geri satırı ve Kaydet altlığı form için yalnız yaklaşık 3,5 dp bıraktı. Hız girişi görsel olarak okunamıyor. Otomatik taslak testi bu yerleşim koşulunu kapsamıyordu.
- Gerçek hata görüntüsü/XML `mint-landscape-ime-before.*`; PNG SHA-256 `db572eb1e2617f9c694496469319571e3b51b3b505f0904517c27500562cbacb`. XML geçişte eski düğümler içerebilir; kusur yerleşmiş PNG üzerinden doğrulandı.
- Kısa kullanılabilir yüksekliğe özel daha küçük birleşik geri/kaydet alanı uygulanacak. Bu checkpoint yalnız bulgu kaydıdır; düzeltme ve cihaz doğrulaması bekleniyor.

## 2026-11-11T07:05+03:00 — Yeni devam oturumu, güncel uzak kayıt alındı

- Yeni ortamda depo/Android araçları bulunmuyordu. Uzak main temiz kopyalandı: `cb3f115`, yerel/uzak 0/0. Önceki başarılı mint testi tekrar çözülmesi gereken hata sanılmadı.
- Bu oturumun işi: 640×360 dp yatay klavyede form alanını tüketen sabit geri/kaydet düzenini düzeltmek ve aynı koşulda doğrulamak. Önceki portre görüntüleri ve test kanıtları korundu.
- Önceki kurulum sohbetindeki doğrulanmış Android kurulum betiği çalışma alanına geri alındı; araç kurulumu sürüyor. Kaynak veya APK henüz değiştirilmedi.
- Yeni görünür sohbet `docs/conversations/2026-11-11-resume.md` altında arşivleniyor.

## 2026-11-11T07:06+03:00 — Kısa ekran eylem satırı, doğrulama bekleyen checkpoint

- Düzenleyici kullanılabilir yüksekliği klavye boşluğu çıkarıldıktan sonra ölçüyor. 320 dp altında Geri ve Kaydet aynı satırda, form kalan kaydırılabilir alanı kullanıyor. Portredeki geniş alt Kaydet korunuyor.
- Her iki Kaydet düğmesi aynı doğrulama/kayıt işlevini çağırıyor; kısa düzende hata özeti üst satırda erişilebilir. Taslakların composition konumu korunuyor.
- `git diff --check` temiz. Android araç kurulumu sürüyor; bu kaynak henüz derlenmedi veya cihazda sınanmadı.

## 2026-11-11T07:08+03:00 — Gerçek yatay klavye regresyon testi eklendi

- MintUiDeviceTest yeni kontrolü gerçek Android IME açıyor: 360 dp kısa kenarlı telefonda yatay alan ve Kaydet en az 48 dp görünür kalmalı, klavyeyle/ birbirleriyle örtüşmemeli. Negatif giriş kaydedilmez; geçerli değişiklik iki ağın diğer değerlerini koruyarak kaydedilir.
- Test yön/ayar/kural değişikliklerini sonunda geri yükler; başarılı görünür alanın ekranını test cache dizinine alır. Henüz derlenmedi/çalıştırılmadı. Android SDK paketleri kuruluyor.

## 2026-11-11T07:10+03:00 — Test beklemeleri ve ortam kurulumu

- Yeni yatay test, yön değişiminden sonra eski Activity yerine farklı RESUMED örneğini bekliyor; klavye üst sınırını her görünürlük kontrolünde taze okuyor.
- Android araçları kuruldu, native race testleri geçti; AAR üretimi sürüyor. 8 GB diskli yeni ortamda emülatör boş alan kontrolü açılışı durdurdu. Yalnız bu oturumun indirilen/açılan kurulum arşivleri temizlendi; kaynak ve kanıtlar korunuyor. Android imajının sıfır blokları byte içeriği değişmeden sparse hâle getiriliyor. Yeni cihaz sonucu henüz yok.

## 2026-11-11T07:15+03:00 — Yeni ortamda native derleme tamamlandı

- Go 1.25.5 / NDK 28.2.13676358 ile race kontrolü geçti ve üç mimarili AAR üretildi. Özet/hash `docs/validation/0.1.4-ui/landscape-native-build.json`. Android/JVM/lint derlemesi sürüyor.
- 8 GB diskte yer kazanmak için AAR üretimi tamamlandıktan sonra SDK yöneticisiyle NDK kaldırıldı; yeniden native derleme gerekirse aynı sürüm kurulmalı. Mevcut AAR kaynaklardan üretildi, APK’dan çıkarılmadı.
- Yeni boş AVD’nin 6 GB kullanıcı diskini SDK mkfs.ext4 ile sparse olarak hazırladık (ilk gerçek kullanım 130 MB). Emülatör 720×1280 px, 320 dpi ile açılıyor; cihaz henüz hazır değil. Kullanıcı/telefon verisine müdahale edilmedi.

## 2026-11-11T07:23+03:00 — Kısa ekran düzeninin derlemesi ve 60 JVM testi geçti

- `8ddfe06` uygulama / `7946d33` test kaynağı ile derleme 9m 46s içinde başarılı: 44 core + 16 app testi, hata/atlanan test yok. Lint 0 hata / 10 uyarı / 2 öneri.
- Uyarı artışı güncel ağ bağımlılık sürümü önerilerinden geliyor: 9 Gradle/bağımlılık güncelleme uyarısı ve mevcut QuotaAlerts UseKtx uyarısı; yeni yerleşim hatası raporlanmadı. Sürüm yükseltmeleri bu düzeltmeye eklenmedi.
- Kanıt: `landscape-build.txt/json`. Yeni APK yalnız bu ortamın emülatör testi içindir; telefon güncellemesi olarak yayımlanmadı. Test/probe APK hazırlanıyor.
- 8 GB bellek sınırı nedeniyle emülatör ilk açılışı durduruldu; derleme/test paketi tamamlandıktan sonra ayrı çalıştırılacak. Native derleme önbelleği temizlendi.

## 2026-11-11T13:07+03:00 — Cihaz test paketleri hazır, sohbet özeti istendi

- Uygulama test APK’sı ve probe APK 21 saniyede derlendi. Kanıt `landscape-test-build.txt`; iki mint testi henüz cihazda çalışmadı.
- Gradle/Kotlin daemon süreçleri kapatıldı. Android açılışına yer bırakmak için yalnız yeniden üretilebilir Gradle transforms önbelleği temizlendi; APK, AAR, test sonuçları ve kaynaklar korundu.
- Kullanıcı bu sohbette neler konuşulduğunu sordu; güncel durum özetlendi ve görünür mesajlar konuşma arşivine eklendi.
