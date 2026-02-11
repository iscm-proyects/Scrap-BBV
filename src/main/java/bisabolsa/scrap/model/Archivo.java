package bisabolsa.scrap.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "archivos_descargados")
@Data
public class Archivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreArchivo; // Nombre lógico (ej: Balance_2024.pdf)

    private String nombreFisico;  // Nombre en disco (ej: Balance_2024_v20250116.pdf)

    @Column(columnDefinition = "TEXT")
    private String urlOrigen;

    private boolean descargadoExitosamente;

    private LocalDateTime fechaIntento;

    private String hashSha256; // La huella digital del archivo

    private String estado; // "NUEVO", "ACTUALIZADO", "DUPLICADO_IGNORADO", "ERROR"

    @ManyToOne
    @JoinColumn(name = "emisor_codigo", nullable = false)
    private Emisor emisor;
}