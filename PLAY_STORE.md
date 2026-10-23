# Publicar BeStrong en Google Play

Igual que MiDinero: cada compilación de `main` que pasa la CI («Android CI») sube sola los .aab del móvil y del reloj a
Google Play, ya publicados para los testers de la prueba cerrada y con las notas de
`fastlane/metadata/android/*/changelogs/default.txt` (actualízalas antes de cada versión).

- Ficha en Play Console: **BeStrong**, paquete **`com.copidev.bestrong`** (`com.bestrong` ya estaba cogido en Play).
  La esfera es otra app: `com.copidev.bestrong.watchface` (no se sube a Play por ahora).
- Móvil → canal `alpha` («Prueba cerrada - Alpha»). Reloj → canal de Wear OS «Reloj - prueba cerrada».
- Testers: el mismo grupo que MiDinero, `midinero-testers@googlegroups.com`.
- `versionCode` = número de ejecución de «Android CI» (el del reloj, +100000: Play exige números distintos en cada bundle).

## Secretos del repositorio (una sola vez)

GitHub → *Settings → Secrets and variables → Actions*. Sin ellos el trabajo «play» se salta con un aviso.

| Secreto | Valor |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 upload.jks` (clave de subida) |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | los de esa clave |
| `PLAY_SERVICE_ACCOUNT_JSON` | el .json de la cuenta de servicio (la misma que usa MiDinero) |

Puedes usar la misma clave de subida que MiDinero (los mismos valores que sus secretos `MIDINERO_KEYSTORE_*`) o crear una
nueva: `keytool -genkeypair -v -keystore upload.jks -alias bestrong -keyalg RSA -keysize 4096 -validity 10000`.
Deja activada la **firma de apps de Google Play**.

Los mismos secretos `KEYSTORE_*` los usa `release.yml` para firmar los APK de las releases de GitHub. Esos APK no se
instalan encima de la versión de Play (Google la firma con su clave).

## Cuenta de servicio

La de MiDinero sirve: Play Console → *Usuarios y permisos* → la cuenta `…@….iam.gserviceaccount.com` →
*Permisos de la aplicación* → añade **BeStrong** con «Publicar en canales de prueba» y «Ver información de la aplicación».

## Primera versión

Mientras la app está en borrador, Play solo acepta versiones en borrador: la primera subida automática queda como
borrador en la prueba cerrada y hay que **enviarla a revisión a mano** (después de completar *Contenido de la aplicación*:
política de privacidad, seguridad de los datos, clasificación, público objetivo, permisos de salud). Las siguientes salen solas.

Si los canales cambian de nombre, ponlos en *Variables* del repositorio: `PLAY_TRACK` (móvil) y `PLAY_WEAR_TRACK` (reloj).
Si un nombre no existe, el paso falla y muestra la lista de canales que hay.
