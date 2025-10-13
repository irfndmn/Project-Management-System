package com.example.ntpproject;
import lombok.Data;

@Data
public class ProjeKayıtRequest {
    private String ogrenciIsim;
    private String ogrenciSoyad;
    private String okulNo;
    private String email;
    private String projeAdi;
    private String projeAciklamasi;
}
