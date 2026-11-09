# Akış doğrulama notları

Bu dosya otomatik testlerle doğrulanan davranışları cihazda henüz doğrulanmamış davranışlardan ayırır. APK'nın derlenmesi tek başına telefon üzerinde VPN uyumluluğunu kanıtlamaz.

## 0.1.3 bağlantı düzeltmesi — tamamlanan kontroller

Eski 0.1.2 APK'da ulaşılamayan IPv6 hedefinin VPN açıkken sahte TCP bağlantı başarısı vermesi Android 16 emülatöründe tekrarlandı. VPN kapalıyken aynı hedefe bağlantı kurulamadı; alan adıyla DNS/HTTP her iki durumda çalıştı. [Önceki sürümün başarısız regresyon testi](validation/0.1.3/regression-before-0.1.2.txt) bu ayrımı kaydeder. Emülatör Wi-Fi'si site-local IPv6 adresi taşır; test sonucu fiziksel Samsung cihazının ağ durumu olarak yorumlanmamalıdır.

0.1.3 kullanılabilir IPv6 adresi ve varsayılan rota olmayan ağlarda IPv6 sunmaz; link-local ve eski site-local adresler yeterli sayılmaz. Ağ özellikleri değişince motor güncellenir. Bu, her global/ULA IPv6 rotasının Internet'e gerçekten erişebildiği garantisi değildir. Samsung cihazında tekrar test gerekir.

54 JVM testi, lint ve APK derlemesi geçti. Native motorun 16 test fonksiyonu ve 19 alt testi, TLS 1.2/1.3 × IPv4/IPv6 dahil, race detector ile geçti. Yeni APK üzerinde **6 Android testi iki çalıştırmada (5 + 1) geçti**: ilk paket 389,009 saniye, Wi-Fi/mobil geçiş testi 198,864 saniye. Aynı APK kullanıldı; ayrı çalıştırma yalnız ek testin sonradan hazırlanmasından kaynaklandı. [Doğrulama kaydı](validation/0.1.3/verification.json), [ilk beş test](validation/0.1.3/android-instrumentation.txt) ve [ağ geçişi testi](validation/0.1.3/network-handoff-instrumentation.txt) depodadır. Eski 0.1.2'nin aşağıdaki sonuçları yeni APK için cihaz doğrulaması sayılmaz.

## 0.1.2 tarihsel sonuçları

9 Kasım 2026: **0.1.2 alfa için 48 JVM birim testi ve Android 16/API 36 emülatöründeki dört cihaz testinin tamamı geçti.** Cihaz testleri 237,443 saniyede tamamlandı. Fiziksel telefon, üretici davranışı ve gerçek mobil ağ geçişi kontrolleri bekliyor.

| Kontrol | Sonuç | Kapsam |
| --- | --- | --- |
| `:core:test` | 44 test geçti | Erişim, hız rezervasyonu/iade, ortak uygulama hızları, TCP/UDP kota hesabı, geçici izin, dönem ve aylık yenileme sınırları; SDK sandbox UID eşleme için 4 test dahil |
| `:app:testDebugUnitTest` | 4 test geçti | Paket/UID kimliği değiştiğinde kurallar ve geçmişin eşleştirilmesi |
| `:app:assembleDebug` | 0.1.2 APK derlemesi geçti | Debug APK üretildi |
| `:app:lintDebug` | 0 hata, 2 uyarı, 1 öneri | Android statik incelemesi |
| Go `go test -race ./mobile` | 13 test fonksiyonu ve 15 alt test geçti; önbellekteki başarılı sonuç yeniden doğrulandı | IPv4/IPv6 TCP/UDP aktarımı, soket koruması, sahiplik, DNS yönlendirmesi, kota, hız beklemesinin iptali, TUN dosya tanıtıcısı yaşam döngüsü |
| Android JSON cihaz testleri | 0.1.2 üzerinde 3 test geçti | API 36 emülatöründe gerçek Android JSON uygulamasıyla kayıt/yedek doğrulaması |
| Android VPN trafik testi | 0.1.2 üzerinde geçti | İzinli TCP/UDP, Wi-Fi engeli, TCP/UDP hız sınırları, uygulama kotası, yeniden başlatma sonrası aktarım ve kullanım kaydı |
| Fiziksel telefon uyumluluğu | Bekliyor | Üretici güç yönetimi, gerçek mobil ağ, ağ geçişleri ve uzun süreli kullanım |

