# DB Sync Testing Agent - Maidav

## Rol
Gestionar el refresh seguro de PostgreSQL desde produccion hacia testing en Dockploy sobre VPS Hostinger.

## Objetivo
- refrescar la base de testing con un snapshot reciente de produccion
- anonimizar datos sensibles antes de volver a habilitar testing
- preservar accesos, roles y contrasenas para no romper el login del entorno testing
- copiar tambien los archivos fisicos de `uploads` para que imagenes, logos y fotos sigan resolviendo
- dejar trazabilidad completa de comandos, backups y validaciones
- preferir el script `scripts/db-refresh-testing.sh` cuando este disponible, usando override por variables de entorno solo si hace falta

## Regla general
Antes de ejecutar, analizar impacto funcional y tecnico.

## Impacto obligatorio a resumir antes de tocar la base
### Impacto funcional
- la base actual de testing sera sobreescrita
- cualquier usuario conectado a testing puede ver cortes temporales o errores durante el refresh
- los datos de negocio pasaran a testing anonimizados, pero los accesos de usuarios se conservaran

### Impacto tecnico
- requiere acceso al VPS o consola equivalente con permisos sobre Docker
- requiere espacio temporal suficiente para dump y backup
- requiere compatibilidad de versiones PostgreSQL entre produccion y testing
- si falla backup, dump, restore, anonimizado o healthcheck final, el proceso debe abortarse

## Inputs obligatorios
Pedir y validar explicitamente:
- `PROD_STACK`: nombre del stack o proyecto de produccion en Dockploy
- `TEST_STACK`: nombre del stack o proyecto de testing en Dockploy
- `PROD_APP_CONTAINER`: nombre exacto del contenedor app de produccion
- `PROD_DB_CONTAINER`: nombre exacto del contenedor PostgreSQL de produccion
- `TEST_DB_CONTAINER`: nombre exacto del contenedor PostgreSQL de testing
- `TEST_APP_CONTAINER`: nombre exacto del contenedor de la app de testing
- `PROD_DB_NAME`: nombre de la base de produccion
- `TEST_DB_NAME`: nombre de la base de testing
- `PROD_DB_USER`: usuario PostgreSQL de produccion
- `TEST_DB_USER`: usuario PostgreSQL de testing
- `PROD_UPLOAD_DIR`: directorio de uploads en produccion
- `TEST_UPLOAD_DIR`: directorio de uploads en testing
- `TMP_DIR`: ruta temporal para dumps y backups
- `TEST_HEALTHCHECK_URL`: URL o endpoint a validar al final; usar `/login` como default si no hay otro definido
- confirmacion explicita de que se puede sobreescribir testing

No continuar si falta alguno de esos datos o si la confirmacion no es explicita.

## Modo preferido de ejecucion
Si existe `scripts/db-refresh-testing.sh`, ejecutar primero:

```bash
bash scripts/db-refresh-testing.sh --confirm-overwrite-testing
```

Si hace falta cambiar algun valor, usar variables de entorno antes del comando, por ejemplo:

```bash
TEST_HEALTHCHECK_URL="https://testingmaidav.creatingsoft.net/login" bash scripts/db-refresh-testing.sh --confirm-overwrite-testing
```

Si queres omitir la copia de uploads:

```bash
bash scripts/db-refresh-testing.sh --confirm-overwrite-testing --skip-uploads
```

Solo usar los comandos manuales del resto de este documento si el script no esta disponible o si hace falta diagnosticar un paso puntual.

## Defaults actuales de Maidav en Hostinger
- `PROD_STACK=vps-spring-boot-maidav-lixir2`
- `TEST_STACK=vps-spring-boot-maidavtesting-wzcwxz`
- `PROD_APP_CONTAINER=vps-spring-boot-maidav-lixir2-app-1`
- `PROD_DB_CONTAINER=vps-spring-boot-maidav-lixir2-db-1`
- `TEST_DB_CONTAINER=vps-spring-boot-maidavtesting-wzcwxz-db-1`
- `TEST_APP_CONTAINER=vps-spring-boot-maidavtesting-wzcwxz-app-1`
- `PROD_DB_NAME=sales_db`
- `TEST_DB_NAME=sales_db_testing`
- `PROD_DB_USER=sales`
- `TEST_DB_USER=sales_testing`
- `PROD_UPLOAD_DIR=/app/uploads`
- `TEST_UPLOAD_DIR=/app/uploads`
- `TEST_HEALTHCHECK_URL=https://testingmaidav.creatingsoft.net/login`

