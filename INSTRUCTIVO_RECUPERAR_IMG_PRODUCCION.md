# Maidav — Recuperar y descargar imágenes de producción

## Objetivo

Descargar a la computadora local todas las imágenes almacenadas actualmente en el servidor de producción de Maidav, sin modificar ni eliminar los archivos originales.

Las imágenes se encuentran en el contenedor Docker de la aplicación de producción, dentro de `/app/uploads`.

## 1. Datos del servidor y contenedores

**Servidor VPS**
- IP pública: `187.127.10.6`
- Usuario SSH: `root`
- Sistema operativo: Ubuntu 24.04.4 LTS
- Plataforma de despliegue: Dokploy

**Contenedores identificados**

| Servicio | Contenedor | IP interna |
|---|---|---|
| Aplicación Maidav producción | `vps-spring-boot-maidav-lixir2-app-1` | No verificada en este procedimiento |
| Base de datos Maidav producción | `vps-spring-boot-maidav-lixir2-db-1` | `172.20.0.2` |
| Base de datos Maidav testing | `vps-spring-boot-maidavtesting-wzcwxz-db-1` | `172.21.0.2` |

## 2. Conexión SSH desde Windows

Abrir PowerShell y conectarse al servidor:

```powershell
ssh root@187.127.10.6
```

Introducir la contraseña SSH cuando sea solicitada.

### Túnel SSH a PostgreSQL de producción

Para acceder a PostgreSQL de producción desde herramientas locales, se utilizó:

```powershell
ssh -L 5435:172.20.0.2:5432 root@187.127.10.6
```

Este túnel permite acceder al puerto local `5435`, que redirige al puerto `5432` de la base de datos de producción. La sesión SSH debe permanecer abierta mientras se utiliza el túnel.

**Nota:** este túnel es independiente de la recuperación de imágenes.

## 3. Identificar los contenedores Docker

Una vez conectado por SSH:

```bash
docker ps
```

Para consultar las IP internas de las bases de datos:

```bash
docker inspect -f '{{.Name}} {{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' \
  vps-spring-boot-maidav-lixir2-db-1 \
  vps-spring-boot-maidavtesting-wzcwxz-db-1
```

Resultado verificado:

```text
/vps-spring-boot-maidav-lixir2-db-1 172.20.0.2
/vps-spring-boot-maidavtesting-wzcwxz-db-1 172.21.0.2
```

## 4. Localizar las imágenes de producción

Se buscaron imágenes dentro del contenedor de la aplicación de producción:

```bash
docker exec vps-spring-boot-maidav-lixir2-app-1 sh -lc \
'find /app -type f \( -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.png" -o -iname "*.webp" -o -iname "*.gif" -o -iname "*.avif" \) 2>/dev/null | head -200'
```

Se encontraron archivos en estas ubicaciones:

- `/app/uploads/`
- `/app/uploads/products/`

Ejemplos de archivos encontrados:

```text
/app/uploads/1b17dc6c-249d-487c-901a-2c1d5c6a0db1-1000423900.png
/app/uploads/products/4b86c6c4-03d7-40e0-aa51-020414d0abbe-1000734395.jpg
/app/uploads/products/d69fee79-ecab-466d-8474-ed969012783c-olla paellera dinax.png
```

La búsqueda mostró hasta 200 resultados. Esto no representa necesariamente el total de imágenes existentes.

## 5. Crear un archivo comprimido con las imágenes

Ejecutar en el servidor Ubuntu:

```bash
docker exec vps-spring-boot-maidav-lixir2-app-1 \
  tar -czf /tmp/maidav-images.tar.gz -C /app uploads
```

Este comando genera un archivo comprimido con todo el directorio `/app/uploads` del contenedor, incluyendo sus subdirectorios.

La estructura interna esperada es:

```text
uploads/
├── imagenes-generales
└── products/
    ├── imagen-producto-1.jpg
    ├── imagen-producto-2.png
    └── ...
```

El comando no elimina ni modifica los archivos originales.

## 6. Copiar el archivo comprimido al servidor VPS

Ejecutar en Ubuntu:

```bash
docker cp vps-spring-boot-maidav-lixir2-app-1:/tmp/maidav-images.tar.gz /root/maidav-images.tar.gz
```

El archivo quedará en:

```text
/root/maidav-images.tar.gz
```

## 7. Descargar las imágenes a Windows

Salir de la sesión SSH si es necesario:

```bash
exit
```

Abrir una nueva ventana de PowerShell en Windows y ejecutar:

```powershell
scp root@187.127.10.6:/root/maidav-images.tar.gz "$env:USERPROFILE\Downloads\maidav-images.tar.gz"
```

Introducir la contraseña SSH cuando sea solicitada.

El archivo quedará en la carpeta Descargas del usuario de Windows:

```text
%USERPROFILE%\Downloads\maidav-images.tar.gz
```

## 8. Extraer las imágenes en Windows

Utilizar 7-Zip u otra herramienta compatible con archivos `.tar.gz`.

Extraer primero el archivo `.tar.gz` y luego el `.tar` generado, si la herramienta no realiza ambas operaciones automáticamente.

La carpeta resultante contendrá:

```text
uploads/
├── ...
└── products/
    ├── ...
```

Desde allí se pueden copiar las imágenes al proyecto local de Maidav, según la estructura de carpetas que utilice el entorno de desarrollo.

## 9. Verificaciones recomendadas

### Verificar el archivo en el servidor

```bash
ls -lh /root/maidav-images.tar.gz
```

### Verificar el contenido del archivo comprimido

```bash
tar -tzf /root/maidav-images.tar.gz | head -50
```

### Contar los archivos incluidos

```bash
tar -tzf /root/maidav-images.tar.gz | wc -l
```

### Verificar el tamaño en Windows

```powershell
Get-Item "$env:USERPROFILE\Downloads\maidav-images.tar.gz" |
    Select-Object FullName, Length, LastWriteTime
```

### Verificar la integridad del archivo

En Ubuntu:

```bash
sha256sum /root/maidav-images.tar.gz
```

En PowerShell, después de descargarlo:

```powershell
Get-FileHash "$env:USERPROFILE\Downloads\maidav-images.tar.gz" -Algorithm SHA256
```

Los valores SHA-256 deben coincidir para confirmar que el archivo descargado es idéntico al del servidor.

## 10. Limpieza opcional

Después de comprobar que el archivo se descargó correctamente y que las imágenes se extrajeron, se pueden eliminar los archivos temporales creados durante el procedimiento.

En el servidor Ubuntu:

```bash
rm -f /root/maidav-images.tar.gz
```

Dentro del contenedor de producción:

```bash
docker exec vps-spring-boot-maidav-lixir2-app-1 \
  rm -f /tmp/maidav-images.tar.gz
```

**Precaución:** estos comandos eliminan únicamente los archivos comprimidos temporales indicados. No ejecutarlos hasta verificar que la copia local está completa.

## 11. Consideraciones importantes

- El procedimiento trabaja con el contenedor de la aplicación de producción.
- Los archivos originales ubicados en `/app/uploads` no se modifican durante la creación del archivo comprimido.
- El archivo incluye todo el directorio `uploads`, no solamente los primeros 200 archivos mostrados por la búsqueda.
- Si las imágenes están almacenadas en volúmenes externos, fuera de `/app/uploads` o en otro servicio, deberán recuperarse por separado.
- Antes de copiar las imágenes al entorno local, conviene conservar una copia de seguridad de las imágenes existentes para evitar sobrescrituras.
- No se debe restaurar ni sobrescribir la base de datos para recuperar imágenes que ya existen como archivos.
- Las operaciones de creación, copia y descarga descritas aquí no requieren reiniciar la aplicación ni detener el servicio de producción.

## Estado del procedimiento

**Confirmado:** se encontraron imágenes en `/app/uploads/` y `/app/uploads/products/` dentro del contenedor de la aplicación de producción.

**Procedimiento documentado:** compresión, copia al VPS y descarga mediante SCP a Windows.

**Pendiente de verificación:** confirmar que el archivo comprimido se generó correctamente, que se descargó a Windows y que todas las imágenes se extrajeron sin errores.