GitHub yedeğinde [doğrulama özeti](validation/0.1.2/verification.json), [Android test çıktısı](validation/0.1.2/android-instrumentation.txt), [trafik sonuçları](validation/0.1.2/android-traffic-results.json), [lint raporu](validation/0.1.2/lint-results-debug.txt) ve [ana ekran görüntüsü](images/akis-home.png) bulunur. APK [downloads](../downloads/README.md) klasöründedir.

Sonuç dosyaları çalışma ortamında şu konumlardadır:

- APK: `/workspace/artifacts/akis-0.1.2-debug.apk`
- APK SHA-256: `09881ca6c3379acf04d8e52cad6a7db2068b8c10a599a2c640343ffa6e94a5d3`
- Derleme: `/workspace/toolchains/verified-build-0.1.2.log`
- Cihaz testi: `/workspace/toolchains/device-test-012/instrumentation-timeouts10.txt` — `OK (4 tests)`
- Android sistem kaydı: `/workspace/toolchains/device-test-012/logcat-timeouts10.txt`
- Son ana ekran: `/workspace/toolchains/device-test-012/main-screen-final.png`
- Native test kaydı: `/workspace/artifacts/native-tests.jsonl`

Başarılı cihaz çalıştırması, donanım hızlandırması olmayan emülatörde `readinessTimeoutMs=120000` ve `ro.hw_timeout_multiplier=10` ile yapıldı. Bunlar ortamın yavaşlığı için bekleme sürelerini artırır; trafik başarı koşulları ve hız ölçüm eşikleri değiştirilmedi. Sonraki kaynak değişiklikleri için sonuçlar yeniden doğrulanmalıdır. Masaüstündeki motor testleri tek başına Android UID çözümlemesini veya OEM davranışını doğrulamaz; tamamlanan API 36 emülatör testi de fiziksel cihaz matrisi yerine geçmez.

Tekrarlama notu: 0.1.1 çalıştırmasında yeniden başlatma için 15 saniyelik test beklemesi, Android'in kaydedilen 20,399 saniyelik hizmet başlatma gecikmesinden kısa kalmıştı. 0.1.2'de yavaş emülatör için aşağıdaki ayarlanabilir hazır olma beklemesi kullanıldı; son çalıştırmada yeniden başlatma dahil tüm dört test geçti.

SDK sandbox eşlemesinin dört birim testi, Android 13/API 33 ve üzerindeki sandbox UID aralığının ana uygulamaya dönüşmesini, Android kullanıcı önekinin korunmasını, diğer UID türlerinin değişmemesini ve sandbox trafiğinin ana uygulamanın erişim/kota/kullanım hesabını paylaşmasını sınar.

## Tekrarlanabilir otomatik kontroller

Araç kurulumunu [README.md](../README.md) dosyasına göre tamamladıktan sonra:

```sh
./tools/build-debug.sh
```

Motor daha önce derlenmişse yalnızca Kotlin ve Android kontrolleri:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Raporlar:

- `core/build/reports/tests/test/index.html`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- Cihaz testi Gradle ile çalıştırıldıysa `app/build/reports/androidTests/connected/`

## Gerçek paketlerle emülatör testi

`VpnDeviceTest` ayrı UID'ye sahip `probe` uygulamasının TCP/UDP, sistem DNS'i, alan adıyla bağlantı ve Wi-Fi/mobil geçiş trafiğini denetler. Emülatörün Wi-Fi bağlantısı etkin, hücresel veri bağlantısı kullanılabilir, ekranı açılmış ve normal Android açılışı tamamlanmış olmalıdır. Bu test için ayrı bir test emülatörü kullan: test korumayı başlatıp durdurur, Wi-Fi'yi kapatıp açar ve probe uygulamasının kuralını geçici olarak değiştirir.

`StateJsonDeviceTest` ayrıca Android'in gerçek JSON uygulamasıyla 3 test çalıştırır: yerel kayıt ve taşınabilir yedeğin yazılıp geri okunması, önceki sürüm 1 dosyalarında eksik yenileme gününün ayın ilk gününe dönüşmesi ve geçersiz sınır/yinelenen paket kimliklerinin reddedilmesi. Taşınabilir yedeğin geçici izinleri taşımadığı da doğrulanır.

