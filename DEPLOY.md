# Despliegue — RankPadel

Guía de producción. **Backend + MySQL en Railway**, **frontend en Cloudflare Pages**,
**backups a Cloudflare R2**.

No contiene secretos: los valores reales se cargan como variables de entorno en cada
plataforma.

> Un proyecto de Railway **por cliente**: los datos de un club nunca comparten base
> con los de otro.

> **Para la demo pública tampoco hace falta nada de esto**: corre en la misma cuenta de
> Railway pero en su propio proyecto. Ver [§0](#0-demo-pública-railway--cloudflare-pages).
> Lo de abajo es lo que va cuando entra un cliente pagando.

---

## 0. Demo pública (Railway + Cloudflare Pages)

La instancia que se le muestra a un cliente antes de venderle. Vive en la **misma cuenta de
Railway** que todo lo demás, pero en **su propio proyecto**: si la demo se cae un domingo no
pasa nada, y no comparte base con ningún club real.

> **Migrada el 2026-10-08.** Antes corría en **Render** (backend) + **Aiven** (MySQL) +
> **Cloudinary** (fotos) + **UptimeRobot** (despertador), todo en planes gratuitos. **Esos
> cuatro servicios se dieron de baja.** Si encontrás una referencia a
> `rankpadel-demo.onrender.com` o a `padel-front-five.vercel.app`, está muerta: no buscar
> nada ahí.

| Pieza | Servicio | Dónde |
|---|---|---|
| Frontend | **Cloudflare Pages** | proyecto `rankpadel` → `https://rankpadel.pages.dev` |
| Backend | **Railway** | proyecto `rankpadel-demo`, servicio `backend` → `https://backend-production-9319c.up.railway.app` |
| Base MySQL | **Railway** | en el mismo proyecto |
| Fotos | **volumen de Railway** | montado en `/data/uploads`, sin Cloudinary |
| Visitas | **Cloudflare Web Analytics** | sin cookies → no hace falta cartel de consentimiento |
| Despertador | **ya no hace falta** | Railway no duerme el servicio |

### Las dos trampas de Railway

- **El volumen de fotos necesita `RAILWAY_RUN_UID=0`.** El contenedor corre como `appuser` y
  sin esa variable no puede escribir en `/data/uploads`: las fotos se suben y después dan 404.
- **`railway.json` (Config as Code) deja de funcionar el 2026-12-01.** Migrar antes con
  `railway config migrate`.

### El orden importa (hay una dependencia circular)

El backend necesita la URL del front (para el CORS) y el front necesita la URL del backend
(para pegarle a la API). Ninguno de los dos existe antes de crearse. La salida es:

```
Railway (backend + MySQL, con el CORS provisorio) -> Pages -> volver a Railway y corregir el CORS
```

Saltear el último paso es el error clásico: el sitio carga, pero **cualquier pantalla que
pida datos queda vacía** y en la consola del navegador aparece un error de CORS.

---

### Paso 1 — Backend y base (Railway)

1. [railway.app](https://railway.app) → **New Project** → nombre `rankpadel-demo`.
2. Dentro del proyecto: **New** → **Database** → **MySQL**. Railway lo provisiona y expone
   las variables de conexión.
3. El servicio del backend se sube desde la máquina de desarrollo con la CLI:

   ```
   railway up
   ```

4. Variables de entorno del servicio `backend`:

   | Variable | Valor |
   |---|---|
   | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | referenciar las del MySQL del mismo proyecto |
   | `JWT_SECRET` | BASE64 de al menos 256 bits, generado aparte |
   | `ADMIN_INITIAL_PASSWORD` | la que se usa para entrar la primera vez |
   | `APP_CORS_ALLOWED_ORIGINS` | provisorio, se corrige en el paso 3 |
   | `PAGOS_MODO_DEMO` | `true` — nadie cobra ni paga de verdad |
   | `RAILWAY_RUN_UID` | `0` — **sin esto las fotos no se guardan** |
   | `JAVA_TOOL_OPTIONS` | `-Xmx384m` para bajar la factura |

5. **Volume** → montar en `/data/uploads` (ahí van logos de sponsors, galería y fotos de
   jugadores).
6. Verificar con `/actuator/health`: tiene que devolver `{"status":"UP"}`.

> `management.health.mail.enabled=false` está puesto a propósito: sin SMTP configurado, el
> health indicator del starter de mail deja `/actuator/health` en DOWN (503) y Railway falla
> el deploy. No volver a habilitarlo.

---

### Paso 2 — Frontend (Cloudflare Pages)

**Intentar primero con Git.** `Workers & Pages` → **Create application** → **Pages** →
**Connect to Git** → `serranoleon055/padel-front`, preset `Vite`, build `npm run build`,
salida `dist`, rama `main`. En **Environment variables (advanced)**:
`VITE_API_BASE_URL` = la URL del backend de Railway (**sin barra final**) y
`VITE_SITE_URL` = el dominio que va a quedar en Pages. Las dos son **de build**: si se
cambian después, hay que volver a desplegar.

Los archivos `public/_headers` (CSP y headers de seguridad) y `public/_redirects` (para que
los enlaces profundos del SPA no den 404) ya están en el repo: Pages los toma solo.

> **Si el build de Cloudflare se cuelga o falla sin motivo claro** (pasó el 2026-08-12: el
> log se corta en seco justo después de `Executing user build command`, dos veces seguidas en
> el mismo punto exacto; se descartó problema de código clonando el repo limpio y compilando
> local sin problema), no perder tiempo reintentando desde el panel. Subir el build a mano:
>
> ```
> npm ci
> VITE_API_BASE_URL=https://TU-BACKEND.up.railway.app npm run build
> wrangler pages deploy dist --project-name rankpadel --branch main
> ```
>
> En ese modo **no hay redeploy automático**: cada cambio del front repite esos tres pasos.

---

### Paso 3 — Cerrar el círculo del CORS

Con el dominio real de Pages a la vista, volver a **Railway** → servicio `backend` →
**Variables** → editar `APP_CORS_ALLOWED_ORIGINS` con la URL exacta que quedó
(`https://rankpadel.pages.dev`, **sin barra final**).

Se verifica entrando al sitio y abriendo cualquier pantalla con datos (Ranking o Turnos). Si
sigue vacía, mirar la consola del navegador: un error que diga *CORS policy* significa que la
URL cargada no coincide **exactamente** con la del navegador (ojo con `http` vs `https` y con
la barra final).

---

### Paso 4 — El contador de visitas

1. Cloudflare → **Workers & Pages** → el proyecto `rankpadel`.
2. Pestaña **Metrics** → bloque **Web Analytics** → **Enable**.
3. El beacon se instala **en el deploy siguiente**: hay que volver a desplegar.

**Si marca cero visitas**, en orden:

1. Que `static.cloudflareinsights.com` siga en el `script-src` del `_headers`. Si el navegador
   lo bloquea por CSP no se cuenta nada y no salta ningún error visible.
2. Que el sitio no mande `Cache-Control: public, no-transform` — con ese header Cloudflare no
   puede inyectar el script. Hoy no lo mandamos.
3. Que hayas vuelto a desplegar después de activarlo.

---

### Paso 5 — Cargar los datos

1. Entrar a `https://rankpadel.pages.dev/admin` con `admin` y la `ADMIN_INITIAL_PASSWORD`.
2. Crear la sede y las canchas (**Sedes y canchas**) y, en cada cancha, el **horario de
   atención** (Configuración de sede). **Sin horario cargado el sembrador no encuentra ningún
   hueco y no siembra nada.**
3. Desde la máquina de desarrollo:

   ```
   .\scripts\sembrar-demo.ps1 -Api "https://backend-production-9319c.up.railway.app" -Clave "TU-PASSWORD"
   ```

4. Revisar Panel, Caja y Estadísticas: tienen que verse cargados.

### Paso 6 — Actualizar la URL en los dos lugares que quedan

- `index.html` del front: `og:url` y `og:image` (es lo que se ve al compartir el enlace por
  WhatsApp). El resto del SEO lo genera el build desde `VITE_SITE_URL`.
- `Propuesta-fuente.html`, última página — y después **regenerar el PDF**.

### Lo que hay que saber de esta demo

- **Es una demo, y se dice.** Los pagos están simulados (`PAGOS_MODO_DEMO`): nadie cobra ni
  paga nada de verdad.
- **No es donde va un club real.** Cada cliente pagando lleva su propio proyecto de Railway.
- **Sin backups.** El workflow de backup apunta a la base de producción. Si la demo se pierde,
  se vuelve a sembrar con el paso 5.
- Admin de la demo: `Desktop/Negocio/credenciales/rankpadel-demo.env`.

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

- **Alerta de caída**: Railway no duerme el servicio, así que no hace falta despertador. Para avisos de caída sirve cualquier monitor HTTP gratuito contra `https://<backend>/actuator/health` cada 5 min. **Hoy no hay ninguno configurado** (la cuenta de UptimeRobot se cerró el 2026-10-08).
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
