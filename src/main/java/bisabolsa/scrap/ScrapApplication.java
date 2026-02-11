package bisabolsa.scrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

@SpringBootApplication
public class ScrapApplication {

    public static void main(String[] args) {
        // --- FIX CRÍTICO PARA EL HANDSHAKE ---
        // Forzamos a Java a usar TLS 1.2, que es lo que soportan la mayoría de servidores legacy
        System.setProperty("https.protocols", "TLSv1.2");
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        // -------------------------------------

        SpringApplication app = new SpringApplication(ScrapApplication.class);
        // Opcional: Si el puerto 5051 te da problemas, puedes cambiarlo aquí o en properties
        app.setDefaultProperties(Collections.singletonMap("server.port", "5051"));
        app.run(args);
    }
}