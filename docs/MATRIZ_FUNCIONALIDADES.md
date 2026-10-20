# Matriz de funcionalidades – CampusLab

Alcance del proyecto y estado de cada funcionalidad, con el microservicio
responsable y una evidencia verificable. Las rutas de endpoint son las que
expone cada microservicio; desde el frontend se consumen a través del BFF
(`ms-campuslab-bff`), que reenvía por prefijo (ver `GatewayConfig`).

**Estados:** ✅ Implementada · 🟡 Parcial · ❌ No implementada

> Columna **Commit / PR**: completar con el enlace al commit o Pull Request
> una vez subidos los cambios a GitHub.

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
| 1 | Login con Azure AD (MSAL) y rutas protegidas | ✅ | Front-CampusLab | `src/app/app.routes.ts` (`MsalGuard`) | |
| 2 | Validación de JWT de Azure AD (issuer + audience) y roles | ✅ | ms-campuslab-bff | `SecurityConfig`, `AudienceValidator`, `AzureAdJwtAuthenticationConverter` | |
| 3 | Enrutamiento del BFF hacia los microservicios | ✅ | ms-campuslab-bff | `GatewayConfig`: `/api/catalog/**`, `/api/bookings/**`, `/api/audit/**`, `/api/report/**`, `/api/admin/rabbitmq/**`, `/api/admin/kafka/**` | |
| 4 | Autorización por rol en cada microservicio | ✅ | todos los backend | `@PreAuthorize` en los controllers (ADMIN, AUDITOR, TECNICO...) | |
| **Catálogo** |||||
| 5 | Listar / ver recursos (salas, equipos) | ✅ | ms-campuslab-catalog | `GET /api/catalog/resources`, `GET /api/catalog/resources/{id}` | |
| 6 | Crear recurso | ✅ | ms-campuslab-catalog | `POST /api/catalog/resources` | |
| 7 | Actualizar stock / cupo con validación | ✅ | ms-campuslab-catalog | `PUT /api/catalog/resources/{id}` (`@Valid StockUpdateRequest`) | |
| 8 | Alerta de stock agotado por RabbitMQ | ✅ | ms-campuslab-catalog → ms-campuslab-notify | `PUT /api/catalog/resources/{id}` con stock 0 → `cmd.topic` / `email.stock-agotado` → binding `email.*` (`RabbitMqConfig#emailAlertBinding`) → `q.cmd.email`; `MsCampuslabCatalogApplicationTests`, `EmailNotificationListenerTest` | |
| **Reservas** |||||
| 9 | Crear, ver y listar reservas con filtros y paginación | ✅ | ms-campuslab-bookings | `POST /api/bookings`, `GET /api/bookings/{id}`, `GET /api/bookings?estado=&recursoId=&page=` | |
| 10 | Máquina de estados y regla crítica (EN_USO solo si fue APROBADA por TECNICO/ADMIN) | ✅ | ms-campuslab-bookings | `PUT /api/bookings/{id}/status` → 409; `ReservaServiceImplTest`; constraint `CK_RESERVAS_EN_USO_REQ_APROB` | |
| 11 | Verificación de stock contra catálogo antes de aprobar | ✅ | ms-campuslab-bookings | `CatalogClientImpl` | |
| 12 | Publicación de comandos asíncronos a RabbitMQ | ✅ | ms-campuslab-bookings | `ReservaEventPublisher` → `cmd.direct` (`email.send`, `prep.ticket`, `voucher.gen`) | |
| 13 | Publicación del historial de eventos a Kafka | ✅ | ms-campuslab-bookings | `ReservaKafkaEventPublisher` → `bookings.events` | |
| **Notificaciones, tickets y documentos (RabbitMQ)** |||||
| 14 | Consumidor de notificaciones email/push | ✅ | ms-campuslab-notify | `EmailNotificationListener` (`q.cmd.email`) | |
| 15 | Consumidor de tickets de preparación | ✅ | ms-campuslab-notify | `PreparationTicketListener` (`q.cmd.prep`) | |
| 16 | Consumidor de generación de documentos (vale / acta) | 🟡 | ms-campuslab-notify | `VoucherGenerationListener` (`q.cmd.voucher`); el PDF se simula con un log, no se genera un archivo | |
| 17 | ACK explícito (modo manual) | ✅ | ms-campuslab-notify | `acknowledge-mode: manual`; `basicAck` / `basicNack` en cada listener; `EmailNotificationListenerTest` | |
| 18 | DLQ y registro del fallo en logs | ✅ | ms-campuslab-notify | `basicNack(tag, false, false)` → `cmd.dlx` → `q.cmd.*.dlq`; `log.error` con cola, deliveryTag, DLQ y cuerpo del mensaje | |
| 19 | Declaración centralizada de Queue/Exchange/Binding (notificaciones, tickets, vouchers, DLQ) con nombres desde `application.yml` | ✅ | ms-campuslab-notify | `RabbitMqConfig` + `NotifyRabbitProperties` (`campuslab.rabbitmq.*`); `MsCampuslabNotifyApplicationTests` | |
| 20 | Idempotencia por `eventId` | 🟡 | ms-campuslab-notify | `ProcessedEventTracker` (en memoria: se pierde al reiniciar y no se comparte entre réplicas) | |
| 21 | Envío real de email/push | ❌ | ms-campuslab-notify | Fuera de alcance: se simula con un log | |
| **Administración de RabbitMQ** |||||
| 22 | Estado del broker y listado de colas | ✅ | ms-campuslab-rabbitmq-admin | `GET /api/admin/rabbitmq/overview`, `GET /api/admin/rabbitmq/queues` | |
| 23 | Capa de servicio con la lógica de crear/actualizar/eliminar | ✅ | ms-campuslab-rabbitmq-admin | `RabbitAdminService`; `RabbitAdminServiceTest` | |
| 24 | Administrar colas (crear, actualizar, eliminar) | ✅ | ms-campuslab-rabbitmq-admin | `POST /queues`, `PUT /queues/{name}`, `DELETE /queues/{name}?ifEmpty=` | |
| 25 | Administrar exchanges (listar, crear, actualizar, eliminar) | ✅ | ms-campuslab-rabbitmq-admin | `GET/POST /exchanges`, `PUT /exchanges/{name}`, `DELETE /exchanges/{name}?ifUnused=` | |
| 26 | Administrar bindings (listar, crear, eliminar) | ✅ | ms-campuslab-rabbitmq-admin | `GET/POST /bindings`, `DELETE /bindings?exchange=&queue=&routingKey=` | |
| 27 | Validación de entradas (`@NotBlank`, `@NotNull`, `@Pattern`, `@Valid`) | ✅ | ms-campuslab-rabbitmq-admin | `QueueRequestDTO`, `ExchangeRequestDTO`, `BindingRequestDTO`; 400 con detalle por campo; `RabbitMqAdminControllerTest` | |
| 28 | Protección de la topología productiva (no se modifica ni elimina) | ✅ | ms-campuslab-rabbitmq-admin | `campuslab.rabbitmq.protected-resources` → 409 | |
| 29 | Panel de administración RabbitMQ en el frontend (overview, colas, exchanges, bindings) | ✅ | Front-CampusLab | Ruta `/admin/rabbitmq` (solo ADMIN): `pages/rabbitmq/`, `RabbitmqAdminService`; `rabbitmq.spec.ts` | |
| 29b | Topología RabbitMQ versionada | ✅ | infra (repo BFF) | `infra/mq/definitions.json` (exchanges, colas, DLQ y bindings), igual a la que declara `RabbitMqConfig` de notify | |
| **Auditoría y reportes (Kafka)** |||||
| 30 | Timeline de auditoría por reserva y búsqueda con filtros | ✅ | ms-campuslab-audit | `GET /api/audit/reservas/{reservaId}`, `GET /api/audit` | |
| 31 | Consumo idempotente de `bookings.events` con DLT | ✅ | ms-campuslab-audit | `BookingsEventListener`, `KafkaConsumerConfig` (`bookings.events.audit-svc.DLT`); `MsCampuslabAuditApplicationTests` | |
| 32 | KPIs y recursos más reservados por rango | ✅ | ms-campuslab-report | `GET /api/report/kpis?range=last24h`, `GET /api/report/top-resources?range=last7d`; `RangeParserTest` | |
| 33 | Administración de Kafka (topics, consumer groups) | ✅ | ms-campuslab-kafka-admin | `GET /api/admin/kafka/topics`, `GET /api/admin/kafka/consumer-groups` | |
| **Pruebas** |||||
| 34 | Prueba de carga de contexto (`@SpringBootTest`) | ✅ | todos los backend | `src/test/java/**/*ApplicationTests.java` en notify, rabbitmq-admin, report, audit, catalog, kafka-admin y bff (sin infraestructura externa: H2, mocks y listeners desactivados) | |
| 35 | Pruebas unitarias de reglas de negocio | ✅ | bookings, rabbitmq-admin, notify, report, kafka-admin | `ReservaServiceImplTest`, `RabbitAdminServiceTest`, `EmailNotificationListenerTest`, `RangeParserTest`, `KafkaAdminServiceTest` | |
| 36 | Pruebas de seguridad por rol (401/403) | ✅ | bff, rabbitmq-admin, kafka-admin | `CampuslabBffApplicationTests`, `RabbitMqAdminControllerTest`, `MsCampuslabKafkaAdminApplicationTests` | |

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
