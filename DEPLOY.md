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

### El orden importa (hay una dependencia circular)

El backend necesita la URL del front (para el CORS) y el front necesita la URL del backend
(para pegarle a la API). Ninguno de los dos existe antes de crearse. La salida es:

```
Cloudinary → Aiven → Render (con el CORS provisorio) → Pages → volver a Render y corregir el CORS
```

Saltear el último paso es el error clásico: el sitio carga, pero **cualquier pantalla que
pida datos queda vacía** y en la consola del navegador aparece un error de CORS.

---

### Paso 0 — Cloudinary (2 minutos, va primero)

Sin esto, las fotos que se suban (logos de sponsors, galería de la sede, fotos de
jugadores) se guardan en el disco del contenedor, y el plan gratuito de Render **no tiene
disco persistente**: se borran en cada deploy y cada vez que el servicio se duerme.

1. Entrar a [cloudinary.com](https://cloudinary.com) → **Console**.
2. En la barra lateral: **Settings** (el engranaje) → **API Keys**.
   En consolas más viejas está en la pantalla de inicio, en la tarjeta
   **Product Environment Credentials** / **Account Details**.
3. Anotar tres valores:
   - **Cloud name** → `CLOUDINARY_CLOUD_NAME`
   - **API Key** → `CLOUDINARY_API_KEY`
   - **API Secret** (hay que tocar "mostrar"; solo lo ve un usuario con rol Admin) →
     `CLOUDINARY_API_SECRET`

---

### Paso 1 — La base (Aiven, MySQL gratis)

1. [console.aiven.io](https://console.aiven.io) → dentro del proyecto, barra lateral
   **Services** → botón **Create service**.
2. Elegir **MySQL**.
3. **Service tier**: el gratuito (*Free*). Ojo: el tier gratis **limita las regiones y los
   proveedores** disponibles, así que elegilo ANTES que el cloud, o no vas a ver el plan
   free en la lista.
4. **Cloud provider** y región: la que ofrezca el free (suele ser una sola).
5. **Plan**: el free (1 GB de datos, 1 GB de RAM).
6. **Service details** → nombre: `rankpadel-demo`.
7. **Create service**. Queda en estado **Rebuilding** unos minutos; hay que esperar a
   **Running**.
8. Ya en **Running**: pantalla **Overview** del servicio → botón **Quick connect**. Ahí
   salen **Host**, **Port**, **User**, **Password** y **Database name** (en Aiven la base
   por defecto se llama `defaultdb`).

Con eso se arma la URL de conexión. **Aiven exige TLS** y el huso horario hay que fijarlo
o las fechas se corren un día:

```
jdbc:mysql://HOST:PUERTO/defaultdb?sslMode=REQUIRED&serverTimezone=America/Argentina/Buenos_Aires&characterEncoding=UTF-8
```

No hay que crear ninguna tabla ni correr ningún SQL: **Flyway arma el esquema solo** en el
primer arranque del backend (las 54 migraciones).

> **Si Aiven no deja crear la cuenta**: probar con otro mail (sirve `tumail+demo@gmail.com`)
> o en una ventana de incógnito. Si igual falla, la alternativa es TiDB Cloud Serverless,
> que habla el protocolo de MySQL — pero antes hay que correr las 54 migraciones contra él
> y confirmar que pasan: es *compatible* con MySQL, no *es* MySQL.

---

### Paso 2 — El backend (Render)

1. [dashboard.render.com](https://dashboard.render.com) → botón **New** (arriba a la
   derecha) → **Blueprint**.
2. En la lista de repos, **Connect** en `serranoleon055/padel`. Si no aparece, hay que
   darle permiso a Render sobre el repo desde GitHub.
3. **Blueprint name**: `rankpadel-demo`. **Branch**: `main`.
   El campo **Blueprint Path** se deja como está: el `render.yaml` ya está en la raíz.
4. Render lee el archivo y muestra los cambios que va a aplicar, con un formulario para
   las variables marcadas `sync: false`. Cargar ahí (o después, ver punto 6):

   | Variable | Valor |
   |---|---|
   | `DB_URL` | la URL JDBC del paso 1, entera |
   | `DB_USERNAME` | el **User** de Aiven (suele ser `avnadmin`) |
   | `DB_PASSWORD` | el **Password** de Aiven |
   | `ADMIN_INITIAL_PASSWORD` | la que quieras, **mínimo 10 caracteres** |
   | `APP_CORS_ALLOWED_ORIGINS` | provisorio: `https://rankpadel-demo.pages.dev` |
   | `CLOUDINARY_CLOUD_NAME` / `_API_KEY` / `_API_SECRET` | los del paso 0 |

   `JWT_SECRET` **no se toca**: el blueprint le pone `generateValue: true` y Render genera
   uno fuerte solo.

5. **Deploy Blueprint**. El primer build tarda bastante (compila el proyecto con Maven
   adentro de Docker). Se sigue en la pestaña **Logs** del servicio.
6. Si algo quedó sin cargar: servicio `rankpadel-demo` → barra lateral **Environment** →
   **Add environment variable** → **Save changes** (guardar dispara un redeploy solo).
7. Cuando termine, arriba del servicio aparece la URL: `https://rankpadel-demo.onrender.com`.
   **Anotarla.** Probarla entrando a `https://rankpadel-demo.onrender.com/actuator/health`:
   tiene que responder `{"status":"UP"}`.

**Qué mirar si no arranca** (pestaña Logs):

- `SecretsGuard` abortando → falta una variable o el JWT es de desarrollo.
- `ADMIN_INITIAL_PASSWORD es obligatoria en producción` → no la cargaste, o tiene menos de
  10 caracteres. Es a propósito: sin eso el admin quedaría con la contraseña de desarrollo.
- Error de conexión a la base → revisar que la URL tenga `sslMode=REQUIRED`.
- El contenedor muere apenas arranca → problema de memoria. El `Dockerfile` ya calcula el
  heap como porcentaje de la RAM justamente por los 512 MB del plan free.

El blueprint deja fijas `PAGOS_MODO_DEMO=true` y `PAGOS_DEMO_PUBLICA=true`. **Las dos hacen
falta**: con la primera sola, `SecretsGuard` aborta a propósito. En modo demo los pagos se
aprueban solos, y eso contra la base de un cliente real sería regalar turnos.

---

### Paso 3 — El frontend (Cloudflare Pages)

**Intentar primero con Git** (lo normal). Si funciona, listo:

1. [dash.cloudflare.com](https://dash.cloudflare.com) → barra lateral **Workers & Pages**.
2. **Create application** → pestaña **Pages** → **Connect to Git**.
3. Autorizar GitHub → elegir `serranoleon055/padel-front` → **Install & Authorize** →
   **Begin setup**.
4. Configuración:

   | Campo | Valor |
   |---|---|
   | **Project name** | el que sea (define el dominio `<nombre>.pages.dev`) |
   | **Production branch** | `main` |
   | **Framework preset** | `Vite` |
   | **Build command** | `npm run build` |
   | **Build output directory** | `dist` |
   | **Root directory** | se deja vacío |

5. Desplegar **Environment variables (advanced)** y agregar:
   `VITE_API_BASE_URL` = la URL del backend de Render (**sin barra al final**).
   Es de build, no de runtime: si se cambia después hay que volver a desplegar.
6. **Save and Deploy**.

Los archivos `public/_headers` (CSP y headers de seguridad) y `public/_redirects` (para que
los enlaces profundos del SPA no den 404) ya están en el repo: Pages los toma solo.

> **Si el build de Cloudflare se cuelga o falla sin motivo claro** (visto en vivo el
> 2026-08-12: el log se corta en seco justo después de `Executing user build command`, dos
> veces seguidas, mismo punto exacto — no es un error de TypeScript ni de dependencias, se
> descartó clonando el repo limpio y corriendo el mismo build local, que compiló sin
> problema): no perder tiempo reintentando desde el dashboard. Subir el build a mano:
>
> 1. Local: `npm ci && VITE_API_BASE_URL=https://TU-BACKEND.onrender.com npm run build`
>    (en PowerShell: `$env:VITE_API_BASE_URL="..."; npm run build`).
> 2. Cloudflare → **Workers & Pages** → **Create application** → **Pages** →
>    pestaña **Upload assets** → arrastrar la carpeta `dist/`.
> 3. Cada cambio futuro del front repite estos dos pasos — no hay redeploy automático
>    en este modo. Si se retoma el Git deploy más adelante, el primer sospechoso a
>    revisar es el caché de build de Cloudflare (Settings → Builds → borrar caché):
>    persiste `node_modules`/artefactos entre corridas y es candidato a la causa.

---

### Paso 4 — Cerrar el círculo del CORS

Con el dominio real de Pages a la vista, volver a **Render** → `rankpadel-demo` →
**Environment** → editar `APP_CORS_ALLOWED_ORIGINS` con la URL exacta que quedó
(`https://rankpadel-demo.pages.dev`, **sin barra final**) → **Save changes**.

Se verifica entrando al sitio y abriendo cualquier pantalla con datos (Ranking o Turnos).
Si sigue vacía, mirar la consola del navegador: un error que diga *CORS policy* significa
que la URL cargada no coincide **exactamente** con la del navegador (ojo con `http` vs
`https` y con la barra final).

> **Guardar una env var en Render dispara un redeploy**, y durante esos minutos el backend
> devuelve **502**. El navegador lo reporta como error de CORS (sin respuesta, tampoco hay
> cabecera `Access-Control-Allow-Origin`), lo que confunde: parece que el CORS sigue mal
> cuando en realidad el servicio está reiniciando. Esperar a que `/actuator/health` vuelva
> a responder `{"status":"UP"}` antes de tocar nada más.

---

### Paso 5 — El contador de visitas

1. Cloudflare → **Workers & Pages** → el proyecto `rankpadel-demo`.
2. Pestaña **Metrics** → en el bloque **Web Analytics**, botón **Enable**.
3. El beacon se instala solo **en el deploy siguiente**: hay que volver a desplegar
   (**Deployments** → menú del último deploy → **Retry deployment**) o esperar al próximo
   push.

Las visitas se leen después en Cloudflare → **Web Analytics**.

**Si marca cero visitas**, en orden:

1. Que `static.cloudflareinsights.com` siga en el `script-src` del `_headers`. Si el
   navegador lo bloquea por CSP, no se cuenta nada y no salta ningún error visible.
2. Que el sitio no mande `Cache-Control: public, no-transform` — con ese header Cloudflare
   no puede inyectar el script. Hoy no lo mandamos.
3. Que hayas vuelto a desplegar después de activarlo.

---

### Paso 6 — El despertador

El plan gratuito de Render duerme el servicio a los **15 minutos sin tráfico** y tarda
cerca de **un minuto** en despertar. Eso, con un cliente mirando el celular, no sirve.

1. [uptimerobot.com](https://uptimerobot.com) → crear un monitor nuevo.
2. Tipo **HTTP(s)**.
3. URL: `https://rankpadel-demo.onrender.com/actuator/health`
4. Intervalo: **5 minutos**.
5. Contacto de alerta: tu mail.

> **Solo una demo por cuenta de Render.** El plan free da 750 horas de instancia por mes y
> un servicio despierto todo el mes consume 730. Si levantás una segunda, las dos se
> suspenden antes de fin de mes.

---

### Paso 7 — Cargar los datos

1. Entrar a `https://rankpadel-demo.pages.dev/admin` con `admin` y la
   `ADMIN_INITIAL_PASSWORD` del paso 2.
2. Crear la sede y las canchas (**Sedes y canchas**) y, en cada cancha, el **horario de
   atención** (Configuración de sede). Sin horario cargado el sembrador no encuentra
   ningún hueco y no siembra nada.
3. Desde la máquina de desarrollo:

   ```
   .\scripts\sembrar-demo.ps1 -Api "https://rankpadel-demo.onrender.com" -Clave "TU-PASSWORD"
   ```

4. Revisar Panel, Caja y Estadísticas: tienen que verse cargados.

### Paso 8 — Actualizar la URL en los dos lugares que quedan

La URL vieja de la demo está escrita en:

- `index.html` del front: `og:url` y `og:image` (es lo que se ve al compartir el enlace por
  WhatsApp).
- `Propuesta-fuente.html`, última página — y después **regenerar el PDF**.

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
