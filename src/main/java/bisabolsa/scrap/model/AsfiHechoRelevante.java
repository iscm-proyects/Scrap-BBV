package bisabolsa.scrap.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "asfi_hechos_relevantes")
@Data
public class AsfiHechoRelevante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entidad;      // Ej: BANCO BISA S.A.
    private String fechaOrigen;  // La fecha que sale en la tabla

    @Column(columnDefinition = "TEXT")
    private String referencia;   // El texto descriptivo

    private String tokenDescarga; // El ID o Token único del archivo

    private String nombreArchivo; // Nombre con el que lo guardamos

    private boolean descargado;

    private LocalDateTime fechaRegistro;
}