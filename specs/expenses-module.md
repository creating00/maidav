# Gastos: segunda entrega para validación

**Finanzas → Gastos** permite registrar gastos operativos pagados, consultar su historial y descargar un comprobante opcional con autorización. Es independiente de Compras y no modifica stock, costos, precios ni cuentas de crédito.

## Recorrido de prueba con el usuario

1. Ingresar como administrador y abrir **Finanzas → Gastos → Nuevo gasto**.
2. Elegir fecha, categoría y forma de pago; completar descripción e importe.
3. Adjuntar un PDF, JPG o PNG de hasta 5 MB, si corresponde, y registrar.
4. Verificar el detalle y descargar el comprobante. Probar también un gasto sin adjunto.
5. Combinar filtros de fecha, categoría e importe mínimo/máximo en el historial.
6. Probar un rol sin permisos: no debe ver Gastos ni acceder por URL al historial, formulario, detalle o comprobante.

## Reglas iniciales

| Tema | Comportamiento |
|---|---|
| Registro | Gasto completamente pagado en la fecha indicada, hasta hoy. No representa una cuenta pendiente. |
| Categorías | Alquiler, Servicios, Sueldos, Combustible, Reparaciones, Impuestos, Administrativos y Otros. Catálogo fijo para esta entrega. |
| Descripción | Obligatoria, hasta 1000 caracteres. |
| Importe | Positivo, hasta dos decimales y $9.999.999.999,99. |
| Forma de pago | Efectivo, Banco, Transferencia o Débito; se reutilizan los medios existentes. |
| Reenvíos | La misma operación del mismo usuario retorna el gasto original; no duplica el gasto ni su comprobante. |
| Trazabilidad | Se conserva usuario, fecha de registro y fecha del gasto. |
| Historial | Páginas de 20 registros, ordenadas por fecha e ID descendentes; filtros inclusivos y combinables. |
| Correcciones | No incluye edición, anulación, recurrencias, cuotas ni pagos parciales. Se definirán con el usuario. |

## Comprobantes y acceso

Los comprobantes se guardan como `BYTEA` en una tabla separada de los gastos, dentro de la misma transacción. Así no se cargan sus bytes al consultar el historial y no se exponen por `/uploads/**`. La base de datos y sus copias de respaldo incluyen estos archivos; el límite por adjunto es 5 MB.

El servidor verifica tamaño y firma de PDF, JPEG o PNG, sin confiar en el MIME enviado por el navegador. Elimina rutas y caracteres de control del nombre y ajusta la extensión al contenido detectado. No realiza análisis antivirus ni una validación completa del documento. La descarga exige `EXPENSE_READ`, usa `Content-Disposition: attachment`, `nosniff` y `Cache-Control: no-store`.

Ante un error, se conservan los campos del formulario; el usuario debe volver a elegir el archivo, porque el navegador no permite restaurar una selección de archivo desde el servidor.

| Permiso | Acciones |
|---|---|
| `EXPENSE_READ` | Listado, detalle y descarga del comprobante de los gastos. |
| `EXPENSE_CREATE` | Formulario y registro. Puede utilizarse sin permiso de lectura. |

La migración **V56__create_expenses_module.sql** asigna ambos permisos a ADMIN. Se pueden asignar a otros roles con la administración existente. El menú Finanzas también funciona para roles que solo tengan permisos de Gastos.

## Pruebas técnicas

Las pruebas de integración son optativas: solo admiten una base PostgreSQL desechable en `127.0.0.1`, llamada `maidav_purchases_test`, con usuario `purchases_test`. Se reutiliza la base de prueba de Compras para ejecutar las migraciones y comprobar ambos módulos juntos. Agregan datos sintéticos; no usar una base compartida ni datos reales.

```powershell
$env:MAIDAV_PURCHASES_TEST_URL='jdbc:postgresql://127.0.0.1:55439/maidav_purchases_test'
.\mvnw.cmd -o '-Dmaven.repo.local=.m2/repository' '-Dtest=ExpenseIntegrationTest' test
node --test src/test/browser/expenses.test.cjs
```

La integración comprueba registro, filtros, acceso por permisos, CSRF, descarga privada, tamaño y formato de archivos, rollback ante datos inválidos, reenvíos concurrentes y roles con solo creación. Genera páginas Thymeleaf de prueba en `target/expenses-preview/`. La prueba de navegador verifica las restricciones del formulario y el ancho de las pantallas; genera capturas del módulo aislado del menú y de dependencias CDN. No sustituye una prueba contra el servidor desplegado.

Resultado del 2026-11-19: **123 pruebas Java aprobadas**, sin errores ni omisiones, y **3 pruebas de navegador aprobadas** para Gastos, Compras y catálogo de Ventas. Se verificaron las 56 migraciones y Hibernate con PostgreSQL 18.3 temporal; PostgreSQL 16, la versión objetivo documentada, no se ejecutó en esta validación. Se revisaron las capturas del formulario en escritorio y móvil, del historial y del detalle con adjunto; la emulación móvil comprueba 390 px sin desborde horizontal. Los logs locales son `target/expenses-final-tests.log` y las capturas están en `target/expenses-preview/`.

## Límite de reversión y siguiente entrega

La unidad incluye `model/expense`, `repository/expense`, `service/expense`, `ExpenseController`, `ExpenseForm`, las plantillas y CSS de Gastos, la entrada de menú y el manejo específico de archivos demasiado grandes en `GlobalModelAttributes`. Los archivos de Compras y Ventas conservan su comportamiento; las capturas de navegador ahora comparten `chromium-support.cjs`.

Si V56 ya fue aplicada, no borrarla ni modificarla. Una reversión del esquema requiere otra migración y una decisión sobre gastos y comprobantes históricos. Se puede revertir únicamente esta interfaz y sus rutas sin tocar compras ni stock.

Después de validar Gastos con el usuario, seguir con **Resumen de cobros y pagos**, y luego **Campañas e integración con Ventas**. La medición de rentabilidad requiere definir el costo histórico y su criterio de cálculo.
