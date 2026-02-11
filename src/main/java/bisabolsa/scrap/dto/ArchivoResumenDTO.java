package bisabolsa.scrap.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class ArchivoResumenDTO {
    private Long id;
    private String nombreArchivo;
    private String nombreFisico;
    private String estado; // NUEVO, ACTUALIZADO
    private boolean descargado;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaDescarga;
    private String urlOrigen;
}
