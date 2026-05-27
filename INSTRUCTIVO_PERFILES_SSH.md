# Instructivo: perfiles local, testing y prod con tunel SSH

Este proyecto ahora soporta tres perfiles para correr la app localmente:

- `local`: base local restaurada
- `testing`: base de testing por tunel SSH
- `prod`: base de produccion por tunel SSH

## Regla importante de Flyway

Flyway siempre corre sobre la base a la que esta conectada la app.

- `local`: `Flyway` activo por defecto
- `testing`: `Flyway` activo por defecto
- `prod`: `Flyway` apagado por defecto en tu PC

En `prod`, si alguna vez quieres habilitarlo manualmente:

```powershell
$env:MAIDAV_PROD_FLYWAY_ENABLED="true"
```

## 1. Perfil local

Usa la base restaurada local:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
mvn org.springframework.boot:spring-boot-maven-plugin:run
```

Valores por defecto:

- host: `127.0.0.1`
- puerto: `5432`
- base: `sales_db`
- usuario: `sales`
- password: `sales123`

Overrides opcionales:

```powershell
$env:MAIDAV_LOCAL_DB_PORT="5432"
$env:MAIDAV_LOCAL_DB_NAME="sales_db"
$env:MAIDAV_LOCAL_DB_USERNAME="sales"
$env:MAIDAV_LOCAL_DB_PASSWORD="sales123"
```

## 2. Perfil testing

Levanta primero el tunel SSH a testing.

Datos reales detectados en este proyecto:

- servidor: `187.127.10.6`
- contenedor db testing: `vps-spring-boot-maidavtesting-wzcwxz-db-1`
- IP interna testing: `172.21.0.2`
- base testing: `sales_db_testing`
- usuario testing: `sales_testing`

```powershell
ssh -L 5434:172.21.0.2:5432 root@187.127.10.6
```

Verifica el tunel:

```powershell
Test-NetConnection 127.0.0.1 -Port 5434
```

Opcional: valida credenciales antes de levantar Spring:

```powershell
$env:PGPASSWORD="TU_PASSWORD_REAL_DE_TESTING"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h 127.0.0.1 -p 5434 -U sales_testing -d sales_db_testing -c "select current_user, current_database();"
```

Luego corre la app:

```powershell
$env:SPRING_PROFILES_ACTIVE="testing"
$env:MAIDAV_TESTING_DB_PORT="5434"
$env:MAIDAV_TESTING_DB_NAME="sales_db_testing"
$env:MAIDAV_TESTING_DB_USERNAME="sales_testing"
$env:MAIDAV_TESTING_DB_PASSWORD="salestesting"
mvn org.springframework.boot:spring-boot-maven-plugin:run
```

Overrides opcionales:

```powershell
$env:MAIDAV_TESTING_DB_PORT="5434"
$env:MAIDAV_TESTING_DB_NAME="sales_db_testing"
$env:MAIDAV_TESTING_DB_USERNAME="sales_testing"
$env:MAIDAV_TESTING_DB_PASSWORD="TU_PASSWORD_TESTING"
```

## 3. Perfil prod

Levanta primero el tunel SSH a produccion.

Datos reales detectados en este proyecto:

- servidor: `187.127.10.6`
- contenedor db produccion: `vps-spring-boot-maidav-lixir2-db-1`
- IP interna produccion: `172.20.0.2`
- base produccion: `sales_db`
- usuario produccion: `sales`

```powershell
ssh -L 5435:172.20.0.2:5432 root@187.127.10.6
```

Si prefieres conservar el tunel historico de `prod` en `5433`, puedes hacerlo y sobreescribir el puerto del perfil con `MAIDAV_PROD_DB_PORT=5433`.

Verifica el tunel:

```powershell
Test-NetConnection 127.0.0.1 -Port 5435
```

Opcional: valida credenciales antes de levantar Spring:

```powershell
$env:PGPASSWORD="TU_PASSWORD_REAL_DE_PROD"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h 127.0.0.1 -p 5435 -U sales -d sales_db -c "select current_user, current_database();"
```

Luego corre la app:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:MAIDAV_PROD_DB_PORT="5435"
$env:MAIDAV_PROD_DB_NAME="sales_db"
$env:MAIDAV_PROD_DB_USERNAME="sales"
$env:MAIDAV_PROD_DB_PASSWORD="TU_PASSWORD_REAL_DE_PROD"
mvn org.springframework.boot:spring-boot-maven-plugin:run
```

Overrides opcionales:

```powershell
$env:MAIDAV_PROD_DB_PORT="5435"
$env:MAIDAV_PROD_DB_NAME="sales_db"
$env:MAIDAV_PROD_DB_USERNAME="sales"
$env:MAIDAV_PROD_DB_PASSWORD="TU_PASSWORD_PROD"
```

## 4. Cuando despliegas en servidor

Si el servidor usa estos perfiles:

- `testing`: migrara testing automaticamente
- `prod`: solo migrara produccion si seteas `MAIDAV_PROD_FLYWAY_ENABLED=true`

Si el servidor no usa estos perfiles y sigue usando solo `application.yml` con `SPRING_DATASOURCE_*`, entonces el comportamiento actual del deploy no cambia.

## 5. Errores reales y como reconocerlos

Si ves:

```text
Connection to 127.0.0.1:5434 refused
```

entonces el tunel de `testing` no esta levantado o esta en otro puerto.

Si ves:

```text
FATAL: password authentication failed for user "sales_testing"
```

entonces el tunel esta bien, pero la password local no coincide con la real.

Si ves:

```text
FATAL: invalid value for parameter "TimeZone"
```

entonces la app estaba intentando conectar con un timezone no aceptado por PostgreSQL. Este proyecto ya quedo ajustado para normalizar el timezone al arrancar.
