# Despliegue — RankPadel

Guía de producción. **Backend + MySQL en Railway**, **frontend en Cloudflare Pages**,
**backups a Cloudflare R2**.

No contiene secretos: los valores reales se cargan como variables de entorno en cada
plataforma.

> Un proyecto de Railway **por cliente**: los datos de un club nunca comparten base
> con los de otro.

> **Para la demo pública no hace falta nada de esto**: se puede levantar entera en planes
> gratuitos. Ver [§0](#0-demo-p%C3%BAblica-en-planes-gratuitos). Lo de abajo es lo que va
> cuando entra un cliente pagando.

---

## 0. Demo pública en planes gratuitos

La instancia que se le muestra a un cliente antes de venderle. Cero costo, y a propósito
en servicios distintos de los de producción: si la demo se cae un domingo no pasa nada, y
no comparte base con ningún club real.

| Pieza | Servicio | Plan | Límite que importa |
|---|---|---|---|
| Frontend | **Cloudflare Pages** | Gratis | Permite uso comercial (Vercel Hobby **no**) |
| Backend | **Render** (Docker) | Gratis | 512 MB de RAM, 750 h/mes, **se duerme a los 15 min** |
| Base MySQL | **Aiven** | Gratis | 1 GB de datos, 1 GB de RAM, sin tarjeta |
| Fotos | **Cloudinary** | Gratis | Obligatorio: Render free **no tiene disco persistente** |
| Visitas | **Cloudflare Web Analytics** | Gratis | Sin cookies → no hace falta cartel de consentimiento |
| Despertador | **UptimeRobot** | Gratis | Un chequeo cada 5 min a `/actuator/health` |

### Orden de armado

1. **Base (Aiven).** Crear un servicio MySQL free. De la pantalla de conexión salen host,
   puerto, usuario, contraseña y nombre de la base. Aiven **exige TLS**, así que la URL va
   con SSL y con el huso horario fijado (si no, las fechas se corren):

   ```
   jdbc:mysql://HOST:PUERTO/defaultdb?sslMode=REQUIRED&serverTimezone=America/Argentina/Buenos_Aires&characterEncoding=UTF-8
   ```

   No hay que crear ninguna tabla: **Flyway arma el esquema solo** en el primer arranque.

2. **Backend (Render).** `New` → `Blueprint` apuntando al repo: toma el `render.yaml` que
   está en la raíz. Después, en Environment, cargar los valores marcados `sync:false`:
   `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_INITIAL_PASSWORD`,
   `APP_CORS_ALLOWED_ORIGINS` (la URL del front, sin barra final) y las tres de Cloudinary.
   `JWT_SECRET` lo genera Render solo.

   El blueprint ya deja puestas `PAGOS_MODO_DEMO=true` y `PAGOS_DEMO_PUBLICA=true`. **Las
   dos hacen falta**: con la primera sola, `SecretsGuard` aborta el arranque a propósito
   (aprobar pagos solos contra una base remota, en un cliente real, sería regalar turnos).

3. **Frontend (Cloudflare Pages).** Conectar el repo del front. Build `npm run build`,
   salida `dist`, variable `VITE_API_BASE_URL` = la URL de Render. Los archivos `_headers`
   (CSP) y `_redirects` (deep links del SPA) ya están en `public/`.

4. **Visitas.** En Cloudflare → Web Analytics → habilitarlo para el sitio de Pages. El
   beacon lo inyecta Pages solo; el CSP de `_headers` ya lo permite. Si alguna vez las
   visitas dan cero, lo primero a mirar es que `static.cloudflareinsights.com` siga en el
   `script-src`.

5. **Despertador.** UptimeRobot → monitor HTTP(s) a `https://TU-BACKEND.onrender.com/actuator/health`
   cada 5 minutos. Sin esto, la primera visita del día espera un minuto en blanco, que es
   exactamente lo que no puede pasar cuando el cliente entra desde el celular. 750 h/mes
   alcanzan justo para un servicio despierto todo el mes (730 h), pero **solo uno**: si se
   levanta una segunda demo en la misma cuenta, las dos se suspenden a fin de mes.

### Lo que hay que saber de esta demo

- **Es una demo, y se dice.** Los pagos están simulados (`PAGOS_MODO_DEMO`): nadie cobra
  ni paga nada de verdad.
- **La base es chica.** 1 GB alcanza de sobra para datos de muestra; no es donde va un
  club real.
- **Sin backups.** El workflow de backup apunta a la base de producción. Si la demo se
  pierde, se vuelve a sembrar.
- **Cambiar la URL en dos lugares más** cuando el dominio de la demo cambie: el
  `og:url`/`og:image` de `index.html` del front y la página final de `Propuesta-fuente.html`.

---

## 1. Backend (Railway)

1. Crear un proyecto y añadir el plugin **MySQL**.
2. `+ New` → **GitHub Repo** → el repo del backend. Railway detecta el `Dockerfile`.
3. Imágenes: **Cloudinary** (recomendado). Si no se usa, hace falta un **Volume**
   persistente montado en `/data/uploads`, o las fotos se borran en cada redeploy.
4. Variables de entorno (Settings → Variables):

| Variable | Obligatoria | Ejemplo / Nota |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | sí | `prod` (ya viene por defecto en el Dockerfile) |
| `DB_URL` | sí | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=true&serverTimezone=America/Argentina/Buenos_Aires` |
| `DB_USERNAME` | sí | `${{MySQL.MYSQLUSER}}` |
| `DB_PASSWORD` | sí | `${{MySQL.MYSQLPASSWORD}}` |
| `JWT_SECRET` | sí | base64 de ≥256 bits (ver abajo) |
| `ADMIN_INITIAL_PASSWORD` | sí | contraseña fuerte (mín. 8; el panel exige 10) |
| `ADMIN_USERNAME` | no | default `admin` |
| `APP_CORS_ALLOWED_ORIGINS` | sí | dominio del front, p. ej. `https://omapadel.com.ar` (varios con coma) |
| `JAVA_TOOL_OPTIONS` | recomendado | `-Xmx384m` — baja la factura de Railway |
| `CLOUDINARY_CLOUD_NAME` / `_API_KEY` / `_API_SECRET` | recomendado | si se definen, las fotos van a Cloudinary |
| `UPLOAD_DIR` | solo sin Cloudinary | `/data/uploads` (ruta del volumen) |
| `MERCADO_PAGO_BACK_URL_BASE` | si hay pagos | dominio del front |
| `MERCADO_PAGO_NOTIFICATION_URL` | si hay pagos | `https://<backend>/api/pagos/webhook` |
| `MERCADO_PAGO_WEBHOOK_SECRET` | si hay pagos | secret del panel de MP → Webhooks |
| `PAGOS_MODO_DEMO` | — | **`false` en cualquier cliente real** (ver §6) |
| `PAGOS_EXPIRACION_MINUTOS` | no | default `30` — ventana para pagar la seña |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | recomendado | SMTP para avisar al club (ver §5) |
| `NOTIFICACIONES_REMITENTE` | con mail | dirección desde la que salen los avisos |
| `NOTIFICACIONES_DESTINO` | no | fallback si la sede no tiene mail cargado |
| `JWT_EXPIRATION_MS` | no | default `86400000` (24 h) |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_WINDOW_SECONDS` | no | default `10` / `60` |
| `PUBLIC_WRITE_MAX_ATTEMPTS` / `PUBLIC_WRITE_WINDOW_SECONDS` | no | default `20` / `60` |

> **Generar `JWT_SECRET`:** `openssl rand -base64 48`
> (Windows con Node: `node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"`).
> Nunca lo guardes en el repo.

> **El `serverTimezone` de la `DB_URL` no es opcional.** Si falta, gana el timezone del
> servidor de Railway y las fechas de turnos y torneos corren un día.

5. Railway inyecta `PORT` y la app ya lo respeta (`server.port=${PORT:8080}`).
6. Settings → Networking → **Generate Domain**, y después el dominio propio (§4).

### Primer arranque

- Flyway aplica las migraciones (**última: V41**).
- `AdminBootstrap` (perfil `prod`) reemplaza la contraseña sembrada por
  `ADMIN_INITIAL_PASSWORD`. La credencial pública deja de funcionar.
- `SecretsGuard` **aborta el arranque** si detecta la clave JWT de dev contra una base
  remota, o `PAGOS_MODO_DEMO=true` sin `PAGOS_DEMO_PUBLICA=true`.
- La base arranca vacía: el club carga lugar, canchas, horarios, categorías y temporada.
  **Nunca reutilizar la base de la demo para un cliente.**

---

## 2. Frontend (Cloudflare Pages)

> Vercel Hobby **prohíbe el uso comercial**: los fronts de clientes van a Cloudflare Pages.

1. Workers & Pages → Create → **Pages** → Connect to Git → repo del frontend.
2. Framework preset **Vite** · Build command `npm run build` · Output directory `dist`.
3. Variable de entorno: `VITE_API_BASE_URL` = URL pública del backend.
4. `public/_redirects` y `public/_headers` se aplican solos (SPA + CSP). **Sin
   `_redirects`, recargar en `/ranking` o `/admin/torneos` da 404.**

---

## 3. Backups (Cloudflare R2)

El workflow `.github/workflows/backup-db.yml` hace un dump diario a las 03:00 ART con
retención de 30 días.

1. Cloudflare → R2 → Create bucket (p. ej. `rankpadel-backups`).
2. Manage R2 API Tokens → token con permiso de escritura.
3. GitHub → Settings → Secrets and variables → Actions. Cargar:
   `DB_HOST`, `DB_PORT` (los del **proxy público** de Railway, no los internos),
   `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
   `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`.
4. Actions → "Backup diario de MySQL" → **Run workflow** para probarlo a mano.

### Restaurar (ensayarlo, no solo leerlo)

```bash
gunzip -c rankpadel-AAAA-MM-DD.sql.gz | mysql -h <host> -P <port> -u <user> -p <db>
```

**Un backup que nunca se restauró no es un backup.** Ensayar el restore contra una base
descartable al menos una vez, y anotar la fecha en `OPERACIONES.md`.

---

## 4. Dominio

En Cloudflare DNS, con el dominio del club:

- `omapadel.com.ar` → CNAME al proyecto de Pages
- `api.omapadel.com.ar` → CNAME al dominio de Railway

Después actualizar `APP_CORS_ALLOWED_ORIGINS`, `MERCADO_PAGO_BACK_URL_BASE` y
`MERCADO_PAGO_NOTIFICATION_URL` con el dominio final.

---

## 5. Notificaciones por mail

Sin SMTP configurado el sistema funciona igual, pero **el club solo se entera de una
solicitud si mira el panel**. Con `MAIL_HOST` + `NOTIFICACIONES_REMITENTE` cargados, se
avisa por mail cuando entra un turno, entra una inscripción, y cuando se cobra una seña
sin turno disponible.

El destinatario es el mail cargado en **Configuración de sede**; si no hay ninguno, cae
en `NOTIFICACIONES_DESTINO`.

---

## 6. Mercado Pago

- Token de producción **de la cuenta del club**, cargado desde el panel
  (Configuración de sede). Nunca se devuelve en los GET. Hay fallback a
  `MERCADO_PAGO_ACCESS_TOKEN` por env.
- Cargar `MERCADO_PAGO_WEBHOOK_SECRET`: sin él la firma del webhook no se valida.
- La preferencia caduca junto con la reserva y **excluye los medios de pago offline**
  (Rapipago / Pago Fácil / cajero), que se pagan hasta 3 días después: para entonces el
  turno ya venció y quedaría cobrado sin cancha.
- Si aun así un pago entra tarde, el pago queda en `APROBADO_SIN_TURNO`, se loguea en
  ERROR, aparece en el panel como **"Devolver seña"** y se manda un mail al club.
- `PAGOS_MODO_DEMO=true` aprueba los pagos sin cobrar. Sirve **solo** para la instancia
  de demostración, que además debe declarar `PAGOS_DEMO_PUBLICA=true`. En un cliente
  real, `SecretsGuard` corta el arranque.

---

## 7. Monitoreo

- **UptimeRobot** (free): HTTP a `https://<backend>/actuator/health` cada 5 min, alerta al mail.
- **Sentry** (free): un proyecto Java (backend) y uno React (front).
- Logs: Railway → servicio → Logs.

---

## 8. Checklist de verificación post-deploy

- [ ] Login con `ADMIN_INITIAL_PASSWORD`. La credencial sembrada **no** funciona.
- [ ] El front llama al backend **sin** errores de CORS.
- [ ] Deep links: recargar en `/ranking` y `/admin/torneos` no da 404.
- [ ] Subir una foto de jugador → redeploy del backend → la imagen **sigue** disponible.
- [ ] `https://<backend>/swagger-ui.html` devuelve 404 (Swagger off en prod).
- [ ] `https://<backend>/actuator/health` responde `{"status":"UP"}`.
- [ ] Forzar >10 logins fallidos seguidos → HTTP 429.
- [ ] Todo bajo HTTPS, sin contenido mixto.
- [ ] **Un pago real de punta a punta** (monto chico) → la reserva queda CONFIRMADA. Devolverlo desde MP.
- [ ] Una solicitud de turno dispara el mail al club.
- [ ] El workflow de backup corrió y **el restore se ensayó**.
- [ ] Las fechas no corren un día (crear un turno para mañana y verificarlo).

---

## 9. Desarrollo local

- Backend: `./mvnw spring-boot:run` (perfil por defecto, MySQL local, secreto de dev).
- Frontend: `npm run dev` (proxy a `http://localhost:8080`).
- **Una migración nueva exige reiniciar el backend** (`ddl-auto=validate`).
