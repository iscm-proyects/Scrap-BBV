package bisabolsa.scrap.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ScrapingReporte {
    private int totalProcesados = 0;
    private List<String> nuevos = new ArrayList<>();
    private List<String> actualizados = new ArrayList<>();
    private List<String> errores = new ArrayList<>();

    public void agregarNuevo(String archivo) { nuevos.add(archivo); }
    public void agregarActualizado(String archivo) { actualizados.add(archivo); }
    public void agregarError(String archivo) { errores.add(archivo); }
}