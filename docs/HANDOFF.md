# Güncel devir kaydı

Son güncelleme: **9 Kasım 2026, Europe/Istanbul**. Bu dosyanın commit geçmişi,
hangi oturumun en son güncellediğini gösterir. Önce [AGENTS.md](../AGENTS.md)
başlangıç adımlarını uygula ve uzak depoyu kontrol et.

## Hedef ve kullanıcı talimatı

Akış, Android'de uygulama başına Wi-Fi/mobil erişim, hız ve kota kontrolü sağlar.
Kullanıcı mevcut test APK'sındaki bağlantı hatalarının düzeltilmesini bekliyor.
Her anlamlı işlem ve test sonucu GitHub'a kaydedilecek; sonraki oturum güncel
depo/devir kaydından devam edecek. Aktif oturum notları bir kilit sağlamaz.

## Son paylaşılan sürüm

- **0.1.2 alfa / versionCode 3**, paket `dev.akiskontrol.app`, Android 10/API 29+.
- İlk GitHub yedeği: `7c26265` — kaynak, APK ve doğrulama kayıtları.
- APK: [downloads/akis-0.1.2-debug.apk](../downloads/akis-0.1.2-debug.apk).
- Boyut: **76.694.248 bayt**.
- SHA-256: `09881ca6c3379acf04d8e52cad6a7db2068b8c10a599a2c640343ffa6e94a5d3`.
- O sürüm için **48 JVM testi ve 4 API 36 emülatör testi geçti**; kayıtlar
  [validation/0.1.2](validation/0.1.2) ve [TESTING.md](TESTING.md) içinde.
- Bu testler IP adresiyle erişimi sınadı; gerçek telefon hatalarının yokluğunu
  kanıtlamıyor. Kullanıcı SM-S921B cihazda Wi-Fi/mobil bağlantı hatası bildirdi.

## Şu an devam eden iş — henüz cihazda doğrulanmadı

Çalışma sahibi: mevcut kök Codex oturumu; Android, native motor ve emülatör
incelemeleri paralel yürütülüyor. Sonraki oturum bu satıra güvenerek beklememeli;
GitHub commit'lerini ve aşağıdaki sonuçların güncellenip güncellenmediğini kontrol etmeli.

1. `AkisVpnService.kt` / `IpSupport.kt`: IPv6 adresi ve varsayılan IPv6 rotası
   yalnızca seçilen fiziksel ağın kullanılabilir IPv6 adresi ve varsayılan rotası
   varsa TUN'a ekleniyor. Aile değişiminde önce yeni TUN kuruluyor, sonra eskisi
   kapatılıyor; desteklenmeyen IPv6 VPN dışına açılmıyor. Aynı ağdaki
   `LinkProperties` değişimi de artık yeniden yapılandırmaya katılıyor.
2. Native motor: adres/payload içermeyen, tekrarları sınırlanmış bağlantı hatası
   kayıtları; gerçek TLS 1.2/1.3, IPv4/IPv6 testleri eklendi. Native ajan bu testlerin
   `go test -race ./mobile` ile geçtiğini bildirdi; güncel AAR derlemesi tamamlandı.
   Tam yeni APK doğrulaması bekliyor.
3. `tools/probe_dns_server.py`, `ProbeActivity.java`, `VpnDeviceTest.kt`: eşsiz
   `*.akis.test.invalid` adlarını `10.0.2.2`'ye çözen yerel DNS fixture'ı; VPN öncesi,
   sırası ve sonrasında alan adıyla HTTP; IPv4-only ağda IPv6 bağlantısının yanlış
   biçimde kurulmuş görünmemesi için regresyon testi hazırlanıyor.
4. `engine/build.sh`: `gomobile init` çağrısının dolaylı olarak `gobind@latest`
   çekerek sabit Go sürümünü aşması bulundu. Sabitlenmiş araçlarla tekrarlanabilir
   AAR derlemesi için düzeltme uygulandı ve native derleme tamamlandı.

Yeni kaynak sürümü **0.1.3 / versionCode 4** olarak artırıldı. **54 JVM testi
(44 core + 10 app) geçti.** API 33 `RouteInfo.getType` kullanımı API 29 uyumlu
`isDefaultRoute()` ile düzeltildi; app birim testi ve lint başarılı. Deprecated
site-local IPv6 adresleri desteğe dahil edilmiyor. Native 16 test ve 19 alt test
geçti. Birleşik yeni APK derlemesi başarılı; 0.1.3 üzerinde 5 Android testi çalışıyor.

**Bu checkpoint yeni APK'nın hazır olduğu anlamına gelmez.** 0.1.2 dosyasının
üzerine doğrulanmamış bir APK yazma; yeni APK üretildiğinde sürüm/boyut/özet ve
test sonuçlarını birlikte güncelle.

## Bulgu ve henüz kanıtlanmamış kısım

Videoda Samsung Browser'ın Wi-Fi kuralı açık/sınırsız/kotasız; mobilde Speedtest
7,52 Mbps indirme ve 15,9 Mbps yükleme tamamlıyor, tarayıcı ise “Bağlantı sıfırlandı”
diyor. Bu, tek başına DNS arızasını kanıtlamaz.

