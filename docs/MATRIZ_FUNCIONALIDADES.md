# Matriz de funcionalidades – CampusLab

Alcance del proyecto y estado de cada funcionalidad, con el microservicio
responsable y una evidencia verificable. Las rutas de endpoint son las que
expone cada microservicio; desde el frontend se consumen a través del BFF
(`ms-campuslab-bff`), que reenvía por prefijo (ver `GatewayConfig`).

**Estados:** ✅ Implementada · 🟡 Parcial · ❌ No implementada

> Columna **Commit / PR**: **EP3** = commit de esta entrega (rama
> `ep3/entrega`, ver [Pull Requests](#pull-requests-de-la-entrega));
> **main** = la funcionalidad ya existía en `main` antes de la EP3 y se
> enlaza el commit vigente de esa rama.

## Repositorios

| Componente | Repositorio |
|---|---|
| Frontend (Angular + MSAL) | https://github.com/TomasZamora10/Front-CampusLab |
| BFF / API Gateway | https://github.com/Sicr0911/MS-Campuslab-BFF |
| Catálogo | https://github.com/TomasZamora10/ms-campuslab-catalog |
| Reservas | https://github.com/Sicr0911/MS-Campuslab-Bookings |
| Notificaciones (consumidor RabbitMQ) | https://github.com/Sicr0911/ms-campuslab-notify |
| Administrador RabbitMQ | https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin |
| Auditoría (consumidor Kafka) | https://github.com/Sicr0911/ms-campuslab-audit |
| Reportes / KPIs (consumidor Kafka) | https://github.com/Sicr0911/ms-campuslab-report |
| Administrador Kafka | https://github.com/Sicr0911/ms-campuslab-kafka-admin |

## Mensajería: qué va por RabbitMQ y qué por Kafka

| Flujo | Broker | Productor → Consumidor | Motivo |
|---|---|---|---|
| Notificación email/push al estudiante | **RabbitMQ** (`cmd.direct` / `email.send` → `q.cmd.email`) | bookings → notify | Tarea asíncrona (comando) con ACK manual y DLQ |
| Ticket de preparación para el técnico | **RabbitMQ** (`cmd.direct` / `prep.ticket` → `q.cmd.prep`) | bookings → notify | Ídem |
| Documento PDF: vale de retiro / acta de devolución | **RabbitMQ** (`cmd.direct` / `voucher.gen` → `q.cmd.voucher`) | bookings → notify | Ídem |
| Alerta de stock agotado | **RabbitMQ** (`cmd.topic` / `email.*` → `q.cmd.email`) | catalog → notify | Alerta interna asíncrona |
| Mensajes fallidos de las tres colas | **RabbitMQ** (`cmd.dlx` → `q.cmd.*.dlq`) | broker (dead-lettering) | `basicNack(requeue=false)` |
| Historial de eventos de reservas (auditoría, KPIs) | Kafka (`bookings.events`) | bookings → audit, report | Log de eventos reproducible, varios consumidores |

Las tareas asíncronas evaluadas (notificaciones, tickets y documentos)
pasan **exclusivamente por RabbitMQ**. Kafka solo transporta el historial
de eventos que leen auditoría y reportes.

## Matriz

| # | Funcionalidad | Estado | Microservicio | Evidencia verificable | Commit / PR |
|---|---|---|---|---|---|
| **Seguridad y acceso** |||||
| 1 | Login con Azure AD (MSAL) y rutas protegidas | ✅ | Front-CampusLab | `src/app/app.routes.ts` (`MsalGuard`) | main [a7d6307](https://github.com/TomasZamora10/Front-CampusLab/commit/a7d6307) |
| 2 | Validación de JWT de Azure AD (issuer + audience) y roles | ✅ | ms-campuslab-bff | `SecurityConfig`, `AudienceValidator`, `AzureAdJwtAuthenticationConverter` | main [6217da6](https://github.com/Sicr0911/MS-Campuslab-BFF/commit/6217da6) |
| 3 | Enrutamiento del BFF hacia los microservicios | ✅ | ms-campuslab-bff | `GatewayConfig`: `/api/catalog/**`, `/api/bookings/**`, `/api/audit/**`, `/api/report/**`, `/api/admin/rabbitmq/**`, `/api/admin/kafka/**` | main [6217da6](https://github.com/Sicr0911/MS-Campuslab-BFF/commit/6217da6) |
| 4 | Autorización por rol en cada microservicio y en el frontend (App Roles de Azure AD normalizados: `Admin` → `ADMIN`) | ✅ | todos los backend, Front-CampusLab | `@PreAuthorize` en los controllers; `SecurityConfig#normalizarRol` en cada microservicio; `AuthService` del front; `SecurityConfigTest` | main (cada repo); EP3 [2db5135](https://github.com/Sicr0911/MS-Campuslab-BFF/commit/2db5135), [b4f06b3](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/b4f06b3), [9c5b6e0](https://github.com/Sicr0911/ms-campuslab-audit/commit/9c5b6e0), [a1e565b](https://github.com/Sicr0911/ms-campuslab-report/commit/a1e565b), [476fbf9](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/476fbf9), [1f20537](https://github.com/Sicr0911/ms-campuslab-kafka-admin/commit/1f20537), [9579c49](https://github.com/TomasZamora10/Front-CampusLab/commit/9579c49) |
| **Catálogo** |||||
| 5 | Listar / ver recursos (salas, equipos) | ✅ | ms-campuslab-catalog | `GET /api/catalog/resources`, `GET /api/catalog/resources/{id}` | main [863dff8](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/863dff8) |
| 6 | Crear recurso | ✅ | ms-campuslab-catalog | `POST /api/catalog/resources` | main [863dff8](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/863dff8) |
| 7 | Actualizar stock / cupo con validación | ✅ | ms-campuslab-catalog | `PUT /api/catalog/resources/{id}` (`@Valid StockUpdateRequest`) | main [863dff8](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/863dff8) |
| 8 | Alerta de stock agotado por RabbitMQ | ✅ | ms-campuslab-catalog → ms-campuslab-notify | `PUT /api/catalog/resources/{id}` con stock 0 → `cmd.topic` / `email.stock-agotado` → binding `email.*` (`RabbitMqConfig#emailAlertBinding`) → `q.cmd.email`; `MsCampuslabCatalogApplicationTests`, `EmailNotificationListenerTest` | EP3 [9a9c689](https://github.com/Sicr0911/ms-campuslab-notify/commit/9a9c689), [4b620ad](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/4b620ad), [f049f35](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/f049f35) |
| **Reservas** |||||
| 9 | Crear, ver y listar reservas con filtros y paginación | ✅ | ms-campuslab-bookings | `POST /api/bookings`, `GET /api/bookings/{id}`, `GET /api/bookings?estado=&recursoId=&page=` | main [af70394](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/af70394) |
| 10 | Máquina de estados y regla crítica (EN_USO solo si fue APROBADA por TECNICO/ADMIN) | ✅ | ms-campuslab-bookings | `PUT /api/bookings/{id}/status` → 409; `ReservaServiceImplTest`; constraint `CK_RESERVAS_EN_USO_REQ_APROB` | main [af70394](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/af70394) |
| 11 | Verificación de stock contra catálogo antes de aprobar | ✅ | ms-campuslab-bookings | `CatalogClientImpl` | main [af70394](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/af70394) |
| 12 | Publicación de comandos asíncronos a RabbitMQ | ✅ | ms-campuslab-bookings | `ReservaEventPublisher` → `cmd.direct` (`email.send`, `prep.ticket`, `voucher.gen`), nombres desde `application.yml`; `ReservaEventPublisherTest` | main [af70394](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/af70394); EP3 [3c39975](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/3c39975) |
| 13 | Publicación del historial de eventos a Kafka | ✅ | ms-campuslab-bookings | `ReservaKafkaEventPublisher` → `bookings.events` | main [af70394](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/af70394) |
| **Notificaciones, tickets y documentos (RabbitMQ)** |||||
| 14 | Consumidor de notificaciones email/push | ✅ | ms-campuslab-notify | `notificacion/EmailNotificationListener` (`@RabbitListener`, `q.cmd.email`) | EP3 [9a9c689](https://github.com/Sicr0911/ms-campuslab-notify/commit/9a9c689), [2232be6](https://github.com/Sicr0911/ms-campuslab-notify/commit/2232be6) |
| 15 | Consumidor de tickets de preparación | ✅ | ms-campuslab-notify | `preparacion/PreparationTicketListener` (`@RabbitListener`, `q.cmd.prep`) | EP3 [9a9c689](https://github.com/Sicr0911/ms-campuslab-notify/commit/9a9c689), [2232be6](https://github.com/Sicr0911/ms-campuslab-notify/commit/2232be6) |
| 16 | Consumidor de generación de documentos (vale / acta) | 🟡 | ms-campuslab-notify | `documento/VoucherGenerationListener` (`q.cmd.voucher`); el PDF se simula con un log, no se genera un archivo | EP3 [9a9c689](https://github.com/Sicr0911/ms-campuslab-notify/commit/9a9c689), [2232be6](https://github.com/Sicr0911/ms-campuslab-notify/commit/2232be6) |
| 17 | ACK explícito (modo manual) | ✅ | ms-campuslab-notify | `acknowledge-mode: manual`; `basicAck` / `basicNack` en `ConsumidorConAck`; `ConsumidorConAckTest` | EP3 [9a9c689](https://github.com/Sicr0911/ms-campuslab-notify/commit/9a9c689), [2232be6](https://github.com/Sicr0911/ms-campuslab-notify/commit/2232be6) |
| 18 | DLQ y registro del fallo en logs, con errores recuperables (1 reintento) vs no recuperables (DLQ directo) | ✅ | ms-campuslab-notify | `ConsumidorConAck` + `MensajeInvalidoException`: `basicNack(requeue=true)` en el primer fallo transitorio, `basicNack(requeue=false)` → `cmd.dlx` → `q.cmd.*.dlq` si es no recuperable o ya reentregado; `log.error` con cola, DLQ, motivo y cuerpo; política en el README de notify | EP3 [9a9c689](https://github.com/Sicr0911/ms-campuslab-notify/commit/9a9c689), [2232be6](https://github.com/Sicr0911/ms-campuslab-notify/commit/2232be6) |
| 19 | Declaración centralizada de Queue/Exchange/Binding (notificaciones, tickets, vouchers, DLQ) con nombres desde `application.yml` | ✅ | ms-campuslab-notify | `RabbitMqConfig` + `NotifyRabbitProperties` (`campuslab.rabbitmq.*`); `MsCampuslabNotifyApplicationTests` | EP3 [9a9c689](https://github.com/Sicr0911/ms-campuslab-notify/commit/9a9c689) |
| 20 | Idempotencia por `eventId` | 🟡 | ms-campuslab-notify | `ProcessedEventTracker` (en memoria: se pierde al reiniciar y no se comparte entre réplicas) | main [efe08d3](https://github.com/Sicr0911/ms-campuslab-notify/commit/efe08d3) |
| 21 | Envío real de email/push | ❌ | ms-campuslab-notify | Fuera de alcance: se simula con un log | — |
| 21b | Consumidores agrupados por dominio funcional (`notificacion`, `preparacion`, `documento`) con la configuración de mensajería separada (`config`) | ✅ | ms-campuslab-notify | Paquetes de dominio + `messaging/ConsumidorConAck`; tabla en el README de notify | EP3 [2232be6](https://github.com/Sicr0911/ms-campuslab-notify/commit/2232be6) |
| 21c | Nombres de exchanges y routing keys centralizados también en los productores, con beans de `Exchange` | ✅ | ms-campuslab-bookings, ms-campuslab-catalog | `RabbitMqProperties` + `RabbitMqConfig` (`campuslab.rabbitmq.*` en `application.yml`); sin nombres en el código de negocio | EP3 [3c39975](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/3c39975), [f049f35](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/f049f35) |
| 21d | Mensajes no enrutables registrados en log del lado productor | ✅ | ms-campuslab-bookings, ms-campuslab-catalog | `spring.rabbitmq.template.mandatory=true` + `RabbitMqConfig#mensajesNoEnrutadosCustomizer` (`log.error` con exchange, routing key y cuerpo) | EP3 [3c39975](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/3c39975), [f049f35](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/f049f35) |
| **Administración de RabbitMQ** |||||
| 22 | Estado del broker y listado de colas | ✅ | ms-campuslab-rabbitmq-admin | `GET /api/admin/rabbitmq/overview`, `GET /api/admin/rabbitmq/queues` | main [dc1dd4a](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/dc1dd4a); EP3 [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587) |
| 23 | Capa de servicio con la lógica de crear/actualizar/eliminar | ✅ | ms-campuslab-rabbitmq-admin | `RabbitAdminService`; `RabbitAdminServiceTest` | EP3 [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587) |
| 24 | Administrar colas (crear, actualizar, eliminar) | ✅ | ms-campuslab-rabbitmq-admin | `POST /queues`, `PUT /queues/{name}`, `DELETE /queues/{name}?ifEmpty=` | EP3 [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587) |
| 25 | Administrar exchanges (listar, crear, actualizar, eliminar) | ✅ | ms-campuslab-rabbitmq-admin | `GET/POST /exchanges`, `PUT /exchanges/{name}`, `DELETE /exchanges/{name}?ifUnused=` | EP3 [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587) |
| 26 | Administrar bindings (listar, crear, eliminar) | ✅ | ms-campuslab-rabbitmq-admin | `GET/POST /bindings`, `DELETE /bindings?exchange=&queue=&routingKey=` | EP3 [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587) |
| 27 | Validación de entradas (`@NotBlank`, `@NotNull`, `@Pattern`, `@Valid`) | ✅ | ms-campuslab-rabbitmq-admin | `QueueRequestDTO`, `ExchangeRequestDTO`, `BindingRequestDTO`; 400 con detalle por campo; `RabbitMqAdminControllerTest` | EP3 [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587) |
| 28 | Protección de la topología productiva (no se modifica ni elimina) | ✅ | ms-campuslab-rabbitmq-admin | `campuslab.rabbitmq.protected-resources` → 409 | EP3 [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587) |
| 28b | API REST del administrador documentada (endpoints, ejemplos, validaciones y códigos de error) | ✅ | ms-campuslab-rabbitmq-admin | [`README.md`](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/blob/ep3/entrega/README.md) | EP3 [4fdb661](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/4fdb661) |
| 29 | Panel de administración RabbitMQ en el frontend (overview, colas, exchanges, bindings) | ✅ | Front-CampusLab | Ruta `/admin/rabbitmq` (solo ADMIN): `pages/rabbitmq/`, `RabbitmqAdminService`; `rabbitmq.spec.ts` | EP3 [86f684d](https://github.com/TomasZamora10/Front-CampusLab/commit/86f684d) |
| 29b | Topología RabbitMQ versionada | ✅ | infra (repo BFF) | `infra/mq/definitions.json` (exchanges, colas, DLQ y bindings), igual a la que declara `RabbitMqConfig` de notify | EP3 [2db5135](https://github.com/Sicr0911/MS-Campuslab-BFF/commit/2db5135) |
| **Auditoría y reportes (Kafka)** |||||
| 30 | Timeline de auditoría por reserva y búsqueda con filtros | ✅ | ms-campuslab-audit | `GET /api/audit/reservas/{reservaId}`, `GET /api/audit` | main [be6884e](https://github.com/Sicr0911/ms-campuslab-audit/commit/be6884e) |
| 31 | Consumo idempotente de `bookings.events` con DLT | ✅ | ms-campuslab-audit | `BookingsEventListener`, `KafkaConsumerConfig` (`bookings.events.audit-svc.DLT`); `MsCampuslabAuditApplicationTests` | main [be6884e](https://github.com/Sicr0911/ms-campuslab-audit/commit/be6884e); tests EP3 [beabe6b](https://github.com/Sicr0911/ms-campuslab-audit/commit/beabe6b) |
| 32 | KPIs y recursos más reservados por rango | ✅ | ms-campuslab-report | `GET /api/report/kpis?range=last24h`, `GET /api/report/top-resources?range=last7d`; `RangeParserTest` | main [465f734](https://github.com/Sicr0911/ms-campuslab-report/commit/465f734); tests EP3 [aed8ace](https://github.com/Sicr0911/ms-campuslab-report/commit/aed8ace) |
| 33 | Administración de Kafka (topics, consumer groups) | ✅ | ms-campuslab-kafka-admin | `GET /api/admin/kafka/topics`, `GET /api/admin/kafka/consumer-groups` | main [e5f35c9](https://github.com/Sicr0911/ms-campuslab-kafka-admin/commit/e5f35c9); tests EP3 [7ca2f64](https://github.com/Sicr0911/ms-campuslab-kafka-admin/commit/7ca2f64) |
| **Pruebas** |||||
| 34 | Prueba de carga de contexto (`@SpringBootTest`) | ✅ | todos los backend | `src/test/java/**/*ApplicationTests.java` en notify, rabbitmq-admin, report, audit, catalog, kafka-admin y bff (sin infraestructura externa: H2, mocks y listeners desactivados) | EP3 [9a9c689](https://github.com/Sicr0911/ms-campuslab-notify/commit/9a9c689), [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587), [aed8ace](https://github.com/Sicr0911/ms-campuslab-report/commit/aed8ace), [beabe6b](https://github.com/Sicr0911/ms-campuslab-audit/commit/beabe6b), [4b620ad](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/4b620ad), [7ca2f64](https://github.com/Sicr0911/ms-campuslab-kafka-admin/commit/7ca2f64), [2db5135](https://github.com/Sicr0911/MS-Campuslab-BFF/commit/2db5135) |
| 35 | Pruebas unitarias de reglas de negocio | ✅ | bookings, rabbitmq-admin, notify, report, kafka-admin | `ReservaServiceImplTest`, `ReservaEventPublisherTest`, `RabbitAdminServiceTest`, `ConsumidorConAckTest`, `EmailNotificationListenerTest`, `VoucherGenerationListenerTest`, `RangeParserTest`, `KafkaAdminServiceTest` | main [af70394](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/af70394); EP3 [3c39975](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/3c39975), [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587), [2232be6](https://github.com/Sicr0911/ms-campuslab-notify/commit/2232be6), [aed8ace](https://github.com/Sicr0911/ms-campuslab-report/commit/aed8ace), [7ca2f64](https://github.com/Sicr0911/ms-campuslab-kafka-admin/commit/7ca2f64) |
| 36 | Pruebas de seguridad por rol (401/403) | ✅ | bff, rabbitmq-admin, kafka-admin | `CampuslabBffApplicationTests`, `RabbitMqAdminControllerTest`, `MsCampuslabKafkaAdminApplicationTests` | EP3 [2db5135](https://github.com/Sicr0911/MS-Campuslab-BFF/commit/2db5135), [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587), [7ca2f64](https://github.com/Sicr0911/ms-campuslab-kafka-admin/commit/7ca2f64) |
| 37 | Pruebas del frontend (componentes con MSAL y HTTP simulados) | ✅ | Front-CampusLab | `npm test`: 11 tests en 9 specs; `src/testing/providers-de-prueba.ts` | EP3 [86f684d](https://github.com/TomasZamora10/Front-CampusLab/commit/86f684d) |

## Pull Requests de la entrega

Todos los cambios de la EP3 están en la rama `ep3/entrega` de cada repositorio.

| Repositorio | Commits EP3 | Rama | Pull Request |
|---|---|---|---|
| MS-Campuslab-Bookings | [3c39975](https://github.com/Sicr0911/MS-Campuslab-Bookings/commit/3c39975) | [ep3/entrega](https://github.com/Sicr0911/MS-Campuslab-Bookings/tree/ep3/entrega) | [#1](https://github.com/Sicr0911/MS-Campuslab-Bookings/pull/1) |
| ms-campuslab-notify | [9a9c689](https://github.com/Sicr0911/ms-campuslab-notify/commit/9a9c689), [2232be6](https://github.com/Sicr0911/ms-campuslab-notify/commit/2232be6) | [ep3/entrega](https://github.com/Sicr0911/ms-campuslab-notify/tree/ep3/entrega) | [#1](https://github.com/Sicr0911/ms-campuslab-notify/pull/1) |
| ms-campuslab-rabbitmq-admin | [5179587](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/5179587), [4fdb661](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/commit/4fdb661) | [ep3/entrega](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/tree/ep3/entrega) | [#1](https://github.com/Sicr0911/ms-campuslab-rabbitmq-admin/pull/1) |
| ms-campuslab-report | [aed8ace](https://github.com/Sicr0911/ms-campuslab-report/commit/aed8ace) | [ep3/entrega](https://github.com/Sicr0911/ms-campuslab-report/tree/ep3/entrega) | [#1](https://github.com/Sicr0911/ms-campuslab-report/pull/1) |
| ms-campuslab-audit | [beabe6b](https://github.com/Sicr0911/ms-campuslab-audit/commit/beabe6b) | [ep3/entrega](https://github.com/Sicr0911/ms-campuslab-audit/tree/ep3/entrega) | [#1](https://github.com/Sicr0911/ms-campuslab-audit/pull/1) |
| ms-campuslab-catalog | [4b620ad](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/4b620ad), [f049f35](https://github.com/TomasZamora10/ms-campuslab-catalog/commit/f049f35) | [ep3/entrega](https://github.com/TomasZamora10/ms-campuslab-catalog/tree/ep3/entrega) | [#1](https://github.com/TomasZamora10/ms-campuslab-catalog/pull/1) |
| ms-campuslab-kafka-admin | [7ca2f64](https://github.com/Sicr0911/ms-campuslab-kafka-admin/commit/7ca2f64) | [ep3/entrega](https://github.com/Sicr0911/ms-campuslab-kafka-admin/tree/ep3/entrega) | [#1](https://github.com/Sicr0911/ms-campuslab-kafka-admin/pull/1) |
| MS-Campuslab-BFF | [2db5135](https://github.com/Sicr0911/MS-Campuslab-BFF/commit/2db5135) | [ep3/entrega](https://github.com/Sicr0911/MS-Campuslab-BFF/tree/ep3/entrega) | [#1](https://github.com/Sicr0911/MS-Campuslab-BFF/pull/1) |
| Front-CampusLab | [86f684d](https://github.com/TomasZamora10/Front-CampusLab/commit/86f684d) | [ep3/entrega](https://github.com/TomasZamora10/Front-CampusLab/tree/ep3/entrega) | [#1](https://github.com/TomasZamora10/Front-CampusLab/pull/1) |

## Cómo verificar

```bash
# En cada microservicio backend
mvn test

# En el frontend
npm test
```

Ningún test necesita RabbitMQ, Kafka, Oracle ni el IdP: catalog, report y
audit usan el perfil `test` (H2 en memoria), notify desactiva el arranque de
sus listeners y el BFF, rabbitmq-admin y kafka-admin simulan el token con
`jwt()` de spring-security-test.
