# Güncel devir kaydı

Son güncelleme: **2026-11-10T07:43+03:00**. Her yeni görevde veya “devam” mesajında [AGENTS.md](../AGENTS.md) başlangıç adımlarını uygula, uzak main'i getir ve son [işlem günlüğünü](WORK_LOG.md) oku. Aktif oturum notu hesaplar arasında kilit değildir.

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

## Güncel görev: seçilen açık mint tasarımının uygulanması

**2026-11-10T07:43+03:00:** Kullanıcı `01 / AÇIK VE SADE` görselini seçti ve uygulama
arayüzünün buna dönüştürülmesini istedi. Seçilen görsel
[01-acik-ve-sade.png](designs/01-acik-ve-sade.png) ile görsel olarak aynı;
sohbetteki yeni yüklemenin ham dosyası bu makinede ayrıca erişilebilir değil.
Kullanıcı tüm proje mesajlarının/görsellerinin ve tamamlanan her anlamlı işlemin
anında GitHub'a eklenmesini yeniden açıkça istedi. AGENTS güncellendi.

Aktif çalışma sahipliği: bu Codex oturumu arayüz değişikliklerini yürütüyor;
Uygulamalar listesi, tam ekran kural düzenleyici ve ortak tema ayrı dosyalarda
hazırlanıyor. Diğer oturumlar bu dosyalarda değişiklik yapmadan son uzak kaydı
kontrol etmeli. Bu not kilit değildir.

## Kesin sonraki adım

1. Açık mint tema, satırlı uygulama listesi ve tam ekran ağ/hız/kota düzenleyicisini
   uygula. Mevcut erişim, kota, haftalık dönem, toplu işlem ve koyu tema işlevlerini koru.
2. Her tamamlanan değişiklik grubunu hemen günlükle/commit/push; test edilmemiş
   checkpoint'leri açıkça etiketle. Görselleri ve erişilebilir konuşmaları arşivle.
3. JVM testleri, lint, APK derlemesi ve emülatör arayüz akışlarını doğrula.
   Uygulama listesi/tam ekran düzenleyici entegre edildi, açılış Uygulamalar; yeni tasarım henüz test edilmedi; önceki 0.1.3 testleri yeni tasarımın kanıtı değildir.
4. Bu ortamın debug sertifikası mevcut dağıtımdan farklıdır. Yetkili eski anahtar
   olmadan yeni APK'yı telefondaki sürümün üzerine güncellenebilir diye sunma.
   Fiziksel Samsung testi ve mağaza yayını bu çalışmanın doğrulaması değildir.

## Hesap/ortam değişiminde

- Kullanıcı her anlamlı değişiklik/test sonucunun GitHub'da tutulmasını ve her devamda uzak depo kontrolünü istedi. [AGENTS.md](../AGENTS.md) bunu tarif eder; normal checkpoint için tekrar izin isteme, force push yapma. Çakışan diğer oturumun işini koru.
- Debug imzalama anahtarı GitHub'da **yoktur**. Başka ortamda yeni anahtar üretilirse telefondaki APK güncellenemez. Önceki APK sertifikasıyla karşılaştır: SHA-256 `09b153dbc484daf6723476aad94d4e4e1dbf614712670a502a0390ef9bffa156`. Yetkili anahtarı özel sır deposuyla sağla; Git'e koyma veya sohbetten isteme. Verileri değerlendirmeden uygulamayı kaldırmayı çözüm olarak önerme.
- Başka hesapta bu makinenin dosyalarının var olduğunu varsayma. Taşınabilir derleme: [README.md](../README.md), `tools/build-debug.sh`; cihaz testi: [TESTING.md](TESTING.md).
- `engine/build.sh` sabit gomobile/gobind sürümlerini kullanır; `gomobile init` dolaylı @latest yüklemesi kaldırıldı. AAR üretilir, depoya alınmaz.

## Bu çalışma ortamındaki kanıt konumları

Repo `/workspace/uygulama-1`, yerel dal `work`, uzak hedef `main`. Araç ortamı `/workspace/toolchains/android-env.sh`. Emülatör `emulator-5554` API 36; testlerin ikisi de tamamlandı, aynı anda çalışan test bırakılmadı.

Günlükler `/workspace/toolchains/connectivity-013/`: `new-013-instrumentation.txt`, `network-handoff-instrumentation.txt`, ilgili runner/logcat ve probe JSON dosyaları. Ham logcat kaynak depoya konmadı. Yavaş emülatör bekleme ayarları `readinessTimeoutMs=120000`, `ro.hw_timeout_multiplier=10`; gerçek telefon gereksinimi değildir. Yerel DNS fixture `tools/probe_dns_server.py`, HTTP/UDP fixture `tools/probe_server.py`; emulator `-dns-server 127.0.0.1`. Managed proxy/TLS ayarlarını devre dışı bırakma.
