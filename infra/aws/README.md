# Despliegue en AWS (AWS Academy)

## Instancias

| Instancia | Rol | IP pública (2026-10-26) | Puertos |
|---|---|---|---|
| `ec2-apps` | Frontend, BFF y microservicios (Docker) | `100.53.6.28` | **solo 80** (frontend) abierto a internet; el resto solo interno |
| `ec2-mq` | RabbitMQ | `100.26.43.133` | 5672 (AMQP), 15672 (Management): solo desde `ec2-apps` |
| `ec2-kafka` | Kafka | `100.26.47.196` | 9092: solo desde `ec2-apps` |

> **Las IP públicas cambian cada vez que se reinicia el lab** (no hay IP
> elástica). Las **IP privadas** (`172.31.x.x`, columna "Dirección IPv4
> privada" en la consola de EC2) no cambian: son las que conviene usar en las
> variables de entorno de `ec2-apps`.

> **Security Group de `ec2-apps`:** dejar entrante solo el puerto 80 (y 22
> para EC2 Instance Connect). Cerrar 8080 y **8081**: catalog no tiene
> seguridad propia y con el puerto abierto cualquiera podia cambiar el stock
> sin iniciar sesion. Con [`docker-compose.yml`](docker-compose.yml) esos
> puertos ya no se publican; nginx (frontend) reenvia `/api/*` al BFF por la
> red interna de Docker.

## Desplegar en `ec2-apps`

1. Clonar los repositorios uno al lado del otro (rama `ep3/entrega`):

   ```bash
   mkdir -p ~/campuslab && cd ~/campuslab
   for r in TomasZamora10/Front-CampusLab TomasZamora10/ms-campuslab-catalog \
            Sicr0911/MS-Campuslab-BFF Sicr0911/MS-Campuslab-Bookings Sicr0911/ms-campuslab-notify \
            Sicr0911/ms-campuslab-rabbitmq-admin Sicr0911/ms-campuslab-audit \
            Sicr0911/ms-campuslab-report Sicr0911/ms-campuslab-kafka-admin; do
     git clone -b ep3/entrega https://github.com/$r.git
   done
   ```

   Para actualizar despues: `git pull` en cada carpeta.

2. Configurar y levantar:

   ```bash
   cd ~/campuslab/MS-Campuslab-BFF/infra/aws
   cp .env.example .env && nano .env      # IP privadas, credenciales de Oracle y RabbitMQ
   docker compose up -d --build
   docker compose ps                      # bff, catalog, bookings y audit deben quedar "healthy"
   ```

3. En Azure AD → App Registration → Authentication, agregar como Redirect
   URI (tipo SPA) `http://<IP publica de ec2-apps>` (cambia en cada reinicio
   del lab).

4. Verificar:

   ```bash
   curl -s localhost:8080/actuator/health                     # {"status":"UP"}
   docker compose logs ms-campuslab-bookings | grep -i "migrat"  # V2 OUTBOX_RABBIT aplicada
   docker compose logs ms-campuslab-audit    | grep -i "migrat"  # V2 SOLICITUDES_INFORMACION aplicada
   ```

   Luego entrar a `http://<IP publica>` con un usuario de cada App Role.

Las migraciones nuevas (Flyway) crean `OUTBOX_RABBIT` (bookings) y
`SOLICITUDES_INFORMACION` (audit) al arrancar; el usuario de Oracle necesita
permiso `CREATE TABLE` y `CREATE SEQUENCE`.

## Variables de entorno en `ec2-apps`

Los `application.yml` traen valores por defecto, pero en EC2 se sobrescriben
con `infra/aws/.env` (fuera del control de versiones; plantilla en
[`.env.example`](.env.example)):

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
   cd ~/campuslab/MS-Campuslab-BFF/infra/aws
   docker compose up -d                        # levantar/reiniciar los servicios
   ```

   Si RabbitMQ estuvo caido, bookings envia solo los avisos que quedaron
   pendientes en `OUTBOX_RABBIT` apenas vuelve el broker. Los mensajes que
   hayan terminado en una DLQ se reenvian desde el panel RabbitMQ de la web
   (boton **Reprocesar**).

4. Si la topología de RabbitMQ no existe en el broker, importarla con
   [`infra/mq/definitions.json`](../mq/README.md).
