# Güncel devir kaydı

Son güncelleme: **2026-11-09T20:19+03:00**. Her yeni görevde veya “devam” mesajında [AGENTS.md](../AGENTS.md) başlangıç adımlarını uygula, uzak main'i getir ve son [işlem günlüğünü](WORK_LOG.md) oku. Aktif oturum notu hesaplar arasında kilit değildir.

## Güncel durum

**Akış 0.1.3 / versionCode 4** için bağlantı düzeltmesi ve otomatik kontroller tamamlandı. APK `14b4be9` commit'iyle GitHub main'e yüklendi. Doğrudan bağlantıdan HTTP 200 ile yeniden indirildi; 76.857.067 bayt ve SHA-256 birebir eşleşti. Son kullanıcıdaki Samsung fiziksel test sonucu henüz yok.

- Paket: `dev.akiskontrol.app`; Android 10/API 29+, target/compile 36.
- [APK](../downloads/akis-0.1.3-debug.apk), 76.857.067 bayt.
- SHA-256: `32d33ea29485daa4091d6b279d94b6a73246530e0585732854e14ca4e9dd76d9`.
- Önceki 0.1.2 ile aynı imza; mevcut kuruluma güncellenebilir.
- 54 JVM testi, lint 0 hata, native 16 test fonksiyonu + 19 alt test geçti.
- API 36 emülatöründe 6 Android testi iki çalıştırmada geçti: **5 test / 389,009 s**, **1 ağ geçişi testi / 198,864 s**. Test edilen ana APK aynı dosyadır.
- Kanıtlar: [verification.json](validation/0.1.3/verification.json), [Android testleri](validation/0.1.3/android-instrumentation.txt), [Wi-Fi/mobil testi](validation/0.1.3/network-handoff-instrumentation.txt), [native sonuçlar](validation/0.1.3/native-tests.jsonl).

## Ne düzeltildi, ne kanıtlandı?

0.1.2 her ağda IPv6 sunuyordu. gVisor yerel TCP el sıkışmasını fiziksel çıkış soketinden önce tamamladığı için, ulaşılamayan IPv6 hedefi uygulamaya bağlı görünüp ardından reset oluşabiliyordu. Eski APK'da test bu hatayı tekrar gösterdi: VPN kapalıyken başarısız olan connect, VPN açıkken 651 ms'de sahte başarı verdi. **0.1.3 aynı testi geçti**; erişilemeyen IPv6 bağlantısı hemen reddediliyor, izinli DNS/HTTP çalışıyor. [Eski sürüm kanıtı](validation/0.1.3/regression-before-0.1.2.txt).

Yeni VPN yalnızca kullanılabilir IPv6 adresi ve varsayılan rota bulunan ağlarda bu aileyi sunuyor; link-local ve eski site-local adresler yeterli sayılmıyor. Aile değişiminde yeni TUN kurulmadan eskisi kapanmıyor; aynı ağdaki DNS/rota değişimleri izleniyor. Tanı kayıtları adres/payload tutmadan hata kategorisini kaydediyor. Bilinmeyen uygulamalara toplu izin verilmedi.

Native TLS 1.2/1.3 × IPv4/IPv6 aktarımı, Android alan adı çözümleme, uygulama engelleri, hız/kota, VPN yeniden başlatma ve emüle edilmiş Wi-Fi→mobil→Wi-Fi geçişi doğrulandı. IPv6 adresi+rota gerçek global/ULA erişimini garanti etmez; tüm üretici/operatör sorunlarının giderildiği iddia edilmemeli.

## Güncel görev: görsel tasarım alternatifleri

Kullanıcı Akış için birkaç uygulama tasarımını **resim olarak** istedi. Üç konsept üretildi, sohbette sunuldu ve [görsel arşivine](designs/README.md) kaydedildi:

1. **Sade Mint:** açık, temiz tema; kolay okunur izin ve kota kontrolleri.
2. **Koyu Kontrol:** grafit zemin, camgöbeği vurgular; ayrıntılı kontrol paneli.
3. **Renkli Kartlar:** sıcak krem zemin, indigo ve renkli kartlar; kolay kullanım.

