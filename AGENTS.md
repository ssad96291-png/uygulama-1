# Akış: oturumlar arasında çalışma kuralları

Kullanıcı bu projeyi farklı Codex oturumlarından sürdürüyor. GitHub'daki güncel
kod ve devir kaydı çalışma belleğidir. Sohbet geçmişinin güncel olduğunu varsayma.
Bu talimatlar kullanıcının 9 Kasım 2026 tarihli kayıt ve devam etme isteğini uygular.

## Her yeni görevde ve her "devam et" mesajında

1. Önce `git status --short`, mevcut dal ve son commit'leri kontrol et. Kaydedilmemiş
   kullanıcı/başka ajan değişikliklerini koru.
2. Uzak ana dalı açık refspec ile getir:

   ```sh
   git fetch origin refs/heads/main:refs/remotes/origin/main
   git rev-list --left-right --count HEAD...origin/main
   git log -5 --oneline origin/main
   ```

   Bazı bulut kopyalarında varsayılan fetch yalnızca `HEAD` getirir; yalnızca
   `git fetch` çalıştırmak `origin/main` kaydını güncellemeyebilir.
3. [docs/HANDOFF.md](docs/HANDOFF.md), [docs/WORK_LOG.md](docs/WORK_LOG.md)
   son kayıtları ve ilgili test belgelerini oku. Uzak dal farklıysa bunların uzak
   sürümünü de `git show origin/main:docs/HANDOFF.md` ile incele.
4. Başka oturum ilerlemişse yerel işi kaybetmeden birleştir. Temiz, yalnızca geride
   olan dalda fast-forward uygundur. Ayrışan dallarda değişiklikleri inceleyerek
   birleştir veya ayrı çalışma dalı kullan. `reset --hard`, `clean -fd`, stash silme,
   force push veya yayımlanmış geçmişi yeniden yazma ile eşitleme yapma.
5. Devir kaydındaki somut sonraki adımdan devam et. Yapılmış işi ve başarılı
   testleri yeni bir değişiklik/arıza gerektirmedikçe tekrarlama.

## İşlem kaydı ve yedekleme

- Her anlamlı işlemden sonra (kod/belge değişikliği, test/derleme, araştırma sonucu,
  hata veya dağıtım kontrolü) `docs/WORK_LOG.md` dosyasına kısa bir kayıt ekle:
  tarih/saat ve saat dilimi, yapılan işlem, sonuç, kanıt ve gerekiyorsa sonraki adım.
  Kullanıcıya dönük tarihler için `Europe/Istanbul` kullan. Eski kayıtları sessizce
  değiştirme; düzeltmeyi yeni kayıt olarak ekle.
- `docs/HANDOFF.md` dosyasını güncel durumun kısa özeti olarak tut: çalışan sürüm,
  devam eden iş, testleri tamamlanmamış değişiklikler, engeller, kesin sonraki adım.
  İşlem günlüğü geçmişi; devir dosyası güncel durumu gösterir.
- Mantıksal değişiklik/test tamamlandığında ve oturumu bitirmeden önce ilgili
  dosyaları açıkça stage et, commit oluştur ve GitHub'a yedekle. Kullanıcı bunu
  açıkça istedi; sıradan yedek için tekrar izin isteme. Testi tamamlanmamış bir
  checkpoint'i açıkça bu şekilde etiketle; ona doğrulanmış APK/sürüm deme.
- Push öncesinde uzak dalı tekrar kontrol et. Güncel ana dalın devamıysa
  `git push origin HEAD:main` kullan. Push başka oturum ilerlediği için reddedilirse
  force kullanma; yeniden fetch et, değişiklikleri koruyarak birleştir. Gerekirse
  açıklayıcı ayrı bir dalı push et ve devir dosyasına dalı/merge ihtiyacını yaz.
- Push sonrasında `git ls-remote origin refs/heads/main` (ayrı dalda o dalın ref'i)
  ile kaydın gerçekten GitHub'da olduğunu doğrula. Ağ/kimlik hatasında yerel commit'i
  koru, yedeğin henüz GitHub'a ulaşmadığını açıkça kaydet ve kullanıcıya bildir.
- "Aktif oturum" notu yalnızca bilgilendiricidir; hesaplar arasında kilit değildir.
  İki oturumun aynı dosyayı aynı anda düzenlemesini önlemek için sahipliği devir
  kaydında belirt; Git'in çatışma ve non-fast-forward korumalarını kaldırma.

## Kanıt ve gizlilik

- Komutların sonuçlarını kaydet; token, parola, imzalama anahtarı, ortam sırları,
  ham kişisel trafik veya gereksiz uzun terminal dökümleri kaydetme.
- Kullanıcının örnek uygulama ZIP/APK'sını, özel videolarını veya bunlardan alınan
  kod/görselleri bu depoya ekleme. İnceleme bulgularını kendi ifadelerinle özetle.
- Derleme, JVM testi, emülatör testi ve fiziksel cihaz doğrulamasını ayrı belirt.
  Önceki sürümün testleri yeni değişikliklerin doğrulandığı anlamına gelmez.
- APK paylaşırken sürümü, dosya özetini ve indirilebilir bağlantıyı doğrula.
  Önceki APK'nın üzerine güncelleme beklendiğinde imza sertifikası parmak izini de
  karşılaştır. Başka ortamın yeni debug anahtarını sessizce kullanma; özel imzalama
  anahtarı Git'e veya sohbete alınmaz. İmza uyuşmazlığına karşı kullanıcı verisini
  korumadan kaldırıp yeniden kurmayı çözüm olarak sunma.
  Debug APK'yı Play Store'a hazır veya tüm Android cihazlarda sorunsuz ilan etme.
- Bulut ortamının yapılandırmasını yayımlamak ve Play Store yayını, kaynak kodu
  yedekleme işinden ayrıdır; kullanıcı ayrıca istemedikçe bunları yapma.

Derleme ve test başlangıç noktaları: [README.md](README.md),
[docs/TESTING.md](docs/TESTING.md), `tools/build-debug.sh`.
