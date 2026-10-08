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
- **Descanso en la notificación** (cuenta atrás, +15 s, saltar) que sigue funcionando con la pantalla apagada; aparece en la pantalla de bloqueo.
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
| `wear` | Compose for Wear OS, Health Services, Tiles (ProtoLayout), Complications, Ongoing Activity, Data Layer. |

Sin frameworks de DI: un `AppContainer` / `WearContainer` manual.

## Compilar

Requisitos: Android Studio Ladybug o posterior, JDK 17, Android SDK 35.

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
- Lint en ambos módulos.

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

## Pendiente (necesita cuentas o servicios externos)
- Cuenta, copia en la nube y parte social: hay que elegir backend (Firebase o Supabase).
- Informes de errores (Crashlytics): necesita un proyecto de Firebase con `google-services.json`.
