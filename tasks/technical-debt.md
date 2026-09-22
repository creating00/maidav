# Backlog de deuda técnica

**Estado:** inventario propuesto; no implica ejecución.
**Fecha:** 2026-09-18.
**Alcance:** hallazgos del repositorio relevado. Las recomendaciones no fueron aplicadas.

## Priorización

| ID | Prioridad | Hallazgo y evidencia | Impacto | Recomendación | Criterio de aceptación | Estado propuesto |
|---|---|---|---|---|---|---|
| TD-01 | Alta | Posibles datos sensibles en instructivos, `application-local.yml` y archivos Compose. | Exposición accidental de información de entorno. | Auditar, retirar o sustituir valores sensibles por mecanismos seguros; documentar el tratamiento de secretos. | No hay secretos reales versionados y la documentación no expone valores operativos. | Pendiente de auditoría. |
| TD-02 | Alta | `scripts/db-refresh-testing.sh` es destructivo y sincroniza producción con pruebas. | Pérdida o exposición de datos; daño al ambiente objetivo. | Añadir salvaguardas, confirmaciones y anonimización; definir procedimiento de recuperación. | La ejecución exige protección explícita, no replica datos sensibles sin anonimizar y deja evidencia verificable. | Pendiente. |
| TD-03 | Alta | Cobertura limitada: 11 pruebas JUnit/Mockito, una `@SpringBootTest`; sin Testcontainers, E2E ni CI versionado detectados. | Regresiones de ventas, crédito, stock y seguridad con detección tardía. | Priorizar pruebas de reglas críticas, integración de PostgreSQL y flujos E2E; incorporar pipeline versionada. | Los flujos críticos cuentan con pruebas automatizadas y se ejecutan en una pipeline versionada. | Pendiente de definición. |
| TD-04 | Media | Posible conflicto entre `LocalDataSourceConfig` y `application-local.yml`. | Fallos o configuración inesperada al usar el perfil local. | Reproducir mediante arranque controlado y resolver según evidencia. | El perfil local inicia de forma determinista y su fuente de `DataSource` está documentada. | Pendiente de confirmación. |
| TD-05 | Media | Archivos de alta concentración: `sales/form.html`, `products/index.html`, `CreditAccountServiceImpl.java`, `CreditAccountController.java`. | Mantenimiento, revisión y pruebas más costosos; mayor riesgo de regresión. | Delimitar responsabilidades y extraer unidades cohesivas con pruebas de regresión. | Cada archivo reduce responsabilidades mezcladas sin cambiar comportamiento, con pruebas de las rutas afectadas. | Pendiente de análisis. |
| TD-06 | Media | UI mediante CDN (Tailwind, Alpine.js, fuentes) sin SRI/versionado final detectado. | Riesgo de disponibilidad, cambios no controlados e integridad de recursos. | Fijar versiones y evaluar SRI o empaquetado local según restricciones operativas. | Los recursos externos críticos tienen versión fijada y protección de integridad o una alternativa local documentada. | Pendiente. |
| TD-07 | Baja | Se detectó texto con codificación incorrecta (mojibake). | Degrada documentación e interfaz; puede inducir errores de soporte. | Identificar archivos afectados y normalizar a UTF-8 validado. | Los textos afectados se visualizan correctamente y se preserva una codificación consistente. | Pendiente de inventario. |
| TD-08 | Baja | `HELP.md` parece provenir de scaffold y estar desactualizado. | Onboarding y operación confusos. | Revisar, actualizar o retirar el documento si no aporta al sistema actual. | El documento refleja el sistema vigente o se elimina con justificación. | Pendiente. |
| TD-09 | Baja | Archivo `.gga` sin seguimiento detectado previamente. | Ruido en el árbol de trabajo o posible archivo no intencional. | Determinar su propósito y versionarlo, ignorarlo o eliminarlo mediante decisión explícita. | El estado del archivo y la regla de seguimiento quedan documentados. | Pendiente de clasificación. |

## Hechos comprobados

- La base de datos usa 50 migraciones Flyway (`src/main/resources/db/migration/`) y el entorno Compose incorpora PostgreSQL.
- El despliegue detectado utiliza Render y Docker/Dockploy; no se detectó Kubernetes ni CI/CD versionado.
- Los módulos de ventas, crédito, catálogo y seguridad son áreas funcionalmente sensibles.

## Pendientes y límites

- TD-04 requiere evidencia de ejecución; no se afirma que el conflicto exista.
- TD-01 requiere inspección de contenido y políticas de despliegue; este backlog no declara una filtración.
- TD-09 se registra como estado preexistente y no se modifica.

## Orden sugerido

1. Tratar TD-01 y TD-02 antes de ampliar automatizaciones o sincronizaciones.
2. Confirmar TD-04 y establecer la base de pruebas de TD-03.
3. Abordar TD-05 por unidades funcionales, con cobertura previa o simultánea.
4. Planificar TD-06 a TD-09 como mantenimiento incremental.
