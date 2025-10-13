package com.example.ntpproject;


import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.apache.commons.lang3.StringUtils; // Levenshtein için gerekli

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProjeService {

    private final ProjeRepository projeRepository;
    private final WebClient webClient;

    private static final int MAX_KISI = 3;
    private static final double JACCARD_ESIGI = 0.55;
    private static final int MAX_LEVENSHTEIN_HATASI = 5;
    private static final String GPT_MODEL = "gpt-3.5-turbo"; // Kullanılacak model
    // E-posta formatını kontrol eden Pattern objesi
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^\\d{9}@firat\\.edu\\.tr$", Pattern.CASE_INSENSITIVE);

    // YENİ: Okul Numarası formatını kontrol eden Pattern objesi (Tam 9 rakam)
    private static final Pattern OKUL_NO_PATTERN =
            Pattern.compile("^\\d{9}$");

    // application.properties'den (veya ENV'den) OpenAI API key'i al
    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Autowired
    public ProjeService(ProjeRepository projeRepository, WebClient.Builder webClientBuilder) {
        this.projeRepository = projeRepository;
        // OpenAI Base URL'ini belirle
        this.webClient = webClientBuilder.baseUrl("https://api.openai.com/v1/chat/completions").build();
    }

    /*
     * BÖLÜM I: YEREL VE HIZLI KONTROL METOTLARI (Aynı Kalır)
     * (Bu metotlar, projelerTokenOlarakBenzerMi adıyla önceki cevapta detaylı verilmişti.)
     */
    private boolean projelerTokenOlarakBenzerMi(String mevcutAdi, String yeniAdi) {
        if (mevcutAdi == null || yeniAdi == null) return false;

        String mLower = mevcutAdi.toLowerCase().trim();
        String yLower = yeniAdi.toLowerCase().trim();

        // 1. Yazım Hatası ve Kapsama Kontrolü (Levenshtein + Contains)
        int distance = StringUtils.getLevenshteinDistance(mLower, yLower);
        if (distance <= MAX_LEVENSHTEIN_HATASI || mLower.contains(yLower) || yLower.contains(mLower)) {
            return true;
        }

        // 2. Kilit Kelime Eşdeğerliği Kontrolü
        boolean kilitKelimeEslesmesi = (mLower.contains("rent a car") && (yLower.contains("kiralama") || yLower.contains("araba"))) ||
                (yLower.contains("rent a car") && (mLower.contains("kiralama") || mLower.contains("araba"))) ||
                (mLower.contains("e-ticaret") && yLower.contains("ecommerce")) ||
                (yLower.contains("e-ticaret") && mLower.contains("ecommerce"));

        if (kilitKelimeEslesmesi) {
            return true;
        }

        // 3. Jaccard Benzerliği
        Set<String> set1 = new HashSet<>(Arrays.asList(mLower.split("\\s+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(yLower.split("\\s+")));
        Set<String> kesisim = new HashSet<>(set1); kesisim.retainAll(set2);
        Set<String> birlesim = new HashSet<>(set1); birlesim.addAll(set2);

        if (!birlesim.isEmpty() && (double) kesisim.size() / birlesim.size() >= JACCARD_ESIGI) {
            return true;
        }

        return false;
    }


    /**
     * Yerel kontrol yetersiz kaldığında devreye girer. OpenAI API'yi kullanarak semantik kontrol yapar.
     */
    private boolean projelerGPTIleKontrolEt(String mevcutAdi, String yeniAdi) {
        System.out.println("Yerel benzerlik yetersiz. GPT ile semantik kontrol ediliyor...");

        // Prompt'u İngilizceye çevirerek GPT'nin kısıtlamalara uyma oranını artırıyoruz.
        String prompt = String.format(
                "Are these two project titles the same project concept? Title 1: '%s'. Title 2: '%s'. Respond only with 'YES' or 'NO'.",
                mevcutAdi, yeniAdi
        );

        String payload = String.format("""
            {
                "model": "%s",
                "messages": [{"role": "user", "content": "%s"}],
                "max_tokens": 5,
                "temperature": 0.0
            }
            """, GPT_MODEL, prompt.replace("\"", "\\\""));

        try {
            // OpenAI API Çağrısı (Senkron)
            String responseBody = webClient.post()
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // JSON yanıtını işleme
            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody);
            String gptCevap = root.path("choices").get(0).path("message").path("content")
                    .asText().trim().toUpperCase();

            // Cevabın "YES" ile başlaması yeterlidir.
            return gptCevap.startsWith("YES");

        } catch (Exception e) {
            // API hatası durumunda, güvenliği sağlamak için varsayılan olarak HAYIR kabul et.
            System.err.println("OpenAI API hatası: " + e.getMessage() + ". Varsayılan olarak HAYIR kabul ediliyor.");
            return false;
        }
    }

    /*
     * BÖLÜM II: ANA İŞ MANTIĞI
     */

    /**
     * Yeni bir öğrenci ve proje kaydı oluşturur, İKİ SEVİYELİ benzerlik kontrolü yapar.
     */
    public Proje yeniKayitOlustur(ProjeKayıtRequest request) {
        // YENİ GÜVENLİK KONTROLÜ: Okul No Formatı
        if (request.getOkulNo() == null || !OKUL_NO_PATTERN.matcher(request.getOkulNo()).matches()) {
            throw new RuntimeException("Veri Bütünlüğü Hatası: Okul Numarası formatı geçersiz. Tam 9 rakam bekleniyor.");
        }

        // E-posta Formatı Kontrolü
        if (request.getEmail() == null || !EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            throw new RuntimeException("Veri Bütünlüğü Hatası: E-posta formatı geçersiz. 9 Rakam@firat.edu.tr bekleniyor.");
        }

        if (projeRepository.existsByOkulNo(request.getOkulNo())) {
            throw new RuntimeException("Hata: " + request.getOkulNo() + " numaralı öğrenci zaten bir projeye kayıtlıdır.");
        }

        List<Proje> tumMevcutProjeler = projeRepository.findAll();
        String yeniProjeAdi = request.getProjeAdi();

        // İKİ SEVİYELİ FİLTRELEME: Önce yerel (hızlı), sonra AI (doğru)
        List<Proje> benzerProjeler = tumMevcutProjeler.stream()
                .filter(p -> {
                    // Seviye 1: Kararlı yerel kontrol
                    if (projelerTokenOlarakBenzerMi(p.getProjeAdi(), yeniProjeAdi)) {
                        return true;
                    }
                    // Seviye 2: Yerel kontrol yetersizse, GPT'ye sor
                    return projelerGPTIleKontrolEt(p.getProjeAdi(), yeniProjeAdi);
                })
                .collect(Collectors.toList());

        // 4. Kontenjan Kontrolü ve sonrası aynı kalır...
        if (benzerProjeler.size() >= MAX_KISI) {
            throw new RuntimeException("Kontenjan Dolu: '" + yeniProjeAdi + "' projesi, benzer projelerle birlikte "
                    + MAX_KISI + " kişi sınırına ulaşmıştır. Lütfen farklı bir otomasyon/konu seçin.");
        }

        // 5. Yeni Proje Kaydını Oluştur ve Kaydet (Lombok ile set/get kullanılır)
        Proje yeniProje = new Proje();
        yeniProje.setOgrenciIsim(request.getOgrenciIsim());
        yeniProje.setOgrenciSoyad(request.getOgrenciSoyad());
        yeniProje.setOkulNo(request.getOkulNo());
        yeniProje.setEmail(request.getEmail());
        yeniProje.setProjeAdi(yeniProjeAdi);
        yeniProje.setProjeAciklamasi(request.getProjeAciklamasi());

        Proje kaydedilenProje = projeRepository.save(yeniProje);

        // 6. Grup Listesini Güncelle
        List<Long> tumGrupIDleri = new ArrayList<>();
        tumGrupIDleri.add(kaydedilenProje.getId());

        for (Proje benzerProje : benzerProjeler) {
            if (benzerProje.getId() != null) {
                tumGrupIDleri.add(benzerProje.getId());
            }
            if (kaydedilenProje.getId() != null && !benzerProje.getAyniProjeKayitlari().contains(kaydedilenProje.getId())) {
                benzerProje.getAyniProjeKayitlari().add(kaydedilenProje.getId());
                projeRepository.save(benzerProje);
            }
        }

        kaydedilenProje.setAyniProjeKayitlari(tumGrupIDleri.stream().distinct().collect(Collectors.toList()));
        return projeRepository.save(kaydedilenProje);
    }
    /**
     * Tüm proje kayıtlarını döndürür.
     */
    public List<Proje> tumKayitlariGetir() {
        return projeRepository.findAll();
    }

    // ... (tumKayitlariGetir metodu aynı kalır) ...
}