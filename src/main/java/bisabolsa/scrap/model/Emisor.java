package bisabolsa.scrap.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "emisores")
@Data
public class Emisor {

    @Id
    private String codigo;

    private String nombre;

    private String sector;

    private String urlPerfil;

    @Column(columnDefinition = "TEXT")
    private String descripcionDetallada;

    private boolean procesado;

    private LocalDateTime updateData;
}