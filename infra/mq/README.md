# Topología RabbitMQ de CampusLab

`definitions.json` declara los exchanges, colas y bindings que usan
ms-campuslab-bookings y ms-campuslab-catalog (productores) y
ms-campuslab-notify (consumidor). Coincide con lo que declara
`RabbitMqConfig` de notify, así que da igual quién arranque primero.

```
cmd.direct --email.send---> q.cmd.email   --nack--> cmd.dlx --> q.cmd.email.dlq
cmd.direct --prep.ticket--> q.cmd.prep    --nack--> cmd.dlx --> q.cmd.prep.dlq
cmd.direct --voucher.gen--> q.cmd.voucher --nack--> cmd.dlx --> q.cmd.voucher.dlq
cmd.topic  --email.*------> q.cmd.email   (alerta de stock agotado de catalog)
```

## Importar en el broker

El archivo **no incluye usuarios** a propósito (no se versionan
credenciales). Por eso se importa en un broker que ya está corriendo; así se
conservan los usuarios existentes:

```bash
curl -u admin:<password> -X POST \
  -H "content-type: application/json" \
  --data @definitions.json \
  http://<host>:15672/api/definitions
```

> Si una cola ya existe con **otros argumentos** (por ejemplo, otro
> `x-dead-letter-exchange`), RabbitMQ rechaza la importación de esa cola. En
> ese caso hay que eliminarla (vacía) y volver a importar, o ajustar este
> archivo y `campuslab.rabbitmq.*` de notify para que coincidan.
