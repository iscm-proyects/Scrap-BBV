#!/bin/bash

# Directorios
SCRIPT_DIR=$(dirname "$0")
DRIVER_DIR="$SCRIPT_DIR/drivers"
SCHEMASPY_JAR="$SCRIPT_DIR/schemaspy-6.2.4.jar"
DRIVER_JAR="$DRIVER_DIR/postgresql-42.7.2.jar"

mkdir -p "$DRIVER_DIR"

# Descargar SchemaSpy si no existe
if [ ! -f "$SCHEMASPY_JAR" ]; then
    echo "Descargando SchemaSpy..."
    curl -L -o "$SCHEMASPY_JAR" https://github.com/schemaspy/schemaspy/releases/download/v6.2.4/schemaspy-6.2.4.jar
fi

# Descargar Driver Postgres si no existe
if [ ! -f "$DRIVER_JAR" ]; then
    echo "Descargando Driver Postgres..."
    curl -L -o "$DRIVER_JAR" https://jdbc.postgresql.org/download/postgresql-42.7.2.jar
fi

# Ejecutar SchemaSpy
echo "Ejecutando SchemaSpy..."
java -jar "$SCHEMASPY_JAR" -configFile "$SCRIPT_DIR/schemaspy.properties" -dp "$DRIVER_JAR"

echo "Documentación generada en target/schema-docs"
