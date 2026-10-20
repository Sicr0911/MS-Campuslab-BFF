# CampusLab – BFF

Backend For Frontend de CampusLab: valida el JWT de Azure AD y enruta las
peticiones del frontend hacia los microservicios de dominio (catálogo,
reservas, auditoría, reportes y administración de RabbitMQ y Kafka).

- Código: [`ms-campuslab-bff/`](ms-campuslab-bff/)
- Enrutamiento: [`GatewayConfig.java`](ms-campuslab-bff/src/main/java/com/campuslab/bff/config/GatewayConfig.java)
- Seguridad: [`SecurityConfig.java`](ms-campuslab-bff/src/main/java/com/campuslab/bff/config/SecurityConfig.java)

## Sistematización del proyecto

La **matriz de funcionalidades** (alcance, estado, microservicio responsable
y evidencia de cada funcionalidad, para todo el sistema) está en
[`docs/MATRIZ_FUNCIONALIDADES.md`](docs/MATRIZ_FUNCIONALIDADES.md).

La topología de RabbitMQ (exchanges, colas, DLQ y bindings) está versionada
en [`infra/mq/definitions.json`](infra/mq/definitions.json); su
[README](infra/mq/README.md) explica cómo importarla en el broker.

## Rutas del gateway

| Prefijo | Microservicio |
|---|---|
| `/api/catalog/**` | ms-campuslab-catalog |
| `/api/bookings/**` | ms-campuslab-bookings |
| `/api/audit/**` | ms-campuslab-audit |
| `/api/report/**` | ms-campuslab-report |
| `/api/admin/rabbitmq/**` | ms-campuslab-rabbitmq-admin |
| `/api/admin/kafka/**` | ms-campuslab-kafka-admin |
