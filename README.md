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

### Galaxy Watch (Wear OS)
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

## Pendiente (necesita backend)
- Cuenta y copia en la nube.
- Parte social (feed, seguir a amigos, compartir rutinas).

Para ambas hay que elegir backend (p. ej. Firebase o Supabase) y crear el proyecto correspondiente.