Önce test paketlerini üret:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :probe:assembleDebug
```

Emülatörü çalıştıran makinede ayrı bir terminalde yerel sunucuyu açık tut:

```sh
python3 tools/probe_server.py
```

Sunucu yalnızca `127.0.0.1:18081` üzerinden HTTP ve `127.0.0.1:18082` üzerinden UDP sunar. Probe, Android emülatörünün ana makine adresi `10.0.2.2` üzerinden bağlanır; fiziksel telefon testi için bu adres uygun değildir. Test uygulaması dış servise ihtiyaç duymaz.

Alan adı testleri için ayrıca `python3 tools/probe_dns_server.py` çalıştır ve emülatörü `-dns-server 127.0.0.1` seçeneğiyle başlat. DNS fixture yalnızca `*.akis.test.invalid` adlarını `10.0.2.2`'ye çözer; her probe farklı ad kullanarak DNS önbelleğini devre dışı bırakır. Linux üzerinde loopback 53 numaralı portu açmak yetki gerektirebilir. Bu DNS yapılandırması yalnızca ayrı test emülatörüne uygulanır; gerçek telefona veya host'un genel DNS ayarlarına uygulanmaz.

Tek bir emülatör bağlıyken:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r probe/build/outputs/apk/debug/probe-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class dev.akiskontrol.app.VpnDeviceTest,dev.akiskontrol.app.data.StateJsonDeviceTest \
  dev.akiskontrol.app.test/androidx.test.runner.AndroidJUnitRunner
```

Birden fazla cihaz bağlıysa her `adb` komutuna `-s SERIAL` ekle. Güncel kaynakta üç VPN testi ve üç JSON testi vardır; tümü birlikte çalıştırıldığında beklenen sonuç `OK (6 tests)` olur. Yalnızca paketlerin kurulması veya uygulamanın açılması başarı sayılmaz. Eski sürümün 4 testlik kayıtları tarihsel kanıttır.

Donanım hızlandırması olmayan yavaş emülatörde, Android hizmetinin hazır olmasını bekleyen süreyi 120 saniyeye çıkarmak için:

```sh
adb shell am instrument -w -r \
  -e class dev.akiskontrol.app.VpnDeviceTest,dev.akiskontrol.app.data.StateJsonDeviceTest \
  -e readinessTimeoutMs 120000 \
  dev.akiskontrol.app.test/androidx.test.runner.AndroidJUnitRunner
```

`readinessTimeoutMs` varsayılan olarak 15.000 ms'dir; girilen değer 15.000–180.000 ms aralığına alınır. Parametre yalnızca hizmetin hazır olması gibi durum beklemelerini değiştirir; aktarım sonuçları, hız alt süreleri ve probe sonucunun 30 saniyelik beklemesi değişmez. Normal hızlı emülatörlerde varsayılan süreyi kullan; fiziksel cihaz testlerinde de bu emülatör ayarını taşımadan varsayılan süreyle ölçüm yap.

Bu çalışma ortamındaki yazılım emülatörü ayrıca Android framework zaman aşımı çarpanı `ro.hw_timeout_multiplier=10` ile çalıştırıldı. Bu, yalnızca emülatörün sistem ayarıdır; uygulamanın çalışma gereksinimi değildir. Kullanılan imajda komut satırından `-prop` ile verilmesi desteklenmediğinden root erişimi ve framework'ün durdurulup yeniden başlatılması gerekti.

Yalnızca sana ait, ayrı bir AOSP test emülatöründe aynı yavaşlık ortaya çıkarsa aşağıdaki işlem tekrarlanabilir. Gerçek telefonlara uygulanmaz. Önce mevcut özelliği ve `system_server` süreç kimliğini oku:

```sh
adb root
adb wait-for-device
adb shell getprop ro.hw_timeout_multiplier
adb shell pidof system_server
```

Özellik boşsa `adb shell setprop ro.hw_timeout_multiplier 10` ile tanımla ve `adb shell getprop ro.hw_timeout_multiplier` sonucunun `10` olduğunu doğrula. Zaten `10` ise tekrar yazma; başka bir değer varsa salt okunur özelliği değiştirmeye çalışma. Özellik doğru ayarlandıktan sonra framework'ün değeri yeniden okuması için:

