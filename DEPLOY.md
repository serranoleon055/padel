# Despliegue — RankPadel (MVP)

Guía de producción. **Backend + MySQL en Railway**, **frontend en Vercel**.
No contiene secretos: los valores reales se cargan como variables de entorno en cada
plataforma.

---

## 1. Backend (Railway)

1. Crear un proyecto en Railway y añadir el plugin **MySQL**.
2. Desplegar este repo (Railway detecta el `Dockerfile` automáticamente).
3. Añadir un **Volume** persistente montado en `/data/uploads` (si no, las imágenes
   subidas se borran en cada redeploy).
4. Configurar las variables de entorno (Settings → Variables):

| Variable | Obligatoria | Ejemplo / Nota |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | sí | `prod` (ya viene por defecto en el Dockerfile) |
| `DB_URL` | sí | `jdbc:mysql://<host>:<port>/<db>?useSSL=true&requireSSL=true&serverTimezone=America/Argentina/Buenos_Aires` |
| `DB_USERNAME` | sí | usuario del MySQL de Railway |
| `DB_PASSWORD` | sí | password del MySQL de Railway |
| `JWT_SECRET` | sí | base64 de ≥256 bits (ver más abajo cómo generarlo) |
| `ADMIN_INITIAL_PASSWORD` | sí | contraseña fuerte del admin (mín. 8 caracteres) |
| `ADMIN_USERNAME` | no | default `admin` |
| `APP_CORS_ALLOWED_ORIGINS` | sí | URL del frontend, p. ej. `https://rankpadel.vercel.app` (varias separadas por coma) |
| `UPLOAD_DIR` | sí | `/data/uploads` (ruta del volumen) |
| `UPLOAD_PUBLIC_BASE_URL` | no | default `/uploads` |
| `JWT_EXPIRATION_MS` | no | default `86400000` (24 h) |
| `LOGIN_MAX_ATTEMPTS` | no | default `10` |
| `LOGIN_WINDOW_SECONDS` | no | default `60` |

> **Generar `JWT_SECRET`:** `openssl rand -base64 48`
> (en Windows con Node: `node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"`).
> Nunca lo guardes en el repo.

5. Mapear el puerto: Railway inyecta `PORT` y la app ya lo respeta
   (`server.port=${PORT:8080}`).

### Primer arranque
- Flyway aplica las migraciones.
- `AdminBootstrap` (perfil `prod`) detecta que el admin tiene la contraseña por defecto
  sembrada y la reemplaza por `ADMIN_INITIAL_PASSWORD`. La credencial pública
  (`admin` / `password`) deja de funcionar.
- Entrá una vez al panel y, si querés, cambiá la contraseña desde **Usuarios admin**
  (a partir de ahí el bootstrap ya no la toca).

---

## 2. Frontend (Vercel)

1. Importar el repo del frontend en Vercel (framework: **Vite**).
2. Variable de entorno:

| Variable | Valor |
|---|---|
| `VITE_API_BASE_URL` | URL pública del backend en Railway, p. ej. `https://rankpadel-api.up.railway.app` |

3. El `vercel.json` ya incluye el rewrite SPA para que funcionen los deep links de
   react-router (recargar en `/ranking`, `/admin/...`).
4. Build command: `npm run build` · Output: `dist` (defaults de Vite).

---

## 3. Checklist de verificación post-deploy

- [ ] Login con `ADMIN_INITIAL_PASSWORD`. La credencial `admin/password` **no** funciona.
- [ ] El frontend (dominio Vercel) llama al backend **sin** errores de CORS.
- [ ] Deep links: recargar en `/ranking` y `/admin/torneos` no da 404.
- [ ] Subir una foto de jugador → redeploy del backend → la imagen **sigue** disponible.
- [ ] `https://<backend>/swagger-ui.html` devuelve 404 (Swagger off en prod).
- [ ] `https://<backend>/actuator/health` responde `{"status":"UP"}`.
- [ ] Forzar >10 logins fallidos seguidos → HTTP 429.
- [ ] Todo bajo HTTPS, sin contenido mixto.

---

## 4. Desarrollo local (sin cambios)

- Backend: `./mvnw spring-boot:run` (perfil por defecto, usa MySQL local y el secreto de dev).
- Frontend: `npm run dev` (proxy a `http://localhost:8080`).
