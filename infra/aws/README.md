# Despliegue en AWS (AWS Academy)

## Instancias

| Instancia | Rol | IP pública (2026-10-26) | Puertos |
|---|---|---|---|
| `ec2-apps` | BFF y microservicios (Docker) | `100.53.6.28` | 8080 (BFF), 8081 (catalog) abiertos a internet; el resto solo interno |
| `ec2-mq` | RabbitMQ | `100.26.43.133` | 5672 (AMQP), 15672 (Management): solo desde `ec2-apps` |
| `ec2-kafka` | Kafka | `100.26.47.196` | 9092: solo desde `ec2-apps` |

> **Las IP públicas cambian cada vez que se reinicia el lab** (no hay IP
> elástica). Las **IP privadas** (`172.31.x.x`, columna "Dirección IPv4
> privada" en la consola de EC2) no cambian: son las que conviene usar en las
> variables de entorno de `ec2-apps`.

## Variables de entorno en `ec2-apps`

Los `application.yml` traen valores por defecto, pero en EC2 se sobrescriben
con un `.env` (fuera del control de versiones):

| Variable | Usada por | Valor |
|---|---|---|
| `RABBITMQ_HOST` | catalog, bookings, notify, rabbitmq-admin | IP privada de `ec2-mq` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | catalog, bookings, notify, rabbitmq-admin | credenciales del broker |
| `KAFKA_BOOTSTRAP_SERVERS` | bookings, audit, report, kafka-admin | `<IP privada de ec2-kafka>:9092` |
| `AUTH_ISSUER_URI` | bookings, audit, report, rabbitmq-admin, kafka-admin | `https://login.microsoftonline.com/<tenant>/v2.0` |
| `DB_HOST`, `DB_PORT`, `DB_SERVICE`, `DB_USERNAME`, `DB_PASSWORD` | catalog, bookings, audit, report | base de datos Oracle |

## Después de reiniciar el lab

1. En EC2 → Instances, verificar que las 3 instancias estén *Running*.
2. Si el `.env` usa IP públicas, actualizarlas (o cambiarlas por las privadas).
3. En `ec2-apps` (EC2 Instance Connect):

   ```bash
   docker ps                                   # contenedores en ejecución
   timeout 3 bash -c "</dev/tcp/<ip-ec2-mq>/5672"    && echo "RabbitMQ OK"
   timeout 3 bash -c "</dev/tcp/<ip-ec2-kafka>/9092" && echo "Kafka OK"
   docker compose up -d                        # levantar/reiniciar los servicios
   ```

4. Si la topología de RabbitMQ no existe en el broker, importarla con
   [`infra/mq/definitions.json`](../mq/README.md).