Bunlar görsel seçeneklerdir; henüz kullanıcının tasarım seçimi yok. Üç PNG `docs/designs/` altında özgün üretilen dosyalarla byte düzeyinde eşleşerek yedeklendi. Görseller uygulamanın mevcut ekran görüntüleri değildir; her biri önerilen iki ekranı gösterir. Bu görevde uygulama arayüzü, kaynak kodu veya APK değiştirilmedi. Teslim edilen 0.1.3 ve önceki 54 JVM / 6 Android testi geçerlidir; Samsung fiziksel yeniden test sonucu hâlâ bekleniyor.

## Kesin sonraki adım

1. Uzak GitHub durumunu kontrol et. Görsel tasarım alternatifleri tamamlandı ve sunuldu. Kullanıcının 1/2/3 seçimi veya revizyon isteğinden devam et; seçim gelmeden bu konseptleri uygulama koduna aktarma. 0.1.3 APK indirmesi doğrulandı; kaynak/APK değişmedikçe derleme ve testleri tekrarlama.
2. Kullanıcıdan mevcut uygulamayı kaldırmadan **0.1.3** kurup Samsung SM-S921B üzerinde Wi-Fi ve mobilde Samsung Browser/diğer uygulamalar + Speedtest'i yeniden denemesini iste.
3. Sorun sürerse ağ türü ve uygulamayla birlikte yerel bakım günlüğündeki kategoriye göre incele; ilk DNS şüphesini kanıtlanmış neden gibi yeniden ele alma. Yeni hata yokken aynı testleri gereksiz tekrarlama.
4. Fiziksel ağ geçişleri, global/ULA IPv6 blackhole, Private DNS, API 29 ve farklı OEM/pil davranışları genel uyumluluk kontrolünde hâlâ yer alır. Reklam/üyelik ve Play Store yayını kullanıcı daha sonra istediğinde ele alınacak.

## Hesap/ortam değişiminde

- Kullanıcı her anlamlı değişiklik/test sonucunun GitHub'da tutulmasını ve her devamda uzak depo kontrolünü istedi. [AGENTS.md](../AGENTS.md) bunu tarif eder; normal checkpoint için tekrar izin isteme, force push yapma. Çakışan diğer oturumun işini koru.
- Debug imzalama anahtarı GitHub'da **yoktur**. Başka ortamda yeni anahtar üretilirse telefondaki APK güncellenemez. Önceki APK sertifikasıyla karşılaştır: SHA-256 `09b153dbc484daf6723476aad94d4e4e1dbf614712670a502a0390ef9bffa156`. Yetkili anahtarı özel sır deposuyla sağla; Git'e koyma veya sohbetten isteme. Verileri değerlendirmeden uygulamayı kaldırmayı çözüm olarak önerme.
- Başka hesapta bu makinenin dosyalarının var olduğunu varsayma. Taşınabilir derleme: [README.md](../README.md), `tools/build-debug.sh`; cihaz testi: [TESTING.md](TESTING.md).
- `engine/build.sh` sabit gomobile/gobind sürümlerini kullanır; `gomobile init` dolaylı @latest yüklemesi kaldırıldı. AAR üretilir, depoya alınmaz.

## Bu çalışma ortamındaki kanıt konumları

Repo `/workspace/uygulama-1`, yerel dal `work`, uzak hedef `main`. Araç ortamı `/workspace/toolchains/android-env.sh`. Emülatör `emulator-5554` API 36; testlerin ikisi de tamamlandı, aynı anda çalışan test bırakılmadı.

Günlükler `/workspace/toolchains/connectivity-013/`: `new-013-instrumentation.txt`, `network-handoff-instrumentation.txt`, ilgili runner/logcat ve probe JSON dosyaları. Ham logcat kaynak depoya konmadı. Yavaş emülatör bekleme ayarları `readinessTimeoutMs=120000`, `ro.hw_timeout_multiplier=10`; gerçek telefon gereksinimi değildir. Yerel DNS fixture `tools/probe_dns_server.py`, HTTP/UDP fixture `tools/probe_server.py`; emulator `-dns-server 127.0.0.1`. Managed proxy/TLS ayarlarını devre dışı bırakma.
