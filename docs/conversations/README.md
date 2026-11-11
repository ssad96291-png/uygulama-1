# Kullanıcı ve asistan yazışmaları

Kullanıcı, 10 Kasım 2026 tarihli isteğiyle proje hakkındaki kendi mesajlarının,
asistan yanıtlarının ve paylaşılan görsellerin GitHub'da tutulmasını istedi.
Bu dizin, ajanın erişebildiği görünür yazışmaları kaynak olarak saklar.

- [10 Kasım 2026 — ortam kurulumu, sürekli kayıt isteği ve Sade Mint seçimi](2026-11-10.md)
- [Aynı gün ikinci Codex: kurulum ve tasarıma devam](2026-11-10-setup-followup.md)
- [11 Kasım — cihaz doğrulamasına devam](2026-11-11.md)
- [Tasarım görselleri](../designs/README.md)
- [İşlem ve doğrulama günlüğü](../WORK_LOG.md)
- [Güncel devir kaydı](../HANDOFF.md)

## Kayıt kapsamı

Kullanıcının ve asistanın doğal dil mesajları rol ve sıra bilgisiyle aktarılır.
İmla, anlatım ve kararlar sessizce düzeltilmez. Mesaj başına kesin zaman bilgisi
bulunmuyorsa saat uydurulmaz. Bir dosyanın tarihi, o kayıt grubunun tarihidir.

Bu dizin bütün eski Codex hesaplarının veya sohbetlerinin tam dışa aktarımı
değildir. Başka oturumlarda geçen ve bu oturuma aktarılmamış mesajlara erişim
olduğu varsayılmaz. Eksik tarihçe gerekiyorsa yalnızca eldeki özgün kayıtlarla
sonradan tamamlanır. Sistem/geliştirici talimatları, gizli akıl yürütme, araç çağrı
şemaları, ham terminal çıktıları, çalışma ortamı meta verileri ve kimlik bilgileri
sohbet dökümüne eklenmez. Kullanıcının çağırdığı becerinin yapılandırılmış tanımı
mesajın kendisinden ayrı bir kaynak belgedir; burada beceri çağrısı kaydedilir.

Görseller mümkün olduğunda özgün dosyalarıyla ve SHA-256 özetiyle korunur. Daha
önce depoya alınmış bir görsele yeniden gönderildiğinde bağlantı verilebilir;
özgün yükleme baytlarına erişilemiyorsa aynı dosya olduğu iddia edilmez. Yerel
Windows yolu, bulut ortamında dosyanın okunabildiğini göstermez. Kaynağı belirsiz
bir görselin yerine yeniden üretilmiş bir görsel konmaz.

Yeni kullanıcı mesajını ve kullanıcıya gönderilen ilerleme/sonuç mesajlarını
ilgili kayıt dosyasına ekle. Anlamlı bir işlem veya değişiklik tamamlanınca ilgili
kayıtlarla birlikte küçük bir commit oluşturup GitHub'a gönder; oturum sonunu
bekleme. Uzak dalı kontrol et, diğer oturumun işini koru ve force push kullanma.
Son yanıtın kaydı, yanıt gönderilmeden önce aynı metinle hazırlanabilir; bunu
"gönderilecek son yanıt" diye belirt, gönderildikten sonra geçmişi uydurma.
