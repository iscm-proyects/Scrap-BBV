package bisabolsa.scrap.service;

import bisabolsa.scrap.model.AsfiHechoRelevante;
import bisabolsa.scrap.repository.AsfiRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AsfiScraperService {

    @Autowired
    private AsfiRepository asfiRepository;

    @Value("${scraping.paths.final:downloads}")
    private String finalPathString;

    private Path asfiDir;
    private HttpClient javaHttpClient;

    private final String BASE_URL = "https://appweb2.asfi.gob.bo/PaginasPublicas2/VistaHechosRelevantes/ListaPublicacionHechoRelevante.aspx";
    // Base para construir la URL absoluta del visor
    private final String BASE_URL_DIR = "https://appweb2.asfi.gob.bo/PaginasPublicas2/VistaHechosRelevantes/";

    @PostConstruct
    public void init() {
        try {
            this.asfiDir = Paths.get(finalPathString, "ASFI_HechosRelevantes");
            if (!Files.exists(this.asfiDir)) Files.createDirectories(this.asfiDir);
            inicializarClienteJava();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String iniciarScrapingAsfi(int gestion) {
        WebDriver driver = null;
        int nuevos = 0;
        int errores = 0;

        try {
            System.out.println("--- Iniciando Scraping ASFI (Java + Cookies) ---");
            driver = iniciarDriver();

            String urlDestino = BASE_URL + "?Gestion=" + gestion;
            System.out.println("Navegando a: " + urlDestino);
            driver.get(urlDestino);

            // Esperar carga inicial
            try { Thread.sleep(3000); } catch (InterruptedException e) {}

            String html = driver.getPageSource();
            Document doc = Jsoup.parse(html);

            // Seleccionar filas de datos
            Elements filas = doc.select("tr[id^='ASPxGridPublicaciones_DXDataRow']");
            System.out.println("Filas encontradas: " + filas.size());

            for (Element fila : filas) {
                try {
                    Elements celdas = fila.select("td");

                    if (celdas.size() >= 2) {
                        // Columna 1 tiene el texto y el link
                        Element celdaInfo = celdas.get(1);
                        Element link = celdaInfo.select("a.linkpublicacion").first();

                        if (link != null) {
                            String textoCompleto = link.text().trim();
                            String hrefRelativo = link.attr("href"); // Ej: VisorDocumentos.aspx?variable1=...

                            String token = extraerTokenDeUrl(hrefRelativo);

                            if (token != null) {
                                String fecha = extraerFecha(textoCompleto);
                                String entidad = "ASFI";
                                if (textoCompleto.contains("ASFI")) entidad = "ASFI"; // Ajustar lógica según texto real

                                System.out.println("   -> Procesando: " + textoCompleto);

                                if (asfiRepository.existsByTokenDescarga(token)) {
                                    System.out.println("      [=] Ya existe.");
                                    continue;
                                }

                                // Construir URL Absoluta
                                String urlDescarga = BASE_URL_DIR + hrefRelativo;
                                String nombreArchivo = generarNombreArchivo(entidad, fecha, token);

                                // DESCARGA ROBUSTA: Usar Java HttpClient inyectando las cookies de Selenium
                                boolean exito = descargarConJavaYCookies(driver, urlDescarga, nombreArchivo);

                                AsfiHechoRelevante hecho = new AsfiHechoRelevante();
                                hecho.setFechaOrigen(fecha);
                                hecho.setEntidad(entidad);
                                hecho.setReferencia(textoCompleto);
                                hecho.setTokenDescarga(token);
                                hecho.setNombreArchivo(nombreArchivo);
                                hecho.setDescargado(exito);
                                hecho.setFechaRegistro(LocalDateTime.now());

                                asfiRepository.save(hecho);

                                if (exito) {
                                    System.out.println("      [+] Descargado: " + nombreArchivo);
                                    nuevos++;
                                } else {
                                    System.err.println("      [x] Falló descarga.");
                                    errores++;
                                }
                                Thread.sleep(1000);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error fila: " + e.getMessage());
                    errores++;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        } finally {
            if (driver != null) driver.quit();
        }

        return String.format("Finalizado. Nuevos: %d, Errores: %d", nuevos, errores);
    }

    /**
     * Método CRÍTICO: Obtiene las cookies de Selenium y las usa en Java HttpClient.
     * Esto permite descargar archivos protegidos por sesión ASP.NET.
     */
    private boolean descargarConJavaYCookies(WebDriver driver, String url, String nombreArchivo) {
        try {
            // 1. Obtener cookies del navegador
            Set<Cookie> seleniumCookies = driver.manage().getCookies();
            StringBuilder cookieString = new StringBuilder();
            for (Cookie cookie : seleniumCookies) {
                cookieString.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
            }

            // 2. Crear petición HTTP con esas cookies
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Cookie", cookieString.toString())
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            // 3. Descargar
            HttpResponse<InputStream> response = javaHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                // Validar que sea un PDF y no una página de error HTML
                String contentType = response.headers().firstValue("Content-Type").orElse("");

                // Opcional: Si el servidor devuelve HTML en vez de PDF, es un error
                // if (contentType.contains("text/html")) return false;

                Path destino = this.asfiDir.resolve(nombreArchivo);
                try (InputStream in = response.body()) {
                    Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } else {
                System.err.println("      [HTTP Error] Status: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("      [Java Error] " + e.getMessage());
        }
        return false;
    }

    private String extraerTokenDeUrl(String href) {
        if (href != null && href.contains("variable1=")) {
            return href.split("variable1=")[1];
        }
        return null;
    }

    private String extraerFecha(String texto) {
        Matcher m = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(texto);
        if (m.find()) return m.group(1);
        return "SinFecha";
    }

    private String generarNombreArchivo(String entidad, String fecha, String token) {
        String fechaClean = fecha.replace("/", "-");
        String tokenCorto = token.length() > 8 ? token.substring(0, 8) : token;
        return "ASFI_" + entidad + "_" + fechaClean + "_" + tokenCorto + ".pdf";
    }

    private void inicializarClienteJava() {
        try {
            TrustManager[] trustAll = new TrustManager[]{ new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};
            SSLContext ssl = SSLContext.getInstance("TLSv1.2");
            ssl.init(null, trustAll, new SecureRandom());
            this.javaHttpClient = HttpClient.newBuilder().sslContext(ssl)
                    .connectTimeout(Duration.ofSeconds(60))
                    .followRedirects(HttpClient.Redirect.ALWAYS).build();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private WebDriver iniciarDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        return driver;
    }
}