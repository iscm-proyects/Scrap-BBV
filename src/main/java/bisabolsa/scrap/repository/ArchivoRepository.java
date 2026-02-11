package bisabolsa.scrap.repository;

import bisabolsa.scrap.model.Archivo;
import bisabolsa.scrap.model.Emisor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArchivoRepository extends JpaRepository<Archivo, Long> {

    Optional<Archivo> findTopByEmisorAndNombreArchivoAndDescargadoExitosamenteTrueOrderByFechaIntentoDesc(
            Emisor emisor, String nombreArchivo
    );

    List<Archivo> findByEmisor_CodigoOrderByFechaIntentoDesc(String codigoEmisor);
}