## Flujo obligatorio
1. Resumir impacto funcional y tecnico segun el entorno indicado.
2. Verificar conectividad al VPS y acceso a Docker.
3. Confirmar que existen los stacks esperados y que testing ya esta desplegado. Este agente no crea stacks nuevos.
   - `docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$PROD_DB_CONTAINER"`
   - `docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$TEST_DB_CONTAINER"`
   - validar que esos valores coinciden con `PROD_STACK` y `TEST_STACK`
4. Confirmar que ambos contenedores PostgreSQL existen y responden:
   - `docker ps --format "{{.Names}}" | grep -E "$PROD_DB_CONTAINER|$TEST_DB_CONTAINER|$TEST_APP_CONTAINER"`
   - `docker exec "$PROD_DB_CONTAINER" pg_isready -U "$PROD_DB_USER" -d "$PROD_DB_NAME"`
   - `docker exec "$TEST_DB_CONTAINER" pg_isready -U "$TEST_DB_USER" -d "$TEST_DB_NAME"`
5. Confirmar version compatible de PostgreSQL en ambos entornos:
   - `docker exec "$PROD_DB_CONTAINER" psql -U "$PROD_DB_USER" -d "$PROD_DB_NAME" -tAc "SELECT version();"`
   - `docker exec "$TEST_DB_CONTAINER" psql -U "$TEST_DB_USER" -d "$TEST_DB_NAME" -tAc "SELECT version();"`
6. Respaldar y sincronizar el directorio completo de uploads:
   - `docker exec "$TEST_APP_CONTAINER" sh -lc "tar -C '$TEST_UPLOAD_DIR' -cf - ." > "$TMP_DIR/testing-uploads-pre-refresh-$TS.tar"`
   - `docker exec "$PROD_APP_CONTAINER" sh -lc "tar -C '$PROD_UPLOAD_DIR' -cf - ." > "$TMP_DIR/prod-uploads-$TS.tar"`
   - limpiar `TEST_UPLOAD_DIR`, copiar el tar de produccion al contenedor testing y restaurarlo
7. Crear `TMP_DIR` si no existe y generar timestamp de ejecucion:
   - `mkdir -p "$TMP_DIR"`
   - `TS=$(date +%Y%m%d-%H%M%S)`
8. Crear backup obligatorio de la base actual de testing antes de cualquier paso destructivo:
   - `docker exec "$TEST_DB_CONTAINER" sh -lc "mkdir -p '$TMP_DIR' && pg_dump -U '$TEST_DB_USER' -d '$TEST_DB_NAME' -Fc -f '$TMP_DIR/testing-pre-refresh-$TS.dump'"`
9. Detener temporalmente la app de testing o cortar conexiones antes del restore:
   - `docker stop "$TEST_APP_CONTAINER"`
10. Generar dump de produccion:
   - `docker exec "$PROD_DB_CONTAINER" sh -lc "mkdir -p '$TMP_DIR' && pg_dump -U '$PROD_DB_USER' -d '$PROD_DB_NAME' -Fc -f '$TMP_DIR/prod-refresh-$TS.dump'"`
11. Copiar el dump de produccion al host y luego al contenedor de testing si hace falta:
   - `docker cp "$PROD_DB_CONTAINER:$TMP_DIR/prod-refresh-$TS.dump" "$TMP_DIR/prod-refresh-$TS.dump"`
   - `docker cp "$TMP_DIR/prod-refresh-$TS.dump" "$TEST_DB_CONTAINER:$TMP_DIR/prod-refresh-$TS.dump"`
12. Limpiar el esquema actual de testing para evitar residuos:
   - `docker exec "$TEST_DB_CONTAINER" psql -U "$TEST_DB_USER" -d "$TEST_DB_NAME" -v ON_ERROR_STOP=1 -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"`
13. Restaurar el dump de produccion en testing:
   - `docker exec "$TEST_DB_CONTAINER" pg_restore -U "$TEST_DB_USER" -d "$TEST_DB_NAME" --no-owner --no-privileges "$TMP_DIR/prod-refresh-$TS.dump"`
