# BeStrong 🏋️

App de entrenamiento de fuerza **estilo Hevy** para **Android + Wear OS**, optimizada para
**Samsung Galaxy Z Fold 8** y **Galaxy Watch Ultra**. Funciona 100 % sin conexión.

## Funcionalidades

### Móvil
- **Entrenamiento en curso** con tabla de series (peso × reps, solo reps o tiempo), columna
  **"Anterior"** (lo que hiciste la última vez; tócala para copiarla), tipos de serie
  (calentamiento / normal / drop / al fallo), notas por ejercicio, reordenar y quitar ejercicios,
  deslizar para borrar series.
- **Temporizador de descanso automático** al completar cada serie (±15 s, saltar, vibración al acabar).
- **Biblioteca de ~70 ejercicios** en español, búsqueda sin tildes, filtros por músculo y equipo,
  ejercicios personalizados y recientes primero.
- **Rutinas**: crear, editar, reordenar, duplicar, empezar con un toque (los pesos se rellenan con tu
  última sesión), guardar un entrenamiento como rutina y **empezar una rutina en el reloj** desde el móvil.
- **Historial** con calendario mensual, detalle de cada sesión, repetir entrenamiento.
- **Progreso por ejercicio**: gráficas de 1RM estimado (Epley), peso máximo, volumen y reps,
  y **récords personales** (se celebran al terminar el entreno).
- **Programas predefinidos** (Full Body 3 días, Push / Pull / Legs, Torso / Pierna 4 días, 5×5 Fuerza,
  Glúteo y pierna): un toque crea todas sus rutinas.
- **Programas con progresión automática**: **5/3/1 Boring But Big** (ciclos de 4 semanas sobre tu training max, que sube
  solo al acabar cada ciclo) y **GZCLP** (T1/T2/T3 con progresión lineal y descarga al 90 % tras dos fallos).
- **«Hoy toca»** en Inicio con las cargas ya calculadas (también llega al reloj), o la rutina que lleva más tiempo sin hacerse.
- **Semana planificada vs hecha**: días entrenados L–D, sesiones frente al objetivo y constancia de las últimas semanas.
- **Ficha de ejercicio** con imagen de **wger.de** (licencia abierta, con autor y licencia visibles) y **notas de técnica
  propias con foto o vídeo** (se guardan solo en el móvil).
- **Tempo por ejercicio** (p. ej. 3-1-1-0) con tiempo bajo tensión.
- **Mapa muscular semanal**: series efectivas por músculo frente a la recomendación de 10–20 por semana (bajo / óptimo / alto).
- **Compartir entreno como imagen** (tarjeta 4:5 con cifras, ejercicios y récords) desde el resumen o el historial.
- **Recordatorios** los días elegidos a la hora elegida (solo si ese día no has entrenado; sugiere la rutina que toca).
- **Notificación de récords** cuando terminas un entreno en el reloj.
- **Copias de seguridad**: semanal (CSV + JSON) en la carpeta que elijas (p. ej. Google Drive), **restaurar** desde el JSON
  (añade lo que falte, no borra nada) y Android Auto Backup de la base de datos.
- **Perfil**: racha semanal, volumen semanal, entrenos por semana, series por músculo (30 días), mejores 1RM.
- **Medidas corporales** (peso, % grasa, cintura, pecho, brazo, muslo) con gráfica de peso.
- **Health Connect** (opcional): cada entreno se guarda como sesión de pesas → aparece en **Samsung Health**.

### Entrenar mejor
- **Sobrecarga progresiva automática** (doble progresión): si completaste todas las series, propone +2,5 kg / +5 lb;
  si te quedaste corto, mismas cargas buscando las reps; con RPE ≥ 9,5 propone repetir.
- **Descarga automática** al 90 % cuando llevas 3 sesiones sin mejorar el 1RM estimado.
- **Superseries y circuitos**: el descanso salta entre ejercicios del grupo y llega al cerrar la ronda.
- **Calentamiento automático** (barra × 10, 40 % × 5, 60 % × 3, 80 % × 2) y **calculadora de discos** (kg o lb, barra de 20/15/10 kg o 45/35/25 lb).
- **RPE** por serie, **kg o lb**, e **instrucciones de técnica** para los ~70 ejercicios.
- **Descanso en la notificación** (cuenta atrás, +15 s, saltar) que sigue funcionando con la pantalla apagada; en Android 16
  es un **Live Update** (chip en la barra de estado y **Now Bar** de One UI 8) con barra de progreso por ejercicio.
- **Widget** con racha, entrenos de la semana, "Entrenar" y tus dos primeras rutinas.
- **CSV**: exporta tu historial e importa desde **Hevy**, **Strong** o BeStrong.

### Galaxy Watch (Wear OS)
- **Entreno compartido en vivo con el móvil**: empieza en uno y sigue en el otro; las series aparecen en ambos y el pulso del reloj se ve en el Fold.
- **Botón rápido** del Watch Ultra (y botones físicos) para completar la serie sin tocar la pantalla.
- **Modo siempre activo**: pantalla de bajo consumo con la serie actual y la hora de fin del descanso.
- **Contador de repeticiones (beta)** con el acelerómetro.
- **Standalone**: rutinas sincronizadas desde el móvil, entrenamiento vacío y selector de ejercicios en el reloj.
- **Bisel/corona** para ajustar peso (±2,5 kg) o reps; toca el número para cambiar de campo.
- **Health Services** (`ExerciseType.WEIGHTLIFTING`): pulso en directo, media/máximo y calorías.
- **Servicio en primer plano + Ongoing Activity**: el entreno sigue con la pantalla apagada y aparece en la esfera.
- Pantalla de **descanso** con anillo por el borde, aviso háptico a 10 s y al terminar.
- **Progreso y discos**: «la última vez», mejor 1RM estimado con su evolución y discos por lado para la serie actual.
- **Calentamiento automático** y **guía de tempo con vibraciones** (un toque largo al bajar, corto en las pausas, doble al subir).
- La sesión de **«Hoy toca»** del programa llega como primera rutina, con sus cargas.
- **Tile** (entrenos de la semana, racha, botón Entrenar) y **complicación** de esfera (entrenos/objetivo semanal).
- Al terminar, el entrenamiento viaja al móvil por la **Wearable Data Layer** (aunque estén desconectados en ese momento).

