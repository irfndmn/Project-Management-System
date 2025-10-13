package com.example.ntpproject; // Kendi paket adınızı kullanın

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;


@Entity
@Data // Getter, Setter, toString, hashCode ve equals otomatik eklenir
@NoArgsConstructor // JPA için gerekli boş yapıcı metodu ekler
public class Proje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Öğrenci bilgileri
    private String ogrenciIsim;
    private String ogrenciSoyad;
    private String okulNo;
    private String email;

    // Proje bilgileri
    private String projeAdi;
    @Column(length = 1000)
    private String projeAciklamasi;

    // Aynı projeyi alan diğer öğrencilerin ID'leri
    @ElementCollection
    private List<Long> ayniProjeKayitlari = new ArrayList<>();
}