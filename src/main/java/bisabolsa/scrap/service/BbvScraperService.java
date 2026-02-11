package bisabolsa.scrap.service;

import bisabolsa.scrap.dto.ScrapingReporte;
import bisabolsa.scrap.model.Archivo;
import bisabolsa.scrap.model.Emisor;
import bisabolsa.scrap.repository.ArchivoRepository;
import bisabolsa.scrap.repository.EmisorRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class BbvScraperService {

    @Autowired
    private EmisorRepository emisorRepository;

    @Autowired
    private ArchivoRepository archivoRepository;

    private final String BASE_URL = "https://www2.bbv.com.bo";
    private final String LISTADO_URL = "https://www2.bbv.com.bo/participantes-del-mercado/";
    private final String API_URL_FRAGMENT = "api/cargaArchivos/descargarprospecto";

    // Inyección de propiedades desde application.properties
    @Value("${scraping.paths.temp:temp_downloads}")
    private String tempPathString;

    @Value("${scraping.paths.final:downloads}")
    private String finalPathString;

    // Variables Path que se inicializarán en el @PostConstruct
    private Path tempDir;
    private Path finalDir;

    // Cliente Java Fallback
    private HttpClient javaHttpClient;

    public BbvScraperService() {
    }

    /**
     * Se ejecuta automáticamente después de que Spring inyecta las dependencias y valores.
     */
    @PostConstruct
    public void init() {
        try {
            // Inicializar Paths con los valores del properties
            this.tempDir = Paths.get(tempPathString);
            this.finalDir = Paths.get(finalPathString);

            // Crear directorios si no existen
            if (!Files.exists(this.tempDir)) Files.createDirectories(this.tempDir);
            if (!Files.exists(this.finalDir)) Files.createDirectories(this.finalDir);

            System.out.println("Directorios configurados:");
            System.out.println(" - Temp: " + this.tempDir.toAbsolutePath());
            System.out.println(" - Final: " + this.finalDir.toAbsolutePath());

            // Inicializar cliente HTTP
            inicializarClienteJava();

        } catch (Exception e) {
            throw new RuntimeException("Error inicializando directorios de descarga", e);
        }
    }

    // =================================================================================
    // FASE 1: LISTADO PRINCIPAL
    // =================================================================================
    public void iniciarScrapingListaPrincipal() {
        WebDriver driver = null;
        try {
            System.out.println("--- Iniciando Scraping de Lista Principal ---");
            driver = iniciarDriver();
            driver.get(LISTADO_URL);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.presenceOfElementLocated(org.openqa.selenium.By.id("emisores-content")));

            try {
                ((JavascriptExecutor) driver).executeScript("document.getElementById('otros-content').style.display='block';");
                Thread.sleep(1000);
            } catch (Exception e) {
                System.out.println("Nota: No se pudo forzar display block, continuando...");
            }

            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);

            int totalEmisores = procesarContenedorLista(doc, "emisores-content");
            int totalOtros = procesarContenedorLista(doc, "otros-content");

            System.out.println("--- Listado finalizado. Total encontrados: " + (totalEmisores + totalOtros) + " ---");

        } catch (Exception e) {
            System.err.println("Error en listado principal: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) driver.quit();
        }
    }

    private int procesarContenedorLista(Document doc, String containerId) {
        Element contenedor = doc.getElementById(containerId);
        if (contenedor == null) {
            System.err.println("AVISO: No se encontró contenedor " + containerId);
            return 0;
        }

        int count = 0;
        Elements bloquesSector = contenedor.select(".emisores");
        for (Element bloque : bloquesSector) {
            String nombreSector = bloque.select(".emisores__title").text();
            Elements linksEmpresas = bloque.select("ul.emisores__list li a.emisores__link");

            for (Element link : linksEmpresas) {
                guardarEmisorInicial(nombreSector, link);
                count++;
            }
        }
        System.out.println("   -> Contenedor [" + containerId + "]: " + count + " registros.");
        return count;
    }

    private void guardarEmisorInicial(String sector, Element linkElement) {
        String nombreEmpresa = linkElement.text();
        String urlRelativa = linkElement.attr("href");
        String codigo = extraerCodigoDeUrl(urlRelativa);

        Emisor emisor = emisorRepository.findById(codigo).orElse(new Emisor());
        emisor.setCodigo(codigo);
        emisor.setNombre(nombreEmpresa);
        emisor.setSector(sector);

        if (!urlRelativa.startsWith("http")) {
            emisor.setUrlPerfil(BASE_URL + urlRelativa);
        } else {
            emisor.setUrlPerfil(urlRelativa);
        }

        emisorRepository.save(emisor);
    }

    // =================================================================================
    // FASE 2: DETALLES GENERALES
    // =================================================================================
    public ScrapingReporte procesarDetallesDeEmisoresGuardados() {
        ScrapingReporte reporte = new ScrapingReporte();
        List<Emisor> emisores = emisorRepository.findAll();
        System.out.println("--- Procesando Detalles de " + emisores.size() + " emisores ---");

        WebDriver driver = iniciarDriver();
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(60));
        Random random = new Random();

        for (int i = 0; i < emisores.size(); i++) {
            Emisor emisor = emisores.get(i);
            try {
                System.out.println(">>> [" + (i + 1) + "/" + emisores.size() + "] Analizando: " + emisor.getNombre());

                driver.get(emisor.getUrlPerfil());
                Thread.sleep(2000);

                String html = driver.getPageSource();
                Document doc = Jsoup.parse(html);
                Set<String> urlsProcesadas = new HashSet<>();

                procesarSeccion(driver, doc, emisor, urlsProcesadas, ".participantes-section.participante", "Cabecera", reporte);
                procesarSeccion(driver, doc, emisor, urlsProcesadas, ".participantes-section.p-memorias", "Memorias", reporte);
                procesarSeccion(driver, doc, emisor, urlsProcesadas, ".participantes-section.p-estados-mensuales", "Estados Mensuales", reporte);
                procesarSeccion(driver, doc, emisor, urlsProcesadas, ".participantes-section.p-emisiones", "Emisiones", reporte);

                emisor.setProcesado(true);
                emisor.setUpdateData(LocalDateTime.now());
                emisorRepository.save(emisor);

                reporte.setTotalProcesados(reporte.getTotalProcesados() + 1);
                Thread.sleep(1000 + random.nextInt(1000));

            } catch (WebDriverException e) {
                System.err.println("!!! CRASH DEL NAVEGADOR. Reiniciando...");
                try { driver.quit(); } catch (Exception ex) {}
                driver = iniciarDriver();
                i--;
                try { Thread.sleep(3000); } catch (Exception ex) {}
            } catch (Exception e) {
                System.err.println("Error genérico en " + emisor.getCodigo() + ": " + e.getMessage());
            }
        }

        if (driver != null) try { driver.quit(); } catch (Exception e) {}
        return reporte;
    }

    private void procesarSeccion(WebDriver driver, Document doc, Emisor emisor, Set<String> urlsProcesadas, String selector, String nombreSeccion, ScrapingReporte reporte) throws MalformedURLException {
        Elements seccion = doc.select(selector);
        if (seccion.isEmpty()) return;

        for (Element link : seccion.select("a[href]")) {
            String href = link.attr("abs:href");
            if (href != null && href.toLowerCase().endsWith(".pdf")) {
                if (urlsProcesadas.contains(href)) continue;
                urlsProcesadas.add(href);

                String pdfUrl = href.replace(" ", "%20");
                String nombreArchivo = Paths.get(new URL(pdfUrl).getPath()).getFileName().toString().replace("%20", "_");

                gestionarDescargaYAlmacenamiento(driver, pdfUrl, emisor, nombreArchivo, reporte);
            }
        }
    }

    // =================================================================================
    // FASE 3: SOLO EMISIONES (API REST)
    // =================================================================================
    public ScrapingReporte procesarSoloEmisionesApi() {
        ScrapingReporte reporte = new ScrapingReporte();
        List<Emisor> emisores = emisorRepository.findAll();
        System.out.println("--- Iniciando Scraping SOLO de APIs de Emisiones ---");

        WebDriver driver = iniciarDriver();
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(60));
        Random random = new Random();

        for (int i = 0; i < emisores.size(); i++) {
            Emisor emisor = emisores.get(i);
            try {
                System.out.println(">>> API Search [" + (i+1) + "]: " + emisor.getNombre());
                driver.get(emisor.getUrlPerfil());
                Thread.sleep(2000);

                Document doc = Jsoup.parse(driver.getPageSource());
                Elements seccion = doc.select(".participantes-section.p-emisiones");

                if (!seccion.isEmpty()) {
                    procesarTablasDeEmisiones(driver, seccion.first(), emisor, reporte);
                }

                emisor.setUpdateData(LocalDateTime.now());
                emisorRepository.save(emisor);
                reporte.setTotalProcesados(reporte.getTotalProcesados() + 1);

                Thread.sleep(500 + random.nextInt(1000));

            } catch (WebDriverException e) {
                System.err.println("!!! CRASH DEL NAVEGADOR. Reiniciando...");
                try { driver.quit(); } catch (Exception ex) {}
                driver = iniciarDriver();
                i--;
                try { Thread.sleep(3000); } catch (Exception ex) {}
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (driver != null) try { driver.quit(); } catch (Exception e) {}
        return reporte;
    }

    private void procesarTablasDeEmisiones(WebDriver driver, Element seccionEmisiones, Emisor emisor, ScrapingReporte reporte) {

        // 1. PROSPECTOS (.l-prospecto)
        Elements divProspectos = seccionEmisiones.select(".p-column-table.l-prospecto");
        if (!divProspectos.isEmpty()) {
            for (Element fila : divProspectos.select("table tbody tr")) {
                Elements celdas = fila.select("td");
                if (celdas.size() >= 1) {
                    String nombreEmision = celdas.get(0).text().trim().replace(" ", "_").replaceAll("[^a-zA-Z0-9_]", "");
                    for (Element link : fila.select("a[href]")) {
                        String href = link.attr("abs:href");
                        if (href != null && href.contains(API_URL_FRAGMENT)) {
                            String sufijo = href.endsWith("/CARAC") ? "Caracteristicas" : (href.endsWith("/P") ? "Prospecto" : "Documento");
                            String nombreArchivo = nombreEmision + "_" + sufijo + ".pdf";
                            gestionarDescargaYAlmacenamiento(driver, href, emisor, nombreArchivo, reporte);
                        }
                    }
                }
            }
        }

        // 2. CALIFICACIONES (.l-calif)
        Elements divCalif = seccionEmisiones.select(".p-column-table.l-calif");
        if (!divCalif.isEmpty()) {
            for (Element fila : divCalif.select("table tbody tr")) {
                Elements celdas = fila.select("td");
                if (celdas.size() >= 2) {
                    String entidad = celdas.get(0).text().trim();
                    String fechaONota = celdas.get(1).text().trim();
                    String nombreBase = "Calificacion_" + entidad + "_" + fechaONota;
                    nombreBase = nombreBase.replace(" ", "_").replaceAll("[^a-zA-Z0-9_\\-]", "");

                    for (Element link : fila.select("a[href]")) {
                        String href = link.attr("abs:href");
                        if (href != null && (href.contains(API_URL_FRAGMENT) || href.toLowerCase().endsWith(".pdf"))) {
                            String nombreArchivo = nombreBase + ".pdf";
                            gestionarDescargaYAlmacenamiento(driver, href, emisor, nombreArchivo, reporte);
                        }
                    }
                }
            }
        }
    }

    // =================================================================================
    // GESTIÓN DE ALMACENAMIENTO
    // =================================================================================

    private void gestionarDescargaYAlmacenamiento(WebDriver driver, String url, Emisor emisor, String nombreArchivo, ScrapingReporte reporte) {
        Path tempFile = null;
        try {
            // Usamos this.tempDir (inyectado)
            tempFile = descargarATemporal(driver, url, nombreArchivo);

            if (tempFile == null) {
                registrarEnBd(emisor, nombreArchivo, null, url, false, "ERROR", null);
                reporte.agregarError(emisor.getCodigo() + " - " + nombreArchivo);
                return;
            }

            String nuevoHash = calcularHashArchivo(tempFile);

            Optional<Archivo> ultimoOpt = archivoRepository
                    .findTopByEmisorAndNombreArchivoAndDescargadoExitosamenteTrueOrderByFechaIntentoDesc(emisor, nombreArchivo);

            if (ultimoOpt.isPresent()) {
                Archivo ultimo = ultimoOpt.get();
                if (nuevoHash.equals(ultimo.getHashSha256())) {
                    Files.deleteIfExists(tempFile);
                    System.out.println("   [=] Sin cambios: " + nombreArchivo);
                } else {
                    String nombreFisicoNuevo = generarNombreVersionado(nombreArchivo);
                    moverAFinal(tempFile, emisor.getCodigo(), nombreFisicoNuevo);
                    registrarEnBd(emisor, nombreArchivo, nombreFisicoNuevo, url, true, "ACTUALIZADO", nuevoHash);
                    reporte.agregarActualizado(emisor.getCodigo() + " - " + nombreArchivo);
                    System.out.println("   [^] ACTUALIZADO: " + nombreArchivo);
                }
            } else {
                moverAFinal(tempFile, emisor.getCodigo(), nombreArchivo);
                registrarEnBd(emisor, nombreArchivo, nombreArchivo, url, true, "NUEVO", nuevoHash);
                reporte.agregarNuevo(emisor.getCodigo() + " - " + nombreArchivo);
                System.out.println("   [+] NUEVO: " + nombreArchivo);
            }

        } catch (Exception e) {
            System.err.println("Error gestionando archivo " + nombreArchivo + ": " + e.getMessage());
            reporte.agregarError(emisor.getCodigo() + " - " + nombreArchivo);
            try { if (tempFile != null) Files.deleteIfExists(tempFile); } catch (Exception ex) {}
        }
    }

    private Path descargarATemporal(WebDriver driver, String url, String nombreArchivo) {
        try {
            byte[] bytes = descargarBytesConChrome(driver, url);
            if (bytes == null) {
                bytes = descargarBytesConJava(url);
            }

            if (bytes != null) {
                // Usamos this.tempDir
                Path tempPath = this.tempDir.resolve(nombreArchivo + "_" + System.currentTimeMillis() + ".tmp");
                try (FileOutputStream fos = new FileOutputStream(tempPath.toFile())) {
                    fos.write(bytes);
                }
                return tempPath;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void moverAFinal(Path tempFile, String codigoEmisor, String nombreFinal) throws Exception {
        // Usamos this.finalDir
        Path dirEmisor = this.finalDir.resolve(codigoEmisor);
        if (!Files.exists(dirEmisor)) Files.createDirectories(dirEmisor);
        Path destino = dirEmisor.resolve(nombreFinal);
        Files.move(tempFile, destino, StandardCopyOption.REPLACE_EXISTING);
    }

    private String generarNombreVersionado(String nombreOriginal) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        int dotIndex = nombreOriginal.lastIndexOf(".");
        if (dotIndex == -1) return nombreOriginal + "_v" + timestamp;
        return nombreOriginal.substring(0, dotIndex) + "_v" + timestamp + nombreOriginal.substring(dotIndex);
    }

    private String calcularHashArchivo(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new FileInputStream(path.toFile())) {
            byte[] byteArray = new byte[1024];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarEnBd(Emisor emisor, String nombreLogico, String nombreFisico, String url, boolean exito, String estado, String hash) {
        try {
            Archivo archivo = new Archivo();
            archivo.setEmisor(emisor);
            archivo.setNombreArchivo(nombreLogico);
            archivo.setNombreFisico(nombreFisico);
            archivo.setUrlOrigen(url);
            archivo.setDescargadoExitosamente(exito);
            archivo.setFechaIntento(LocalDateTime.now());
            archivo.setEstado(estado);
            archivo.setHashSha256(hash);
            archivoRepository.save(archivo);
        } catch (Exception e) {
            System.err.println("Error guardando BD: " + e.getMessage());
        }
    }

    // --- Métodos de Descarga (Bytes) ---

    private byte[] descargarBytesConChrome(WebDriver driver, String url) {
        try {
            String script = "var url = arguments[0]; var callback = arguments[1];" +
                    "fetch(url).then(r => { if(!r.ok) throw new Error(); return r.blob(); })" +
                    ".then(b => { var r = new FileReader(); r.readAsDataURL(b); r.onloadend = function(){ callback(r.result); } })" +
                    ".catch(e => callback(null));";
            Object res = ((JavascriptExecutor) driver).executeAsyncScript(script, url);
            if (res != null) {
                String b64 = res.toString();
                if (b64.contains(",")) b64 = b64.split(",")[1];
                return Base64.getDecoder().decode(b64);
            }
        } catch (Exception e) {}
        return null;
    }

    private byte[] descargarBytesConJava(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET().build();
            HttpResponse<byte[]> res = javaHttpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() == 200) return res.body();
        } catch (Exception e) {}
        return null;
    }

    // --- Configuración ---

    private void inicializarClienteJava() {
        try {
            TrustManager[] trustAll = new TrustManager[]{ new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};
            SSLContext ssl = SSLContext.getInstance("TLSv1.2");
            ssl.init(null, trustAll, new java.security.SecureRandom());
            this.javaHttpClient = HttpClient.newBuilder().sslContext(ssl)
                    .connectTimeout(Duration.ofSeconds(60))
                    .followRedirects(HttpClient.Redirect.ALWAYS).build();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private WebDriver iniciarDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();

        // --- OPCIONES OBLIGATORIAS PARA DOCKER ---
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox"); // Vital para Docker
        options.addArguments("--disable-dev-shm-usage"); // Vital para memoria compartida
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--ignore-certificate-errors");
        // -----------------------------------------

        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        WebDriver driver = new ChromeDriver(options);
        // ...
        return driver;
    }

    private String extraerCodigoDeUrl(String url) {
        try {
            if (url.contains("participante=")) return url.split("participante=")[1].split("&")[0];
        } catch (Exception e) { return "UNKNOWN-" + System.currentTimeMillis(); }
        return "UNKNOWN-" + System.currentTimeMillis();
    }
}