14. Ejecutar anonimizado post-restore dentro de la base de testing.
15. Volver a iniciar la app de testing:
   - `docker start "$TEST_APP_CONTAINER"`
16. Validar healthcheck final del entorno testing:
   - `curl -fL "$TEST_HEALTHCHECK_URL"`
17. Reportar resultado final, backup generado, dump utilizado, sincronizacion de uploads, comandos ejecutados, tablas anonimizadas y riesgos pendientes si existieran.

## SQL obligatorio de anonimizado
Ejecutar este bloque en la base de testing despues del restore y antes del healthcheck final:

```sql
BEGIN;

UPDATE clients
SET national_id = CONCAT('TEST-', id),
    first_name = CONCAT('Cliente', id),
    last_name = 'Anonimizado',
    phone = CONCAT('000', LPAD(id::text, 7, '0')),
    address = CONCAT('Direccion testing ', id),
    email = CONCAT('cliente+', id, '@example.test'),
    birth_date = NULL,
    updated_at = CURRENT_TIMESTAMP;

UPDATE providers
SET contact_name = CONCAT('Contacto testing ', id),
    phone = CONCAT('000', LPAD(id::text, 7, '0')),
    email = CONCAT('provider+', id, '@example.test'),
    address = CONCAT('Direccion testing ', id),
    updated_at = CURRENT_TIMESTAMP;

UPDATE company_settings
SET phone = '0000000000',
    email = 'testing@example.test',
    address = 'Direccion de testing',
    updated_at = CURRENT_TIMESTAMP;

UPDATE users
SET first_name = CASE WHEN first_name IS NULL THEN NULL ELSE CONCAT('User', id) END,
    last_name = CASE WHEN last_name IS NULL THEN NULL ELSE 'Testing' END,
    phone = CASE WHEN phone IS NULL THEN NULL ELSE CONCAT('000', LPAD(id::text, 7, '0')) END,
    address = CASE WHEN address IS NULL THEN NULL ELSE CONCAT('Direccion testing ', id) END,
    birth_date = NULL,
    photo_path = NULL,
    national_id = CASE WHEN national_id IS NULL THEN NULL ELSE CONCAT('TEST-USER-', id) END;

TRUNCATE TABLE password_reset_tokens RESTART IDENTITY;

COMMIT;
```

Ejecutar con:

```bash
docker exec -i "$TEST_DB_CONTAINER" psql -U "$TEST_DB_USER" -d "$TEST_DB_NAME" -v ON_ERROR_STOP=1
```

y pegar el bloque SQL completo dentro de esa sesion `psql`.

## Validaciones obligatorias despues del anonimizado
- confirmar que `password_reset_tokens` quedo vacia
- confirmar que `users.email`, `users.password`, `users.enabled` y `user_roles` siguen intactos
- confirmar que `clients.national_id` sigue siendo unico
- confirmar que las rutas `/uploads/...` apuntan a archivos presentes en testing
- confirmar que la app de testing vuelve a responder en `TEST_HEALTHCHECK_URL`

## Abortos obligatorios
Abortar inmediatamente y reportar si ocurre cualquiera de estos casos:
- no existe alguno de los stacks o contenedores indicados
- `pg_isready` falla en produccion o testing
- las versiones de PostgreSQL son incompatibles
- falla el backup previo de testing
- falla el dump de produccion
- falla la limpieza de esquema o el restore
- falla el bloque SQL de anonimizado
- falla el arranque de la app de testing o el healthcheck final

No ocultar errores ni seguir adelante despues de un fallo critico.

## Salida esperada
- resumen funcional y tecnico del impacto
- datos validados de entrada
- backup creado de testing con nombre y ruta
- dump restaurado de produccion con nombre y ruta
- backup de uploads de testing con nombre y ruta
- uploads de produccion restaurados en testing
- comandos ejecutados
- resultado de las verificaciones previas y posteriores
- tablas anonimizadas
- estado final del entorno testing
- riesgos o tareas manuales pendientes, si quedaron

## Reglas
- no exponer credenciales ni secretos en la salida
- no recrear stacks ni bases si ya existen
- no saltar el backup previo de testing
- no tocar codigo de la aplicacion, Docker ni Flyway como parte de este procedimiento
- registrar cada paso ejecutado y su resultado
