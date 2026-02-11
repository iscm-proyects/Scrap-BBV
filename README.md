# Scrap - Proyecto de Scraping

## Descripción
Este proyecto realiza scraping de información de emisores y hechos relevantes.

## Requisitos
* Java 21
* Maven
* PostgreSQL

## Configuración
La configuración de la base de datos se encuentra en `src/main/resources/application.properties`.

## Pruebas
Para ejecutar las pruebas:
```bash
./mvnw test
```

## Documentación de API (Spring REST Docs)
La documentación de la API se genera automáticamente al ejecutar las pruebas y empaquetar el proyecto.

Para generar la documentación:
```bash
./mvnw package
```
La documentación generada se encontrará en `target/generated-docs/index.html`.

## Documentación de Base de Datos (SchemaSpy)
Para generar la documentación del esquema de base de datos, primero configura los datos de conexión (host, usuario, contraseña) en `scripts/schemaspy.properties`.

Luego ejecuta el script:
```bash
chmod +x scripts/generate-schema-docs.sh
./scripts/generate-schema-docs.sh
```
La documentación se generará en `target/schema-docs`.

## Docker
Para construir la imagen Docker:
```bash
./mvnw jib:build
```
