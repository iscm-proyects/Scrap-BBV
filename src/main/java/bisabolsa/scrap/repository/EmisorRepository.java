package bisabolsa.scrap.repository;

import bisabolsa.scrap.model.Emisor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmisorRepository extends JpaRepository<Emisor, String> {
}