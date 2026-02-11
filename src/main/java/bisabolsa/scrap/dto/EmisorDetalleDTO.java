package bisabolsa.scrap.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@JsonPropertyOrder({ "codigo", "nombre", "sector", "urlPerfil", "ultimoEscaneo", "archivos_Descargados" })
public class EmisorDetalleDTO {
    private String codigo;
    private String nombre;
    private String sector;
    private String urlPerfil;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime ultimoEscaneo;
    @JsonProperty("archivos_Descargados")
    private List<ArchivoResumenDTO> archivosDescargados;
}