# 1. Imagen base Java 21 (Ubuntu Jammy)
FROM eclipse-temurin:21-jdk-jammy

# 2. Evitar preguntas durante la instalación
ENV DEBIAN_FRONTEND=noninteractive

# 3. Instalar herramientas básicas
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    unzip \
    gnupg \
    --no-install-recommends

# 4. Descargar e Instalar Google Chrome Stable
# ESTRATEGIA ROBUSTA: Usar 'apt-get install ./archivo.deb' resuelve las dependencias automáticamente (libgbm, nss, etc.)
RUN wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get install -y ./google-chrome-stable_current_amd64.deb \
    && rm google-chrome-stable_current_amd64.deb \
    && rm -rf /var/lib/apt/lists/*

# 5. Verificar instalación (Esto fallará aquí mismo si faltan librerías, ahorrándote tiempo)
RUN google-chrome --version

# 6. Configurar directorios
WORKDIR /app

# 7. Carpetas de descarga con permisos totales
RUN mkdir -p /app/data/temp_downloads && \
    mkdir -p /app/data/downloads && \
    chmod -R 777 /app/data

# 8. Copiar el JAR
COPY target/Scrap-0.0.1-SNAPSHOT.jar app.jar

# 9. Variables de entorno para Selenium (Opcional pero recomendado)
ENV SE_OFFLINE=false

# 10. Ejecutar
EXPOSE 5051
ENTRYPOINT ["java", "-jar", "app.jar"]