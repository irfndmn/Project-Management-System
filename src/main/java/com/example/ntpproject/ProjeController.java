package com.example.ntpproject;


import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@RestController
@RequestMapping("/api/projeler")
public class ProjeController {

    // 1. Alan Tanımlamaları
    private final ProjeService projeService;
    @Value("${admin.master.password}")
    private String adminMasterPassword;
    private final JdbcTemplate jdbcTemplate; // Final olarak tanımlandı

    // 2. Constructor (Yapıcı Metot) - @Autowired gerekmez, DI otomatik yapar
    public ProjeController(ProjeService projeService, JdbcTemplate jdbcTemplate) {
        this.projeService = projeService;
        this.jdbcTemplate = jdbcTemplate; // Hata burada düzeltildi: Atama doğru yapıldı
    }

    // 1. ÖĞRENCİ KAYIT (POST)
    @PostMapping("/kaydet")
    // Formdan gelen JSON verisini doğrudan DTO'ya map eder.
    public Proje yeniKayitOlustur(@RequestBody ProjeKayitRequest request) { // DTO adı düzeltildi
        try {
            return projeService.yeniKayitOlustur(request);
        } catch (RuntimeException e) {
            // Service katmanından gelen tüm hataları (kontenjan, format, vb.) döndürürüz.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // 2. TÜM PROJELERİ LİSTELEME (GET) - Frontend için JSON çıktısı
    @GetMapping
    public List<Proje> tumProjeleriListele() {
        return projeService.tumKayitlariGetir();
    }


    // 3. EXCEL/CSV İNDİRME (GET) - Hoca için
    @GetMapping("/excel")
    public void indirExcel(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"projeler_kayitlari.csv\"");

        List<Proje> projeler = projeService.tumKayitlariGetir();

        try (PrintWriter writer = response.getWriter()) {
            // Başlık satırı
            writer.println("Proje ID,Öğrenci Adı,Öğrenci Soyadı,Okul No,Email,Proje Adı,Proje Açıklaması,Grup Büyüklüğü");

            // Veri satırları
            for (Proje p : projeler) {
                // Not: Proje adı içinde virgül olabilir, bu yüzden Excel'de bozulmaması için tırnak ("") içine alıyoruz.
                String satir = String.format("%d,\"%s\",\"%s\",%s,%s,\"%s\",\"%s\",%d",
                        p.getId(),
                        p.getOgrenciIsim(),
                        p.getOgrenciSoyad(),
                        p.getOkulNo(),
                        p.getEmail(),
                        p.getProjeAdi().replace("\"", "\"\""), // İç tırnakları Excel için çift tırnağa çevir
                        p.getProjeAciklamasi().replace("\"", "\"\"").replace("\n", " "),
                        p.getAyniProjeKayitlari().size()
                );
                writer.println(satir);
            }
        }
    }

    // ProjeController.java içinde

// ... (Diğer metodlar) ...

    // 4. VERİTABANI SIFIRLAMA (DELETE) - Yönetici için
    // ProjeController.java içinde

// ... (Diğer metodlar) ...

    @DeleteMapping("/admin/sifirla")
    // Şifreyi URL'den 'masterKey' adında bir parametre olarak bekler
    public ResponseEntity<String> sifirlaVeritabani(@RequestParam String masterKey) {

        // 1. GİZLİ MASTER ŞİFRE KONTROLÜ
        if (!adminMasterPassword.equals(masterKey)) {
            // Şifre yanlışsa 401 Unauthorized (Yetkisiz) döndür
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Yetkisiz Erişim. Yönetici şifresi yanlış.");
        }

        try {
            // 2. ÖNCE ÇOCUK TABLOYU SİL (Foreign Key bağımlılığını çözer)
            String deleteElementCollectionSql = "DELETE FROM PROJE_AYNI_PROJE_KAYITLARI";
            jdbcTemplate.update(deleteElementCollectionSql);

            // 3. SONRA EBEVEYN TABLOYU SİL
            String deleteSql = "DELETE FROM PROJE";
            jdbcTemplate.update(deleteSql);

            // 4. IDENTITY kolunun sayacını sıfırla (H2 için doğru syntax)
            String restartIdentitySql = "ALTER TABLE PROJE ALTER COLUMN ID RESTART WITH 1";
            jdbcTemplate.update(restartIdentitySql);

            return ResponseEntity.ok("Veritabanındaki tum proje kayitlari basariyla sıfırlandı.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Veritabanı sıfırlanırken bir hata oluştu: " + e.getMessage());
        }
    }
    }




