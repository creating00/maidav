# Instructivo: traer base y uploads de Dockploy a local

Este instructivo deja documentado el proceso real que funcionó para bajar la base de datos y los archivos de `uploads` desde el servidor/Dockploy hacia la máquina local.

## Datos reales usados en este proyecto

- Servidor: `187.127.10.6`
- Contenedor app producción: `vps-spring-boot-maidav-lixir2-app-1`
- Contenedor db producción: `vps-spring-boot-maidav-lixir2-db-1`
- Dump encontrado: `/tmp/maidav-db-sync/prod-refresh-20260410-051237.dump`

## Configuración local tomada del proyecto

Archivo: [application-local.yml](C:\workspace\Proyectos FullStacks\Maidav Confort\maidav\src\main\resources\application-local.yml)

- Host local: `localhost`
- Puerto local: `5432`
- Base local: `sales_db`
- Usuario local: `sales`
- Password local: `sales123`

## 1. Identificar contenedores en el servidor

Entrar al VPS y ejecutar:

```bash
docker ps
```

En este proyecto devolvió:

```bash
vps-spring-boot-maidav-lixir2-app-1
vps-spring-boot-maidav-lixir2-db-1
```

## 2. Verificar dumps existentes dentro del contenedor de base

Entrar al contenedor de base:

```bash
docker exec -it vps-spring-boot-maidav-lixir2-db-1 bash
```

Buscar dumps:

```bash
find /tmp/maidav-db-sync -maxdepth 1 -type f
```

Resultado real:

```bash
/tmp/maidav-db-sync/testing-pre-refresh-20260410-051237.dump
/tmp/maidav-db-sync/prod-refresh-20260410-051237.dump
```

Salir del contenedor:

```bash
exit
```

## 3. Copiar el dump desde el contenedor al host del servidor

Ejecutar en el host del servidor, no dentro del contenedor:

```bash
docker cp vps-spring-boot-maidav-lixir2-db-1:/tmp/maidav-db-sync/prod-refresh-20260410-051237.dump /tmp/prod-refresh-20260410-051237.dump
```

Verificar:

```bash
ls -lah /tmp/prod-refresh-20260410-051237.dump
```

## 4. Bajar el dump a Windows

Ejecutar en PowerShell local:

```powershell
scp root@187.127.10.6:/tmp/prod-refresh-20260410-051237.dump C:\Users\jonat\Downloads\
```

El archivo queda en:

```text
C:\Users\jonat\Downloads\prod-refresh-20260410-051237.dump
```

## 5. Verificar herramientas PostgreSQL instaladas en Windows

Buscar `pg_restore.exe`:

```powershell
Get-ChildItem "C:\Program Files\PostgreSQL" -Recurse -Filter pg_restore.exe -ErrorAction SilentlyContinue
```

Resultado real:

```text
C:\Program Files\PostgreSQL\15\bin\pg_restore.exe
C:\Program Files\PostgreSQL\18\bin\pg_restore.exe
```

## 6. Convertir el dump a SQL con PostgreSQL 18

Esto fue necesario porque el dump era más nuevo que `pg_restore` 15.

```powershell
& "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe" -f "C:\Users\jonat\Downloads\prod-refresh-20260410-051237.sql" "C:\Users\jonat\Downloads\prod-refresh-20260410-051237.dump"
```

## 7. Generar un SQL compatible con el server local

Se quitó la línea incompatible:

```powershell
(Get-Content "C:\Users\jonat\Downloads\prod-refresh-20260410-051237.sql") |
Where-Object { $_ -notmatch '^SET transaction_timeout = 0;' } |
Set-Content "C:\Users\jonat\Downloads\prod-refresh-20260410-051237.fixed.sql"
```

## 8. Backup opcional de la base local antes de restaurar

```powershell
$env:PGPASSWORD="sales123"
& "C:\Program Files\PostgreSQL\15\bin\pg_dump.exe" -h localhost -p 5432 -U sales -d sales_db -Fc -f "C:\Users\jonat\Downloads\sales_db_backup_local.dump"
```

## 9. Limpiar el esquema local

Esto pisa la base local actual.

```powershell
$env:PGPASSWORD="sales123"
& "C:\Program Files\PostgreSQL\15\bin\psql.exe" -h localhost -p 5432 -U sales -d sales_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

## 10. Restaurar el SQL en la base local

```powershell
$env:PGPASSWORD="sales123"
& "C:\Program Files\PostgreSQL\15\bin\psql.exe" -h localhost -p 5432 -U sales -d sales_db -f "C:\Users\jonat\Downloads\prod-refresh-20260410-051237.fixed.sql"
```

## 11. Copiar uploads desde la app de producción al host del servidor

Ejecutar en el host del servidor:

```bash
docker cp vps-spring-boot-maidav-lixir2-app-1:/app/uploads /tmp/maidav-uploads
```

Verificar:

```bash
ls -lah /tmp/maidav-uploads
find /tmp/maidav-uploads -type f | head
```

## 12. Bajar uploads a Windows

Ejecutar en PowerShell local:

```powershell
scp -r root@187.127.10.6:/tmp/maidav-uploads C:\Users\jonat\Downloads\
```

Queda en:

```text
C:\Users\jonat\Downloads\maidav-uploads
```

## 13. Copiar uploads al proyecto local

Copiar el contenido descargado dentro de:

```text
C:\workspace\Proyectos FullStacks\Maidav Confort\maidav\uploads
```

Si querés reemplazar el contenido actual, primero hacé backup de la carpeta `uploads` local.

## 14. Abrir la base en pgAdmin

Usar esta conexión:

- Host: `localhost`
- Port: `5432`
- Database: `sales_db`
- Username: `sales`
- Password: `sales123`

## Problemas reales encontrados y solución

### Error: `pg_restore` no reconocido

Solución: usar ruta completa:

```powershell
& "C:\Program Files\PostgreSQL\15\bin\pg_restore.exe"
```

### Error: `version no soportada (1.15) en el encabezado del archivo`

Solución: usar `pg_restore` 18 para convertir a SQL.

### Error: `SET transaction_timeout = 0`

Solución: quitar esa línea del SQL antes de restaurar con PostgreSQL 15.

### Error de duplicados / tablas existentes

Solución: vaciar el esquema local antes de restaurar:

```powershell
DROP SCHEMA public CASCADE; CREATE SCHEMA public;
```

### Error con `scp root@srv1550125`

Solución: usar la IP pública real del servidor:

```powershell
scp root@187.127.10.6:...
```

## Checklist rápido para una próxima vez

1. `docker ps`
2. ubicar contenedor app y db de producción
3. localizar dump con `find /tmp/maidav-db-sync`
4. `docker cp` del dump al host
5. `scp` del dump a Windows
6. `pg_restore 18` a `.sql`
7. quitar `transaction_timeout`
8. backup local opcional
9. `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`
10. `psql 15 -f ...fixed.sql`
11. `docker cp` de `/app/uploads`
12. `scp -r` de uploads a Windows
13. copiar uploads al proyecto local
14. abrir `sales_db` en pgAdmin
