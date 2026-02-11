package bisabolsa.scrap.controller;

import bisabolsa.scrap.dto.EmisorDetalleDTO;
import bisabolsa.scrap.dto.ScrapingReporte;
import bisabolsa.scrap.model.Archivo;
import bisabolsa.scrap.model.AsfiHechoRelevante;
import bisabolsa.scrap.model.Emisor;
import bisabolsa.scrap.service.AsfiScraperService;
import bisabolsa.scrap.service.BbvScraperService;
import bisabolsa.scrap.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
class ScraperControllerDocsTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private BbvScraperService bbvScraperService;

    @MockitoBean
    private AsfiScraperService asfiScraperService;

    @MockitoBean
    private FileService fileService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocumentation).uris().withScheme("http").withHost("localhost").withPort(5051))
                .build();
    }

    @Test
    void listarEmisores() throws Exception {
        this.mockMvc.perform(get("/api/scraper/listar-emisores"))
                .andExpect(status().isOk())
                .andDo(document("listar-emisores"));
    }

    @Test
    void procesarDetalles() throws Exception {
        ScrapingReporte reporte = new ScrapingReporte();
        reporte.setTotalProcesados(10);
        reporte.setNuevos(List.of("Archivo1.pdf"));
        reporte.setActualizados(List.of("Archivo2.pdf"));
        reporte.setErrores(new ArrayList<>());

        given(bbvScraperService.procesarDetallesDeEmisoresGuardados()).willReturn(reporte);

        this.mockMvc.perform(get("/api/scraper/procesar-detalles"))
                .andExpect(status().isOk())
                .andDo(document("procesar-detalles",
                        responseFields(
                                fieldWithPath("totalProcesados").description("Total de emisores procesados"),
                                fieldWithPath("nuevos").description("Lista de archivos nuevos"),
                                fieldWithPath("actualizados").description("Lista de archivos actualizados"),
                                fieldWithPath("errores").description("Lista de errores")
                        )));
    }

    @Test
    void procesarEmisionesApi() throws Exception {
        ScrapingReporte reporte = new ScrapingReporte();
        reporte.setTotalProcesados(5);
        reporte.setNuevos(List.of("Emision1.pdf"));

        given(bbvScraperService.procesarSoloEmisionesApi()).willReturn(reporte);

        this.mockMvc.perform(get("/api/scraper/procesar-emisiones-api"))
                .andExpect(status().isOk())
                .andDo(document("procesar-emisiones-api",
                        responseFields(
                                fieldWithPath("totalProcesados").description("Total de emisiones procesadas"),
                                fieldWithPath("nuevos").description("Lista de nuevas emisiones"),
                                fieldWithPath("actualizados").description("Lista de emisiones actualizadas"),
                                fieldWithPath("errores").description("Lista de errores")
                        )));
    }

    @Test
    void listarBbv() throws Exception {
        Archivo archivo = new Archivo();
        archivo.setId(1L);
        archivo.setNombreArchivo("Balance_2024.pdf");
        archivo.setNombreFisico("Balance_2024_v1.pdf");
        archivo.setUrlOrigen("http://example.com/file.pdf");
        archivo.setDescargadoExitosamente(true);
        archivo.setFechaIntento(LocalDateTime.now());
        archivo.setHashSha256("hash123");
        archivo.setEstado("NUEVO");

        Emisor emisor = new Emisor();
        emisor.setCodigo("BIA-emi");
        archivo.setEmisor(emisor);

        given(fileService.listarArchivosBbv()).willReturn(List.of(archivo));

        this.mockMvc.perform(get("/api/scraper/archivos/bbv"))
                .andExpect(status().isOk())
                .andDo(document("archivos-bbv",
                        responseFields(
                                fieldWithPath("[].id").description("ID del archivo"),
                                fieldWithPath("[].nombreArchivo").description("Nombre lógico del archivo"),
                                fieldWithPath("[].nombreFisico").description("Nombre físico en disco"),
                                fieldWithPath("[].urlOrigen").description("URL de origen"),
                                fieldWithPath("[].descargadoExitosamente").description("Indicador de descarga exitosa"),
                                fieldWithPath("[].fechaIntento").description("Fecha del intento de descarga"),
                                fieldWithPath("[].hashSha256").description("Hash SHA-256 del archivo"),
                                fieldWithPath("[].estado").description("Estado del archivo"),
                                fieldWithPath("[].emisor").description("Datos del emisor asociado"),
                                fieldWithPath("[].emisor.codigo").description("Código del emisor"),
                                fieldWithPath("[].emisor.nombre").description("Nombre del emisor").optional(),
                                fieldWithPath("[].emisor.sector").description("Sector del emisor").optional(),
                                fieldWithPath("[].emisor.urlPerfil").description("URL del perfil del emisor").optional(),
                                fieldWithPath("[].emisor.descripcionDetallada").description("Descripción detallada").optional(),
                                fieldWithPath("[].emisor.procesado").description("Indicador de procesado"),
                                fieldWithPath("[].emisor.updateData").description("Fecha de actualización").optional()
                        )));
    }

    @Test
    void listarAsfi() throws Exception {
        AsfiHechoRelevante hecho = new AsfiHechoRelevante();
        hecho.setId(1L);
        hecho.setEntidad("Banco BISA");
        hecho.setFechaOrigen("2024-01-01");
        hecho.setReferencia("Hecho Relevante");
        hecho.setTokenDescarga("token123");
        hecho.setNombreArchivo("hr.pdf");
        hecho.setDescargado(true);
        hecho.setFechaRegistro(LocalDateTime.now());

        given(fileService.listarArchivosAsfi()).willReturn(List.of(hecho));

        this.mockMvc.perform(get("/api/scraper/archivos/asfi"))
                .andExpect(status().isOk())
                .andDo(document("archivos-asfi",
                        responseFields(
                                fieldWithPath("[].id").description("ID del hecho relevante"),
                                fieldWithPath("[].entidad").description("Nombre de la entidad"),
                                fieldWithPath("[].fechaOrigen").description("Fecha de origen"),
                                fieldWithPath("[].referencia").description("Referencia o descripción"),
                                fieldWithPath("[].tokenDescarga").description("Token de descarga"),
                                fieldWithPath("[].nombreArchivo").description("Nombre del archivo guardado"),
                                fieldWithPath("[].descargado").description("Indicador de descarga"),
                                fieldWithPath("[].fechaRegistro").description("Fecha de registro en el sistema")
                        )));
    }

    @Test
    void descargarArchivo() throws Exception {
        ByteArrayResource resource = new ByteArrayResource("PDF Content".getBytes());
        given(fileService.cargarArchivo(eq("bbv"), eq(123L))).willReturn(resource);
        given(fileService.obtenerNombreArchivo(eq("bbv"), eq(123L))).willReturn("archivo.pdf");

        this.mockMvc.perform(get("/api/scraper/descargar/{tabla}/{id}", "bbv", 123))
                .andExpect(status().isOk())
                .andDo(document("descargar-archivo",
                        pathParameters(
                                parameterWithName("tabla").description("Nombre de la tabla (bbv, asfi)"),
                                parameterWithName("id").description("ID del archivo")
                        )));
    }

    @Test
    void procesarAsfi() throws Exception {
        given(asfiScraperService.iniciarScrapingAsfi(anyInt())).willReturn("Scraping ASFI completado");

        this.mockMvc.perform(get("/api/scraper/asfi/hechos-relevantes")
                        .param("gestion", "2025"))
                .andExpect(status().isOk())
                .andDo(document("asfi-hechos-relevantes",
                        queryParameters(
                                parameterWithName("gestion").description("Año de gestión para el scraping (opcional)").optional()
                        )));
    }

    @Test
    void obtenerEmisorDetalle() throws Exception {
        EmisorDetalleDTO dto = new EmisorDetalleDTO();
        dto.setCodigo("BIA-emi");
        dto.setNombre("Banco BISA");
        dto.setSector("Banca");
        dto.setUrlPerfil("http://example.com");
        dto.setUltimoEscaneo(LocalDateTime.now());
        dto.setArchivosDescargados(Collections.emptyList());

        given(fileService.obtenerDetalleEmisor("BIA-emi")).willReturn(dto);

        this.mockMvc.perform(get("/api/scraper/emisor/{codigo}", "BIA-emi"))
                .andExpect(status().isOk())
                .andDo(document("obtener-emisor-detalle",
                        pathParameters(
                                parameterWithName("codigo").description("Código del emisor")
                        ),
                        responseFields(
                                fieldWithPath("codigo").description("Código del emisor"),
                                fieldWithPath("nombre").description("Nombre del emisor"),
                                fieldWithPath("sector").description("Sector del emisor"),
                                fieldWithPath("urlPerfil").description("URL del perfil"),
                                fieldWithPath("ultimoEscaneo").description("Fecha del último escaneo"),
                                fieldWithPath("archivos_Descargados").description("Lista de archivos descargados")
                        )));
    }
}
