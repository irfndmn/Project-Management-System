const API_BASE = '/api/projeler';
const mesajElementi = document.getElementById('mesaj');
const projeListesiElementi = document.getElementById('projeListesi');

// Kısıtlayıcı Sabitler
const MAX_KISI = 3;
// E-posta formatı regex'i: Tam 9 Rakam@firat.edu.tr
const EMAIL_REGEX = /^\d{9}@firat\.edu\.tr$/;
// Okul No formatı regex'i: Tam 9 Rakam
const OKUL_NO_REGEX = /^\d{9}$/;

// Projeye Kayıt Olma Fonksiyonu
async function kayitOl() {
    // 1. Form verilerini JSON objesine topla
    const requestData = {
        ogrenciIsim: document.getElementById('isim').value,
        ogrenciSoyad: document.getElementById('soyad').value,
        okulNo: document.getElementById('okulNo').value,
        email: document.getElementById('email').value,
        projeAdi: document.getElementById('projeAdi').value,
        projeAciklamasi: document.getElementById('projeAciklamasi').value
    };

    // 2. ÖN KONTROLLER (Hızlı ve Kesin Kontrol)

    // Zorunlu alan kontrolü
    if (!requestData.okulNo || !requestData.projeAdi || !requestData.email || !requestData.ogrenciIsim) {
        gosterMesaj("Hata: İsim, Okul No, E-posta ve Proje Adı alanları zorunludur.", 'red');
        return;
    }

    // Okul Numarası Formatı Kontrolü
    if (!OKUL_NO_REGEX.test(requestData.okulNo)) {
        gosterMesaj("Hata: Okul Numarası formatı geçersiz. Tam 6 rakamdan oluşmalıdır.", 'red');
        return;
    }

    // E-posta Formatı Kontrolü
    if (!EMAIL_REGEX.test(requestData.email)) {
        gosterMesaj("Hata: E-posta formatı geçersiz. '6 Rakam@firat.edu.tr' (örneğin: 123456@firat.edu.tr) olmalıdır.", 'red');
        return;
    }

    // !!! EN KRİTİK KONTROL: OKUL NO ve E-POSTA EŞLEŞMESİ !!!
    const emailPrefix = requestData.email.substring(0, 9);
    if (requestData.okulNo !== emailPrefix) {
        gosterMesaj("Hata: Okul Numarası e-posta adresinizin ilk 6 hanesiyle eşleşmelidir.", 'red');
        return;
    }


    // 3. BACKEND İSTEĞİ
    try {
        gosterMesaj("Proje benzerliği kontrol ediliyor (Yapay Zeka dahil)... Lütfen bekleyin.", 'orange');

        const response = await fetch(`${API_BASE}/kaydet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        });

        if (response.ok) {
            gosterMesaj(`${requestData.okulNo} numaralı öğrenci, "${requestData.projeAdi}" projesine başarıyla kaydedildi!`, 'green');
            projeleriGetir(); // Listeyi yenile
        } else {
            // Backend'den gelen hata mesajlarını yakala
            const errorText = await response.json();
            const errorMessage = errorText.message || "Bilinmeyen bir hata oluştu.";

            if (errorMessage.includes("Kontenjan Dolu") || errorMessage.includes("zaten bir projeye kayıtlı") || errorMessage.includes("geçersiz")) {
                gosterMesaj(`Kayıt Başarısız: ${errorMessage}`, 'red');
            } else {
                gosterMesaj(`Sunucu Hatası: ${errorMessage}`, 'red');
            }
        }

    } catch (error) {
        console.error('Kayıt veya Sunucu Hatası:', error);
        gosterMesaj('Sunucuya bağlanırken bir hata oluştu.', 'red');
    }
}

// Projeleri Listeleme Fonksiyonu (Aynı Kalır)
async function projeleriGetir() {
    projeListesiElementi.innerHTML = '<tr><td colspan="4">Yükleniyor...</td></tr>';
    try {
        const response = await fetch(API_BASE);
        const tumProjeler = await response.json();

        // Projeleri Proje Adına Göre Grupla
        const gruplanmisProjeler = {};

        tumProjeler.forEach(p => {
            const grupBuyuklugu = p.ayniProjeKayitlari.length;
            const key = p.projeAdi;

            if (!gruplanmisProjeler[key] || gruplanmisProjeler[key].ogrenciler.length < grupBuyuklugu) {
                gruplanmisProjeler[key] = {
                    projeAdi: p.projeAdi,
                    projeAciklamasi: p.projeAciklamasi,
                    grupBuyuklugu: grupBuyuklugu,
                    ogrenciler: tumProjeler.filter(item => p.ayniProjeKayitlari.includes(item.id))
                        .map(item => item.okulNo)
                };
            }
        });


        projeListesiElementi.innerHTML = '';

        // Gruplanmış projeleri tabloya ekle
        Object.values(gruplanmisProjeler).forEach(grup => {
            const tr = document.createElement('tr');
            const kontenjanDurumu = grup.grupBuyuklugu >= MAX_KISI ? '🔴 DOLU' : `🟢 (${grup.grupBuyuklugu}/${MAX_KISI})`;

            tr.innerHTML = `
                <td>${grup.projeAdi}</td>
                <td>${kontenjanDurumu}</td>
                <td>${grup.ogrenciler.join(', ') || 'Henüz kayıt yok'}</td>
                <td>${grup.projeAciklamasi.substring(0, 100)}...</td>
            `;
            projeListesiElementi.appendChild(tr);
        });

    } catch (error) {
        console.error('Listeleme hatası:', error);
        projeListesiElementi.innerHTML = '<tr><td colspan="4" style="color: red;">Projeler yüklenirken bir hata oluştu.</td></tr>';
    }
}

// Mesaj gösterme yardımcı fonksiyonu (Aynı Kalır)
function gosterMesaj(metin, renk) {
    mesajElementi.textContent = metin;
    mesajElementi.style.color = renk;
}


// YENİ: Yönetici: Veritabanını Sıfırlama Fonksiyonu (ŞİFRE İSTENİYOR)
async function veritabaniSifirla() {
    // 1. Çift Onay Mekanizması
    const onay = confirm("UYARI: Bu işlem geri alınamaz! Tüm kayıtları silmek istediğinizden emin misiniz?");
    if (!onay) return;

    // 2. YÖNETİCİ ŞİFRESİNİ İSTE (Sadece sizin bildiğiniz şifre)
    const masterKey = prompt("Lütfen veritabanını sıfırlamak için yönetici şifresini girin:");

    if (!masterKey) {
        gosterMesaj("Şifre girilmediği için işlem iptal edildi.", 'orange');
        return;
    }

    const kesinOnay = confirm("SON ONAY: Gerçekten tüm veriler silinecektir. Devam etmek için TAMAM'a basın.");
    if (!kesinOnay) return;


    // 3. BACKEND İSTEĞİ (Şifre Query Parametresi Olarak Gönderilir)
    try {
        gosterMesaj("Veritabanı sıfırlanıyor... Lütfen bekleyin.", 'red');

        // Şifreyi query parametresi olarak gönder
        const response = await fetch(`${API_BASE}/admin/sifirla?masterKey=${masterKey}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            const mesaj = await response.text();
            gosterMesaj(mesaj, 'green');
            projeleriGetir(); // Listeyi temizle
        } else if (response.status === 401) { // 401 YETKİSİZ HATASI
            gosterMesaj("Sıfırlama Başarısız: Girdiğiniz yönetici şifresi yanlış.", 'red');
        } else {
            const errorText = await response.text();
            gosterMesaj(`Sıfırlama Başarısız: Sunucu Hatası: ${errorText}`, 'red');
        }
    } catch (error) {
        console.error('Sıfırlama hatası:', error);
        gosterMesaj('Sunucuya bağlanırken hata oluştu. Silme işlemi tamamlanamadı.', 'red');
    }
}


// Sayfa yüklendiğinde listeyi getir (Aynı Kalır)
window.onload = projeleriGetir;