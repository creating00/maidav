# Campañas y ofertas: cuarta entrega

El módulo **Campañas comerciales** permite crear promociones por producto, consultar su vigencia e iniciar una venta con el producto y el precio promocional cargados. El inicio muestra hasta tres ofertas vigentes y un acceso al resto.

## Recorrido de prueba con el usuario

1. Como administrador, abrir **Campañas comerciales → Nueva campaña**.
2. Cargar nombre, descripción opcional y fechas de inicio y fin. Ambas fechas están incluidas.
3. Buscar productos por código o descripción con el mismo selector visual de Ventas y Compras. **Mostrar más productos** amplía el listado sin cerrar el selector.
4. Elegir por producto un precio fijo o un porcentaje de descuento y revisar la vista previa. Crear la campaña.
5. Consultar campañas activas, futuras, vencidas, canceladas o todas. Revisar nombre, productos, precio normal de referencia, precio de oferta y fecha de finalización.
6. Como vendedor, abrir una oferta vigente desde Campañas o desde el inicio y pulsar **Crear venta**. Confirmar el cliente y la cantidad; el producto y el precio ya están cargados.
7. Confirmar la venta y verificar stock, total, referencia de campaña en el detalle e ingreso correspondiente en Finanzas. Reenviar la misma operación no debe duplicar la venta.
8. Cancelar una campaña y comprobar que impide nuevas ventas promocionales sin cambiar las ventas anteriores. Si venció o se canceló después de abrir el formulario, la confirmación debe rechazarse.

## Reglas de esta entrega

- Se admiten entre 1 y 100 productos distintos por campaña. Cada oferta tiene un precio positivo, inferior al precio minorista normal. Los porcentajes deben ser mayores que cero y menores que 100; los importes admiten dos decimales. El resultado porcentual se redondea a dos decimales.
- Se guardan el precio minorista de referencia, el precio promocional y los datos descriptivos del producto al crear la campaña. Cambiar después el catálogo no modifica estos precios ni la campaña modifica los precios del catálogo.
- La vigencia usa las fechas inclusivas y la zona horaria argentina configurada en la aplicación. Los estados se calculan a partir de las fechas y de la cancelación.
- Las ofertas son minoristas y de contado. Contado conserva los medios de cobro existentes; no significa exclusivamente efectivo. No se acumulan con financiación, planes manuales, ajustes automáticos ni descuentos adicionales.
- Una venta iniciada desde una oferta aplica esa oferta a su producto. Puede incorporar otros productos a precio normal. No combina diferentes ofertas en una misma venta. Puede haber campañas simultáneas y el usuario elige explícitamente cuál utilizar.
- La fecha de una venta promocional es la del día. El servidor vuelve a verificar vigencia, cancelación, producto, precio y stock al confirmar. La cancelación y la confirmación se coordinan mediante bloqueo transaccional de la campaña.
- Las ventas promocionales confirmadas conservan su campaña y precio de referencia; no se editan. Pueden anularse mediante el flujo existente, con restitución del stock. Cancelar una campaña no anula ventas.
- Las campañas no se editan ni eliminan en esta entrega: se cancelan y se crea una nueva cuando sea necesario. Si se elimina un producto sin ventas, permanece el historial descriptivo de su campaña, sin posibilidad de venderlo.
- Los borradores de venta promocional se guardan separados del borrador de venta común.

## Permisos y persistencia

La migración **V58__create_campaigns_and_sale_offer_trace.sql** incorpora campañas, productos promocionales, claves de idempotencia y referencias de oferta en los ítems de venta. No altera migraciones anteriores.

`CAMPAIGN_READ` se asigna inicialmente a todos los roles existentes para que puedan consultar ofertas. `CAMPAIGN_CREATE` y `CAMPAIGN_UPDATE` se asignan inicialmente a ADMIN. Los roles nuevos deben recibir los permisos que correspondan. Iniciar y confirmar una venta desde una oferta requiere además `SALES_CREATE`. Las mutaciones usan POST, autorización en servidor y CSRF.

Se mantienen las capas MVC, servicio, repositorio y modelo. Crear una campaña no mueve stock ni dinero; el efecto económico ocurre mediante Ventas y el resumen financiero existente.

## Verificación reproducible

Usar exclusivamente la base local desechable `maidav_purchases_test`, con usuario `purchases_test`, en `127.0.0.1:55439`, siguiendo la preparación de las entregas anteriores. No apuntar las pruebas a datos reales.

```powershell
$env:MAIDAV_PURCHASES_TEST_URL='jdbc:postgresql://127.0.0.1:55439/maidav_purchases_test'
$env:SPRING_DATASOURCE_URL=$env:MAIDAV_PURCHASES_TEST_URL
$env:SPRING_DATASOURCE_USERNAME='purchases_test'
$env:SPRING_DATASOURCE_PASSWORD=''
$env:SPRING_PROFILES_ACTIVE='purchases-test'
.\mvnw.cmd -o '-Dmaven.repo.local=.m2/repository' test
node --test src/test/browser/campaigns.test.cjs src/test/browser/finances.test.cjs src/test/browser/expenses.test.cjs src/test/browser/purchases.test.cjs src/test/browser/sales-catalog.test.cjs
```

Resultado del **2026-11-23**: **146 pruebas Java y 6 pruebas de navegador aprobadas**, sin fallos ni omisiones. Incluyen precios fijos y porcentuales, instantáneas, fechas, permisos, CSRF, venta por vendedor, stock, precio alterado, cancelación, vencimiento, reenvíos concurrentes, anulación e integración con Finanzas. Se aplicaron las 58 migraciones en PostgreSQL 18.3 temporal; no se ejecutó PostgreSQL 16, la versión objetivo documentada.

Las pruebas de navegador ejecutan JavaScript real en Edge sobre vistas generadas por Thymeleaf mediante MockMvc. Se revisaron capturas de formulario y detalle a 1440 y 390 px, sin desborde horizontal de página. No sustituyen una prueba completa contra el servidor desplegado. Evidencia local: `target/campaigns-final-tests.log`, `target/campaigns-browser-tests.log` y `target/campaigns-preview/`.

## Reversión y ajustes posteriores

La entrega incluye el módulo de campañas, los accesos del menú y del inicio y su integración en formulario, servicio y detalle de Ventas. Una retirada debe contemplar juntos esos componentes y conservar la información de las ventas ya confirmadas. Si V58 fue aplicada, conservarla y realizar cualquier cambio de esquema mediante una nueva migración.

Quedan para acordar durante las pruebas con el usuario: ofertas financiadas o mayoristas, combinación de promociones, edición de campañas y condiciones comerciales adicionales. La rentabilidad continúa sujeta a definir costos históricos y criterio de cálculo, como se documentó en Finanzas.