## Optimización para Galaxy Z Fold 8
| Postura | Comportamiento |
|---|---|
| Plegado (pantalla exterior) | Barra de navegación inferior, una columna. |
| Desplegado (pantalla interior) | Navigation rail; Historial y Ejercicios en **lista + detalle** lado a lado; en el entreno, panel lateral con resumen, siguiente serie y descanso grande. |
| **Modo mesa (Flex mode)** | Mitad superior: descanso / resumen visible desde el banco. Mitad inferior: series. |

Además: rejillas adaptables (1–3 columnas), `resizeableActivity` y `configChanges` para no perder
el estado al plegar/desplegar.

## Arquitectura

| Módulo | Contenido |
|---|---|
| `core` (Kotlin/JVM puro) | Modelos, biblioteca de ejercicios, `WorkoutEditor` (edición inmutable del entreno), `WorkoutStats` (récords, progreso, rachas, músculos), contrato de sincronización. Con tests unitarios. |
| `mobile` | Jetpack Compose + Material 3 adaptativo, Room, Navigation, Health Connect, Data Layer. |
| `designsystem` | Paleta de marca y espaciados compartidos por móvil y reloj. |
| `wear` | Compose for Wear OS, Health Services, Tiles (ProtoLayout), Complications, Ongoing Activity, Data Layer. |

- **Hilt** para la inyección de dependencias en móvil y reloj (ViewModels `@HiltViewModel`, servicios `@AndroidEntryPoint`, widget vía `EntryPoint`).
- **Wear Compose Material 3** en el reloj (`AppScaffold`, `ScreenScaffold`, `EdgeButton`, `SwitchButton`).
- **Idiomas**: español e inglés (`values/` y `values-en/`), incluidos nombres y técnica de los ejercicios.
- **Sincronización en vivo móvil ↔ reloj** con fusión serie a serie (`WorkoutMerge`): si editas en los dos a la vez no se pierde ninguna serie completada.
- **Formato persistente versionado** (`Persistence`, `schemaVersion` + migraciones) en Room y en el reloj.
- **Baseline Profiles** (`:baselineprofile`): el workflow *Baseline Profile* los genera en un emulador y los publica en la rama `baseline-profile`.

## Compilar

Requisitos: Android Studio Narwhal o posterior, JDK 17, Android SDK 36.

```bash
./gradlew :core:test                 # tests de lógica
./gradlew :mobile:installDebug       # móvil (emulador "Resizable" o Pixel Fold para probar el Fold)
./gradlew :wear:installDebug         # reloj (emulador "Wear OS Large Round")
```

> Ambos módulos comparten `applicationId = "com.bestrong"`, requisito de la Data Layer.
> Para emparejar emuladores móvil + reloj usa el *Wear OS pairing assistant* de Android Studio.

GitHub Actions compila los dos APK en cada push (`.github/workflows/android.yml`) y los deja como artefacto.

## Calidad
- `core`: tests unitarios de estadísticas, editor, progresión, superseries, discos, calentamiento y CSV.
- `mobile`: tests de interfaz en JVM (Robolectric) con **capturas Roborazzi** del Fold plegado, desplegado y en modo mesa;
  la CI las publica como artefacto `capturas`.
- Accesibilidad: capturas con el texto al 200 % y comprobación de que todo lo pulsable tiene etiqueta para TalkBack.
- Reloj: capturas de la esfera redonda (inicio, entreno, serie, descanso).
- Build release con R8 en cada push y prueba de humo minificada en emulador (workflow *Baseline Profile*).
- Estilo: **ktlint** vía Spotless (`./gradlew spotlessCheck` / `spotlessApply`), reglas en `.editorconfig`.
- Tests de **Room** (en memoria) y de los **Workers** (recordatorios y copias) en Robolectric.
- **Tiempo de arranque** medido con Macrobenchmark en el workflow *Baseline Profile*; falla si empeora más de un 25 %.
- Lint en ambos módulos y **Dependabot** (versiones menores y parches) para dependencias y acciones.
- `tools/wger_media.py` + workflow *Exercise media*: cruza la biblioteca con wger y publica el resultado para revisarlo.

## Publicar
Crea `keystore.properties` en la raíz (no se sube a git):
```
storeFile=ruta/al/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```
y ejecuta `./gradlew :mobile:bundleRelease :wear:bundleRelease`. La política de privacidad está en la app
(Perfil › Privacidad), requisito de Health Connect.

## Pendiente
- Firebase: cuenta, copia en la nube, parte social e informes de errores (Crashlytics).
- Imágenes de ejercicios: wger cubre ~40 de 70; para el resto (y animaciones) haría falta contenido propio o con licencia.
- Publicación en Play Store: icono definitivo, onboarding, capturas de la ficha y formulario de seguridad de datos.
