package bisabolsa.scrap.controller;

import bisabolsa.scrap.dto.EmisorDetalleDTO;
import bisabolsa.scrap.dto.ScrapingReporte;
import bisabolsa.scrap.model.Archivo;
import bisabolsa.scrap.model.AsfiHechoRelevante;
import bisabolsa.scrap.service.AsfiScraperService;
import bisabolsa.scrap.service.BbvScraperService;
import bisabolsa.scrap.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scraper")
public class ScraperController {

    @Autowired
    private BbvScraperService scraperService;

    @Autowired
    private AsfiScraperService asfiScraperService;

    @Autowired
    private FileService fileService;

    // Endpoint 1: Obtener la lista de emisores (La primera pantalla)
    // URL: http://localhost:5051/api/scraper/listar-emisores
    @GetMapping("/listar-emisores")
    public ResponseEntity<String> listarEmisores() {
        try {
            scraperService.iniciarScrapingListaPrincipal();
            return ResponseEntity.ok("Proceso de listado finalizado con éxito. Revisa la base de datos.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    // Endpoint 2: Descargar detalles y PDFs de todos los emisores guardados
    // URL: http://localhost:5051/api/scraper/procesar-detalles
    @GetMapping("/procesar-detalles")
    public ResponseEntity<ScrapingReporte> procesarDetalles() {
        try {
            ScrapingReporte reporte = scraperService.procesarDetallesDeEmisoresGuardados();
            return ResponseEntity.ok(reporte);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/procesar-emisiones-api")
    public ResponseEntity<ScrapingReporte> procesarEmisionesApi() {
        try {
            ScrapingReporte reporte = scraperService.procesarSoloEmisionesApi();
            return ResponseEntity.ok(reporte);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }





    // 1. Listar Archivos BBV
    // GET http://localhost:5051/api/scraper/archivos/bbv
    @GetMapping("/archivos/bbv")
    public ResponseEntity<List<Archivo>> listarBbv() {
        return ResponseEntity.ok(fileService.listarArchivosBbv());
    }

    // 2. Listar Archivos ASFI
    // GET http://localhost:5051/api/scraper/archivos/asfi
    @GetMapping("/archivos/asfi")
    public ResponseEntity<List<AsfiHechoRelevante>> listarAsfi() {
        return ResponseEntity.ok(fileService.listarArchivosAsfi());
    }

    // 3. Descargar Archivo Físico
    // GET http://localhost:5051/api/scraper/descargar/bbv/123
    // GET http://localhost:5051/api/scraper/descargar/asfi/45
    @GetMapping("/descargar/{tabla}/{id}")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable String tabla, @PathVariable Long id) {
        try {
            Resource archivo = fileService.cargarArchivo(tabla, id);
            String nombreArchivo = fileService.obtenerNombreArchivo(tabla, id);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF) // Asumimos PDF, o usa MediaType.APPLICATION_OCTET_STREAM
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .body(archivo);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    // Endpoint 4: Scraping ASFI Hechos Relevantes con Gestión Dinámica
    // Uso:
    // - http://localhost:5051/api/scraper/asfi/hechos-relevantes (Usa año actual)
    // - http://localhost:5051/api/scraper/asfi/hechos-relevantes?gestion=2025
    @GetMapping("/asfi/hechos-relevantes")
    public ResponseEntity<String> procesarAsfi(@RequestParam(name = "gestion", required = false) Integer gestion) {
        try {
            // Si no envían gestión, usamos el año actual del sistema
            int anioProcesar = (gestion != null) ? gestion : java.time.LocalDate.now().getYear();

            String resultado = asfiScraperService.iniciarScrapingAsfi(anioProcesar);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    // Endpoint 5: Obtener detalle completo de un emisor y sus archivos
    // GET http://localhost:5051/api/scraper/emisor/BIA-emi
    @GetMapping("/emisor/{codigo}")
    public ResponseEntity<EmisorDetalleDTO> obtenerEmisorDetalle(@PathVariable String codigo) {
        try {
            EmisorDetalleDTO detalle = fileService.obtenerDetalleEmisor(codigo);
            return ResponseEntity.ok(detalle);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}