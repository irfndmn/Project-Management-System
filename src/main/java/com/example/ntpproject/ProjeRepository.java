package com.example.ntpproject;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjeRepository extends JpaRepository<Proje, Long> {

    // Aynı proje adına sahip TÜM kayıtları listelemek için.
    // Kontenjan ve benzerlik kontrolü bununla yapılacak.
    List<Proje> findAllByProjeAdiIgnoreCase(String projeAdi);

    // Okul numarasına göre bu öğrencinin zaten bir proje almış olup olmadığını kontrol etmek için.
    // Bir öğrenci sadece 1 projeye kayıt olabilir kuralını eklersek bu lazım olur.
    boolean existsByOkulNo(String okulNo);
}
