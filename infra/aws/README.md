# Despliegue en AWS (AWS Academy)

```
Internet ──443/80──► ec2-apps: caddy (HTTPS) ─► frontend (nginx) ─► ms-campuslab-bff ─► microservicios
                                                                                     ├─5672/15672─► ec2-mq (RabbitMQ)
                                                                                     ├─9092──────► ec2-kafka (Kafka)
                                                                                     └─1521──────► Oracle
```

## Instancias

| Instancia | Rol | Tamaño recomendado | Entrantes en su Security Group |
|---|---|---|---|
| `ec2-apps` | Caddy, frontend, BFF y 7 microservicios (Docker) | **t3.large** (8 GB), disco 30 GB | 22, **80, 443** desde `0.0.0.0/0`. Nada más. |
| `ec2-mq` | RabbitMQ | t3.small | 5672 y 15672 **desde el SG de `ec2-apps`** |
| `ec2-kafka` | Kafka | t3.medium | 9092 **desde el SG de `ec2-apps`** |
| Oracle | Base de datos (catalog, bookings, audit, report) | — | 1521 **desde el SG de `ec2-apps`** |

> **IP pública:** cambia en cada reinicio del lab. Asignar una **Elastic IP**
> a `ec2-apps` (EC2 → Elastic IPs → Allocate → Associate) evita tener que
> actualizar `SITE_DOMAIN` y la Redirect URI de Azure cada vez. Para hablar
> con `ec2-mq`, `ec2-kafka` y Oracle usar siempre las **IP privadas**
> (`172.31.x.x`), que no cambian.

> **Kafka:** `advertised.listeners` de `ec2-kafka` debe anunciar la **IP
> privada** (`PLAINTEXT://172.31.x.x:9092`). Si anuncia la pública, los
> clientes se conectan primero bien y luego intentan la IP pública, que el
> Security Group bloquea.

> **Por qué HTTPS:** Azure AD solo acepta Redirect URIs `https://` (salvo
> localhost) y MSAL necesita un contexto seguro del navegador. Caddy obtiene
> un certificado de Let's Encrypt para `<ip-con-guiones>.sslip.io`, que
> resuelve a esa IP sin comprar dominio.

## 1. Preparar `ec2-apps` (una sola vez)

Amazon Linux 2023, desde EC2 Instance Connect:

```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -sSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo curl -sSL https://github.com/docker/buildx/releases/download/v0.17.1/buildx-v0.17.1.linux-amd64 \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/*
# 2 GB de swap: compilar 9 imagenes con Maven pide mucha memoria
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 && sudo chmod 600 /swapfile \
  && sudo mkswap /swapfile && sudo swapon /swapfile && echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
exit   # volver a entrar para que el grupo docker aplique
```

## 2. Clonar los repositorios (rama `ep3/entrega`)

```bash
mkdir -p ~/campuslab && cd ~/campuslab
for r in TomasZamora10/Front-CampusLab TomasZamora10/ms-campuslab-catalog \
         Sicr0911/MS-Campuslab-BFF Sicr0911/MS-Campuslab-Bookings Sicr0911/ms-campuslab-notify \
         Sicr0911/ms-campuslab-rabbitmq-admin Sicr0911/ms-campuslab-audit \
         Sicr0911/ms-campuslab-report Sicr0911/ms-campuslab-kafka-admin; do
  git clone -b ep3/entrega https://github.com/$r.git
done
```

Para actualizar después: `for d in ~/campuslab/*/; do git -C "$d" pull; done`.

## 3. Configurar y levantar

```bash
cd ~/campuslab/MS-Campuslab-BFF/infra/aws
cp .env.example .env && nano .env
COMPOSE_PARALLEL_LIMIT=2 docker compose build   # ~10-15 min la primera vez
docker compose up -d
docker compose ps        # bff, catalog, bookings y audit deben quedar "healthy"
```

En `.env` (plantilla: [`.env.example`](.env.example)):

| Variable | Valor |
|---|---|
| `SITE_DOMAIN` | IP pública de `ec2-apps` con guiones + `.sslip.io`, ej. `100-53-6-28.sslip.io` |
| `JAVA_TOOL_OPTIONS` | Memoria de cada JVM (por defecto 256 MB de heap) |
| `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AUTH_ISSUER_URI` | App Registration de Azure AD |
| `DB_HOST`, `DB_PORT`, `DB_SERVICE`, `DB_USERNAME`, `DB_PASSWORD` | Oracle (catalog, bookings, audit, report) |
| `RABBITMQ_HOST`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | IP privada y credenciales de `ec2-mq` |
| `KAFKA_BOOTSTRAP_SERVERS` | `<IP privada de ec2-kafka>:9092` |

Las migraciones de Flyway crean `OUTBOX_RABBIT` (bookings) y
`SOLICITUDES_INFORMACION` (audit) al arrancar: el usuario de Oracle necesita
`CREATE TABLE` y `CREATE SEQUENCE`.

## 4. Azure AD

App Registration `6bf386f7-…` → **Authentication** → plataforma
**Single-page application** → agregar la Redirect URI
`https://<SITE_DOMAIN>` (ej. `https://100-53-6-28.sslip.io`). En
**Enterprise applications → Users and groups**, asignar un usuario de prueba
a cada App Role (Admin, Tecnico, Estudiante, Auditor).

## 5. Verificar

```bash
curl -s localhost:8080/actuator/health                          # {"status":"UP"}
docker compose logs caddy | grep -i "certificate obtained"      # HTTPS listo
docker compose logs ms-campuslab-bookings | grep -i "migrat"    # V2 OUTBOX_RABBIT
docker compose logs ms-campuslab-audit    | grep -i "migrat"    # V2 SOLICITUDES_INFORMACION
docker compose logs ms-campuslab-notify   | grep -i "started"   # consumidores conectados a RabbitMQ
```

Luego abrir `https://<SITE_DOMAIN>` y entrar con cada usuario de prueba.

Si la topología de RabbitMQ no existe en el broker, importarla con
[`infra/mq/definitions.json`](../mq/README.md).

## Después de reiniciar el lab

1. En EC2 → Instances, verificar que las 3 instancias estén *Running*.
2. Sin Elastic IP: actualizar `SITE_DOMAIN` en `.env` y la Redirect URI de
   Azure con la nueva IP pública de `ec2-apps`.
3. En `ec2-apps`:

   ```bash
   timeout 3 bash -c "</dev/tcp/<ip-privada-ec2-mq>/5672"    && echo "RabbitMQ OK"
   timeout 3 bash -c "</dev/tcp/<ip-privada-ec2-kafka>/9092" && echo "Kafka OK"
   cd ~/campuslab/MS-Campuslab-BFF/infra/aws && docker compose up -d
   ```

Si RabbitMQ estuvo caído, bookings envía solo los avisos que quedaron
pendientes en `OUTBOX_RABBIT` apenas vuelve el broker. Los mensajes que
terminaron en una DLQ se reenvían desde el panel RabbitMQ de la web (botón
**Reprocesar**).
