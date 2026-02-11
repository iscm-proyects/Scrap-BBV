package bisabolsa.scrap.repository;

import bisabolsa.scrap.model.AsfiHechoRelevante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AsfiRepository extends JpaRepository<AsfiHechoRelevante, Long> {
    // Para verificar si ya descargamos este token específico
    boolean existsByTokenDescarga(String tokenDescarga);
}