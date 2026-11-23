# Resumen de cobros y pagos: tercera entrega

**Finanzas → Resumen** compara los cobros netos registrados con las compras y gastos pagados de un período. Permite conciliar los totales con un detalle paginado y un desglose por medio de pago. Es una consulta: no crea movimientos adicionales ni cambia Ventas, Crédito, Compras o Gastos.

## Recorrido de prueba

1. Ingresar como administrador y abrir **Finanzas → Resumen**. Inicialmente muestra desde el primer día del mes hasta hoy.
2. Seleccionar fechas desde/hasta y consultar.
3. Comparar ventas de contado, cobros de crédito y reversiones con los registros de origen.
4. Comprobar que Compras y Gastos coincidan con los importes pagados en esas mismas fechas.
5. Revisar el desglose por medio de pago y abrir el origen de un movimiento.
6. Cambiar de página: los totales deben seguir representando todo el período.

## Qué se suma

| Concepto | Fuente y criterio |
|---|---|
| Ventas de contado | `sales.total_amount` de ventas activas con `payment_type=CASH`, por `sale_date`. Contado puede ser efectivo, banco, transferencia o débito. |
| Cobros de crédito | `credit_payments.amount` con `reversal=false`, por `paid_at`, vinculados a una venta a crédito activa y una cuenta no anulada. Incluye cobros de ventas de meses anteriores. |
| Ajustes por reversiones | `credit_payments.amount` con `reversal=true`, con su signo registrado y por `paid_at`. Normalmente son negativos y reducen los cobros netos. |
| Compras pagadas | `purchases.total_amount` por `purchase_date`. La primera entrega de Compras considera cada registro recibido y pagado íntegramente en esa fecha. |
| Gastos pagados | `expenses.amount` por `expense_date`. La primera entrega de Gastos considera cada registro pagado íntegramente en esa fecha. |

**Diferencia del período = contado + cobros de crédito + ajustes − compras − gastos.** En el detalle, ingresos positivos, pagos y ajustes negativos. No se usa `impact_amount`: representa efecto sobre deuda y puede diferir del dinero cobrado. Tampoco se suman otra vez las cuotas, las imputaciones de pagos ni los ítems de una operación.

Las ventas registradas del período se muestran como referencia comercial separada: total de ventas activas de contado y a crédito por fecha de venta. No se agregan a los cobros.

## Alcance y límites visibles en la pantalla

- La diferencia no incluye saldo inicial ni representa rentabilidad, saldo bancario o arqueo de efectivo.
- Se excluyen ventas de contado anuladas y pagos de cuentas o ventas anuladas, o que ya no corresponden a una venta a crédito. Cuando existen, se muestran cantidad e importe excluidos.
- Una reversión ajusta el registro de un cobro; no acredita por sí sola una devolución de dinero.
- El informe usa el estado actual de los registros: editar o anular operaciones puede cambiar un período anterior. No constituye un cierre histórico inmutable.
- Se conservan los medios de pago existentes. `BANK` se muestra como Banco, sin reclasificar automáticamente datos legados entre débito y transferencia. Los registros sin medio se agrupan como **Sin medio informado**.
- No incluye otros ingresos, retiros, aportes o egresos que no estén registrados en estas cuatro fuentes. No calcula costo de mercadería vendida ni rentabilidad.

## Fechas, consistencia y permisos

Las fechas son inclusivas. Para ventas con hora se consulta desde las 00:00 del primer día hasta antes de las 00:00 del día siguiente al último, respetando la configuración horaria argentina de la aplicación. No se admiten períodos futuros ni desde posterior a hasta. Si solo se indica hasta, desde toma el primer día de ese mes.

La consulta usa JPA con SQL nativo y parámetros tipados. Agrega en base de datos y obtiene únicamente una página de 20 movimientos, sin cargar comprobantes ni colecciones de productos, cuotas o imputaciones. Los totales y el detalle se leen en una transacción `REPEATABLE_READ` para mantener una misma instantánea durante la solicitud.

La migración **V57__add_finance_summary_permission_and_indexes.sql** agrega `FINANCE_READ`, asignado inicialmente a ADMIN, e índices por fecha para las consultas. Este permiso habilita un resumen global del negocio. No concede acceso a los documentos de origen: los enlaces se muestran solo con `SALES_READ`, `ARREARS_READ`, `PURCHASE_READ` o `EXPENSE_READ`, según corresponda.

## Verificación reproducible

Las pruebas requieren la misma base PostgreSQL local desechable utilizada en Compras y Gastos: `maidav_purchases_test`, usuario `purchases_test`, en `127.0.0.1`. No apuntar a datos reales ni a una base compartida.

```powershell
$env:MAIDAV_PURCHASES_TEST_URL='jdbc:postgresql://127.0.0.1:55439/maidav_purchases_test'
.\mvnw.cmd -o '-Dmaven.repo.local=.m2/repository' '-Dtest=FinanceIntegrationTest' test
node --test src/test/browser/finances.test.cjs
```

Los casos incluyen límites de fecha con microsegundos, crédito de un mes anterior, diferencia entre cobro e impacto, pagos distribuidos en varias cuotas, compras con varios ítems, reversiones negativas, registros excluidos, medios sin informar, períodos vacíos, paginación y permisos independientes. Las vistas de prueba se generan en `target/finances-preview/`; las capturas aíslan el módulo del menú y de dependencias CDN y no sustituyen una prueba contra el servidor desplegado.

Resultado del 2026-11-19: **130 pruebas Java y 4 pruebas de navegador aprobadas**, sin errores ni omisiones. Se verificaron las 57 migraciones con PostgreSQL 18.3 temporal; PostgreSQL 16, la versión objetivo documentada del proyecto, no se ejecutó en esta validación. Se revisaron las capturas del resumen positivo, negativo y sin movimientos, con emulación móvil de 390 px sin desborde horizontal de página. Las tablas anchas permiten desplazamiento horizontal dentro de su contenedor. El log completo está en `target/finances-final-tests.log` y las capturas en `target/finances-preview/`.

## Límite de reversión y próxima entrega

La unidad incluye `FinanceController`, `service/finance`, `repository/finance`, la plantilla y CSS de Finanzas, el enlace Resumen en el menú, las pruebas y esta guía. Retirar esas rutas y pantallas no altera ventas, cobros, stock, compras ni gastos. Si V57 fue aplicada, conservarla; cualquier reversión de esquema debe realizarse mediante otra migración.

Después de validar este resumen con el usuario, continuar con **Campañas**, su visualización y la acción **Crear venta** desde una oferta. La rentabilidad queda sujeta a definir el costo histórico y el criterio de cálculo.
