# Compras: primera entrega para validación

Maidav incorpora **Finanzas → Compras** para registrar mercadería recibida y pagada, consultar su historial y ver el detalle. Esta entrega es independiente de Gastos, Resumen financiero y Campañas, que quedan para las siguientes etapas.

## Recorrido de prueba

1. Ingresar con un administrador y abrir **Finanzas → Compras → Nueva compra**.
2. Elegir fecha, proveedor opcional y forma de pago. Usar el mismo buscador y catálogo de Nueva venta: buscar por código o descripción, previsualizar por código de barras o abrir el catálogo móvil con imágenes. También se puede usar el lector de cámara existente.
3. Agregar productos existentes, incluso sin stock, y revisar cantidades y precios finales. El precio se completa con el costo de referencia del catálogo y se puede editar; volver a agregar un producto suma una unidad y conserva el precio editado.
4. Verificar los subtotales y el total; registrar la compra.
5. Comprobar el detalle y el aumento de stock en el catálogo. El costo y los precios de venta deben conservarse.
6. Buscar la compra por fecha, proveedor y producto, combinando los filtros.

## Reglas de esta versión

| Tema | Comportamiento inicial |
|---|---|
| Recepción y pago | La compra se recibe y paga completamente en la fecha indicada. La fecha no puede ser futura. |
| Productos | Solo productos existentes del catálogo; entre 1 y 100 ítems. Se permiten productos repetidos con distinto precio; el stock se acumula por producto. |
| Proveedor | Opcional en la compra; no cambia el proveedor asociado al producto en el catálogo. |
| Importes | Precios finales positivos con hasta dos decimales; el servidor calcula subtotales y total. No se agregan impuestos ni flete. Máximo por importe: $9.999.999.999,99. |
| Stock | Se incrementa al confirmar, dentro de la misma transacción. Un fallo revierte todos los cambios. |
| Costos | Se conserva el precio adquirido en cada ítem; no se modifica el costo ni ningún precio del catálogo. |
| Historial | Conserva código, descripción, proveedor informado, cantidades, precios, usuario y fecha de registro. |
| Reenvíos | Una misma operación del mismo usuario devuelve la compra original sin repetir el ingreso de stock. |
| Acceso | `PURCHASE_READ`: listado y detalle; `PURCHASE_CREATE`: formulario y registro. La migración asigna ambos a ADMIN. Se pueden asignar a otros roles con el mecanismo actual. |
| Correcciones | Esta entrega no ofrece edición, anulación ni devolución. Deben diseñarse junto con la reversión de stock y pagos. |

## Aceptación con el usuario

- [ ] Una compra de varios productos refleja cantidades, precios y total esperados.
- [ ] El stock aumenta correctamente sin modificar precios de venta ni costo de catálogo.
- [ ] Fecha, proveedor opcional, formas de pago y observaciones cubren la operación diaria.
- [ ] Los filtros permiten encontrar compras y la vista funciona en escritorio y móvil.
- [ ] Se confirma si hacen falta pagos pendientes, entregas parciales o costos adicionales.
- [ ] Se define la prioridad del flujo de corrección/anulación antes de uso operativo regular.

## Verificación técnica

Migración nueva: `V55__create_purchases_module.sql`. Mantiene las capas MVC, servicio, repositorio y modelo. Las rutas nuevas usan permisos por método y los registros se realizan por POST con CSRF.

Ventas y Compras comparten el marcado de buscador, previsualización y selector de productos en `fragments/product-picker.html`, con los estilos existentes de Ventas. Compras adapta la selección y el precio al abastecimiento mediante `purchase-products.js`; no requiere otra migración. Los resultados se cargan en tandas de cinco y las imágenes solo se solicitan al abrir el catálogo o selector. Las pruebas de navegador cubren código, descripción sin acentos, código inexistente, previsualización, selección sin stock, paginación y conservación del precio editado. El acceso a una cámara física queda para la prueba con el usuario.

Pruebas sin base de datos, usando la caché Maven local del proyecto:

```powershell
.\mvnw.cmd -o '-Dmaven.repo.local=.m2/repository' '-Dtest=PurchaseServiceImplTest' test
```

Las pruebas de integración son optativas y aceptan únicamente una base PostgreSQL desechable en `127.0.0.1`, llamada `maidav_purchases_test`, con usuario `purchases_test`. Aplican todas las migraciones y agregan datos sintéticos; no deben apuntar a una base compartida ni a datos reales.

```powershell
$env:MAIDAV_PURCHASES_TEST_URL='jdbc:postgresql://127.0.0.1:55439/maidav_purchases_test'
.\mvnw.cmd -o '-Dmaven.repo.local=.m2/repository' '-Dtest=PurchaseIntegrationTest' test
node --test src/test/browser/purchases.test.cjs
```

La integración verifica transacciones reales, reenvíos concurrentes, filtros sin duplicados, permisos, CSRF y renderizado Thymeleaf. Genera vistas de prueba en `target/purchases-preview/`; la prueba de navegador usa esas vistas y el JavaScript real del formulario en Chromium. No sustituye una prueba de navegación y carga contra un servidor desplegado.

Resultado de la verificación del 2026-11-19: **110 pruebas Java aprobadas**, sin errores ni omisiones, usando PostgreSQL 18.3 temporal. También aprobaron las pruebas de navegador de Compras y del catálogo de Ventas en Edge. Se revisaron capturas del formulario, historial y detalle; la emulación del formulario móvil verifica un ancho exacto de 390 px sin desborde horizontal. La base objetivo documentada del proyecto es PostgreSQL 16; esa versión no se ejecutó en esta verificación.

Para repetir las capturas del módulo, aislado del menú y de dependencias CDN:

```powershell
$env:PURCHASE_SCREENSHOTS='1'
node --test src/test/browser/purchases.test.cjs
```

## Límite de reversión

La unidad comprende los paquetes `model/purchase`, `repository/purchase`, `service/purchase`, `PurchaseController`, `PurchaseForm`, las plantillas y recursos de compras, sus pruebas y esta guía. Los cambios en componentes existentes son el enlace de Finanzas en `sidebar.html`, `incrementStockForPurchase` en `ProductRepository` y la extracción del marcado compartido de `pages/sales/form.html` a `fragments/product-picker.html`. Para revertir exclusivamente el buscador compartido, se debe restituir ese marcado en Ventas antes de quitar el fragmento y devolver el selector nativo de Compras; esto no requiere revertir datos ni stock.

Si V55 ya fue aplicada, no borrar ni modificar la migración: cualquier reversión del esquema requiere otra migración y una decisión explícita sobre los registros históricos. Quitar el código no revierte el stock ingresado por compras existentes.

## Próxima entrega

Validar este recorrido con el usuario y continuar con **Gastos**. Después implementar el resumen de cobros/pagos, Campañas y su integración con Ventas, conservando separadas las mediciones de caja y rentabilidad.