Kaynak kodunda somut kusur: eski VPN her ağda IPv6 sunuyor. gVisor TCP el sıkışması,
fiziksel çıkış soketine bağlanmadan tamamlanıyor. IPv4-only ağda tarayıcı IPv6
bağlantısını başarılı sanıp daha sonra reset alabilir; normal IPv4'e dönüş devreye
girmeyebilir. Android regresyon testi **eski 0.1.2'de beklenen hatayı tekrar gösterdi**: VPN
kapalıyken başarısız olan IPv6 connect, VPN açıkken 651 ms içinde sahte başarı
verdi. Yeni 0.1.3 test sonucu bekliyor; bunun kullanıcı cihazındaki tek neden
olduğu iddia edilmemeli. Kanıt: [eski sürüm regresyonu](validation/0.1.3/regression-before-0.1.2.txt).

## Kesin sonraki adımlar

1. Uzak depoyu getir, yerel/uzak ayrışmayı ve çalışma günlüğündeki yeni sonuçları oku.
2. Eski APK regresyonu tamamlandı; tekrar çalıştırma. Yeni test çalıştırıcısı
   `/workspace/toolchains/connectivity-013/run-new-tests.py` şu anda 0.1.3 üzerinde
   tüm 5 cihaz testini çalıştırıyor. Sonucu `new-013-instrumentation.txt` ve
   `new-tests-runner.log` içinde kontrol et; beklenen sonuç `OK (5 tests)`.
3. Birleşik derleme ve 54 JVM testi başarılı (`build-0.1.3-final.log`); native
   AAR ve 16+19 native test başarılı. APK eski sürümle aynı imzaya sahip ve ZIP
   16 KB hizalaması geçti. İndirilecek APK henüz depoya konmadı.
4. DNS/IPv6 regresyonu, mevcut hız/kota/yeniden başlatma testleri, lint, imza ve
   APK bütünlüğü doğrulandıktan sonra yeni sürümü `downloads/` ve doğrulama
   kayıtlarıyla paylaş. Testi geçmeyen kısmı açık bırak.
5. Kullanıcıdan Samsung cihazında Wi-Fi ve mobilde tarayıcı + Speedtest'i yeniden
   denemesini iste. Fiziksel ağ geçişi/üretici doğrulaması ayrıca gerekli.

## Mevcut ortam için yardımcı konumlar

Bunlar bu bulut makinesine aittir; başka hesapta var olduklarını varsayma.
Taşınabilir kurulum/derleme adımları [README.md](../README.md) içindedir.

- Repo: `/workspace/uygulama-1`; yerel dal `work`, uzak hedef `main`.
- Ortam dosyası: `/workspace/toolchains/android-env.sh`.
- Eski APK: `/workspace/artifacts/akis-0.1.2-debug.apk`.
- Güncel çalışma günlükleri: `/workspace/toolchains/connectivity-013/`.
- Emülatör: `emulator-5554`, API 36, yazılım hızlandırması; yavaş ortamda
  `readinessTimeoutMs=120000`, `ro.hw_timeout_multiplier=10`.
- DNS fixture: `python3 tools/probe_dns_server.py`; emülatör
  `-dns-server 127.0.0.1` ile başlatıldı. HTTP/UDP fixture `tools/probe_server.py`.
- Bu makinede yerel test TCP aktarımı için mevcut managed proxy'yi koruyan bir
  loopback adaptörü kullanılıyor. Ortamın proxy/TLS güvenini kapatma.

## Hesap/ortam değişiminde APK imzası

Debug imzalama anahtarı bilinçli olarak GitHub'da bulunmuyor. Yeni bir ortamın
otomatik ürettiği debug anahtarı farklı olursa APK, telefondaki önceki kurulumun
üzerine güncelleme olarak kurulamaz. Yeni APK tesliminden önce sertifika parmak
izini önceki APK ile karşılaştır. Sürekli güncellemeler için aynı yetkili imzalama
anahtarını özel bir sır deposundan sağla; anahtarı Git'e ekleme veya sohbetten
istemeye çalışma. İmza uyuşmazlığını gizleme; yedek/veri kaybı değerlendirmeden
uygulamayı kaldırmayı çözüm olarak önermeden önce durumu açıkla.

Tam geçmiş: [WORK_LOG.md](WORK_LOG.md). Yeni test çıktısı gelince bu dosyadaki
bekliyor ifadelerini kanıtlarıyla güncelle ve checkpoint'i GitHub'a push et.

Son doğrulanan ara kayıt: `5fe7039` GitHub main üzerinde. Yeni test öncesi APK
SHA-256: `32d33ea29485daa4091d6b279d94b6a73246530e0585732854e14ca4e9dd76d9`.
Sıradaki sonuç beklenen dosya: `/workspace/toolchains/connectivity-013/new-013-instrumentation.txt`.

Ek doğrulama: `VpnDeviceTest#hostnameConnectionsFollowWifiAndMobileRules` eklendi fakat henüz çalışmadı. İlk 5 test bitince test APK'sını yeniden derleyip bu metodu ayrı çalıştır; mevcut ana APK aynı kalmalı. Bağımsız inceleme TUN değişiminde engelleyici kusur bulmadı; fiziksel global/ULA IPv6 erişimi yine de garantilenmiyor.
