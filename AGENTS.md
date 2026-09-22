# Guía de contribución — Maidav

**Alcance:** guía operativa basada en el estado relevado del repositorio.
**Fecha de relevamiento:** 2026-09-18.
**Sistema:** Maidav, monolito web de gestión comercial.

## Antes de contribuir

1. Revisar `specs/current-system.md`, `adr/ADR-000-current-architecture.md` y `tasks/technical-debt.md` para entender el estado actual y los riesgos conocidos.
2. Analizar el impacto funcional y técnico antes de implementar cambios.
3. Mantener las capas existentes: controlador MVC, servicio, repositorio y modelo.
4. Para cambios de esquema, crear una migración Flyway versionada y descriptiva en `src/main/resources/db/migration/`; no alterar migraciones ya aplicadas.
5. Verificar autorización y permisos al incorporar rutas o acciones mutables.

## Convenciones observadas

| Área | Convención actual |
|---|---|
| Servicios | Interfaces `*Service` e implementaciones `*ServiceImpl`. |
| Web | Controladores MVC con rutas plurales; las mutaciones se realizan mediante `POST`. |
| Seguridad | Permisos en mayúsculas con sufijos CRUD; autorización mediante Spring Security y `@PreAuthorize`. |
| Persistencia | JPA directo con PostgreSQL y migraciones Flyway. |
| Presentación | Plantillas Thymeleaf y recursos estáticos; la UI se renderiza en servidor. |

## Áreas que requieren atención

- Las operaciones de ventas, crédito y catálogo concentran lógica de negocio; revisar los efectos sobre cuentas, cuotas, stock, precios y permisos antes de modificarlas.
- Los archivos subidos se sirven por `/uploads/**`; considerar compatibilidad y seguridad al tocar el flujo de imágenes.
- La deuda técnica priorizada y su evidencia están en `tasks/technical-debt.md`.

## Subagentes disponibles

- `.codex/agents/analyst.md`
- `.codex/agents/db-sync-testing.md`
- `.codex/agents/developer.md`
- `.codex/agents/qa.md`
- `.codex/agents/devops.md`
- `.codex/agents/optimizer.md`

## Workflows disponibles

- `.codex/workflows/feature.md`
- `.codex/workflows/bugfix.md`

## Límites de esta guía

No se documentan comandos de ejecución, pruebas o despliegue porque no fueron verificados en este relevamiento. Kubernetes y una canalización CI/CD versionada no fueron detectados.
