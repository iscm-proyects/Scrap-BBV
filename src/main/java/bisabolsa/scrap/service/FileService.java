package bisabolsa.scrap.service;

import bisabolsa.scrap.dto.ArchivoResumenDTO;
import bisabolsa.scrap.dto.EmisorDetalleDTO;
import bisabolsa.scrap.model.Archivo;
import bisabolsa.scrap.model.AsfiHechoRelevante;
import bisabolsa.scrap.model.Emisor;
import bisabolsa.scrap.repository.ArchivoRepository;
import bisabolsa.scrap.repository.AsfiRepository;
import bisabolsa.scrap.repository.EmisorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileService {

    @Autowired
    private ArchivoRepository archivoRepository;

    @Autowired
    private AsfiRepository asfiRepository;

    @Autowired
    private EmisorRepository emisorRepository;

    // Inyectamos la misma ruta base que usan los scrapers
    @Value("${scraping.paths.final:downloads}")
    private String finalPathString;

    // --- 1. Listar Archivos BBV ---
    public List<Archivo> listarArchivosBbv() {
        // Podrías agregar paginación aquí si son demasiados,
        // pero por ahora devolvemos la lista completa orden por fecha desc
        return archivoRepository.findAll();
    }

    // --- 2. Listar Archivos ASFI ---
    public List<AsfiHechoRelevante> listarArchivosAsfi() {
        return asfiRepository.findAll();
    }

    // --- 3. Obtener Recurso Físico (Para descarga) ---
    public Resource cargarArchivo(String tabla, Long id) throws FileNotFoundException, MalformedURLException {
        Path rutaArchivo = null;

        if ("bbv".equalsIgnoreCase(tabla)) {
            // Lógica para BBV
            Archivo archivo = archivoRepository.findById(id)
                    .orElseThrow(() -> new FileNotFoundException("Registro BBV no encontrado con ID: " + id));

            // La ruta en BBV es: ROOT/CODIGO_EMISOR/NOMBRE_FISICO
            String nombreReal = (archivo.getNombreFisico() != null) ? archivo.getNombreFisico() : archivo.getNombreArchivo();
            rutaArchivo = Paths.get(finalPathString)
                    .resolve(archivo.getEmisor().getCodigo())
                    .resolve(nombreReal);

        } else if ("asfi".equalsIgnoreCase(tabla)) {
            // Lógica para ASFI
            AsfiHechoRelevante asfi = asfiRepository.findById(id)
                    .orElseThrow(() -> new FileNotFoundException("Registro ASFI no encontrado con ID: " + id));

            // La ruta en ASFI es: ROOT/ASFI_HechosRelevantes/NOMBRE_ARCHIVO
            rutaArchivo = Paths.get(finalPathString)
                    .resolve("ASFI_HechosRelevantes")
                    .resolve(asfi.getNombreArchivo());

        } else {
            throw new IllegalArgumentException("Tabla desconocida. Use 'bbv' o 'asfi'.");
        }

        // Verificar existencia física y devolver recurso
        Resource resource = new UrlResource(rutaArchivo.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new FileNotFoundException("El archivo físico no existe en la ruta: " + rutaArchivo.toString());
        }
    }

    // Método auxiliar para obtener el nombre del archivo para el header de descarga
    public String obtenerNombreArchivo(String tabla, Long id) {
        if ("bbv".equalsIgnoreCase(tabla)) {
            return archivoRepository.findById(id)
                    .map(a -> a.getNombreFisico() != null ? a.getNombreFisico() : a.getNombreArchivo())
                    .orElse("archivo.pdf");
        } else {
            return asfiRepository.findById(id)
                    .map(AsfiHechoRelevante::getNombreArchivo)
                    .orElse("archivo.pdf");
        }
    }

    public EmisorDetalleDTO obtenerDetalleEmisor(String codigoEmisor) {
        // 1. Buscar el Emisor (Si no existe, lanzamos excepción o retornamos null)
        Emisor emisor = emisorRepository.findById(codigoEmisor)
                .orElseThrow(() -> new RuntimeException("Emisor no encontrado: " + codigoEmisor));

        // 2. Buscar sus archivos
        List<Archivo> archivos = archivoRepository.findByEmisor_CodigoOrderByFechaIntentoDesc(codigoEmisor);

        // 3. Convertir Entidad Emisor -> DTO
        EmisorDetalleDTO dto = new EmisorDetalleDTO();
        dto.setCodigo(emisor.getCodigo());
        dto.setNombre(emisor.getNombre());
        dto.setSector(emisor.getSector());
        dto.setUrlPerfil(emisor.getUrlPerfil());
        dto.setUltimoEscaneo(emisor.getUpdateData());

        // 4. Convertir Lista de Archivos -> Lista de DTOs
        List<ArchivoResumenDTO> archivosDto = archivos.stream().map(archivo -> {
            ArchivoResumenDTO aDto = new ArchivoResumenDTO();
            aDto.setId(archivo.getId());
            aDto.setNombreArchivo(archivo.getNombreArchivo());
            aDto.setNombreFisico(archivo.getNombreFisico());
            aDto.setEstado(archivo.getEstado());
            aDto.setDescargado(archivo.isDescargadoExitosamente());
            aDto.setFechaDescarga(archivo.getFechaIntento());
            aDto.setUrlOrigen(archivo.getUrlOrigen());
            return aDto;
        }).collect(java.util.stream.Collectors.toList());

        dto.setArchivosDescargados(archivosDto);

        return dto;
    }
}