```sh
adb shell setprop sys.boot_completed 0
adb shell stop
adb shell start
```

`adb shell getprop sys.boot_completed` tekrar `1` göstermeden test başlatma; `adb shell pidof system_server` çıktısının da önceki süreç kimliğinden farklı olduğunu doğrula. Bu işlem emülatördeki uygulamaları ve sistem arayüzünü yeniden başlatır.

Trafik testi şu adımları sınar:

1. VPN başlar ve Wi-Fi seçilir.
2. İzinli TCP ve UDP aktarımı tamamlanır.
3. Wi-Fi engeli TCP aktarımını durdurur.
4. 8.192 bayt/s indirme sınırında 16.384 baytlık aktarım çok erken tamamlanmaz.
5. 1.024 bayt/s sınırında 1.200 baytlık UDP paketi bekletilerek taşınır.
6. Dolmuş uygulama kotası aktarımı engeller.
7. Hizmet durdurulup tekrar başlatılınca aktarım çalışır ve kullanım kaydedilir.

Ek hostname testi VPN öncesi/sırası/sonrası sistem DNS'i ve HTTP aktarımını, uygulama engelinin korunmasını ve ulaşılamayan IPv6 hedefinde sahte TCP başarısı oluşmamasını sınar. Ağ geçişi testi Wi-Fi engelli/mobil izinli kuralından mobil engelli/Wi-Fi izinli kuralına geçer; gerçek paketlerle seçilen ağın kuralını doğrular. Bunlar emüle edilmiş ağlardır; operatör ve üretici telefon testlerinin yerine geçmez.

Test sonunda önceki probe kuralı ve toplam kota geri yüklenir; koruma durdurulur. Hata incelemesinde test çıktısı, `adb logcat` ve uygulamanın bakım ekranındaki kayıtlar birlikte değerlendirilmelidir.

## Fiziksel cihazda tamamlanacak kontroller

| Senaryo | Beklenen davranış |
| --- | --- |
| Wi-Fi/mobil ayrı kurallar | Bir ağda engelli uygulama diğer ağda izinliyse çalışır; geçişte doğru kural seçilir |
| Çoklu bağlantı ve iki yönlü hız | Aynı uygulamanın bağlantıları tanımlı toplam indirme/yükleme hızını paylaşır |
| Kota ve yenileme | Uygulama kotası doğru eylemi uygular; toplam kota aşılmaz; 29–31. günler kısa aylarda doğru yenilenir |
| Bildirimler | İzin verildiğinde kota eşikleri uyarı üretir; reddedildiğinde uygulama çalışmaya devam eder |
| Uyku, ekran kilidi, yeniden başlatma | İşletim sisteminin VPN ve pil ayarlarıyla tutarlı davranır; durum ekranı gerçek bağlantıyı yansıtır |
| Başka VPN başlatma | Android tek VPN sınırına uygun şekilde Akış durur; kullanıcıya doğru durum gösterilir |
| Her zaman açık VPN / bağlantıyı engelleme | Ağ kesintisi ve hizmet kapanması sırasında Android ayarları beklenen davranışı sağlar |
| IPv6, DNS, HTTPS ve QUIC | Gerçek ağlarda bağlantı kurulur; engel/hız/kota korunur |
| Yeni kurulum, silme ve tekrar kurma | Kurallar yanlış uygulamaya taşınmaz; kullanım geçmişi tutarlı kalır |
| Yedek/CSV ve bakım hesabı | Dosyalar açılır; bozuk yedek uygulanmaz; bakım parolası yedeğe alınmaz |
| Erişilebilirlik ve ekranlar | Büyük yazı, küçük ekran, yatay görünüm ve ekran okuyucu ile temel işlemler kullanılabilir |

Öncelikli cihaz matrisi Android 10/API 29 ve Android 16/API 36 ile en az iki üreticinin güncel telefonudur. ARM64 ve 16 KB bellek sayfalı cihazlar, uzun süreli trafik/pil ölçümü, mobil ağ geçişleri ve mağaza politikası incelemesi tamamlanmadan genel uyumluluk veya yayın hazır olduğu iddiasında bulunulmamalıdır.
