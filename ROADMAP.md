# Hoja de ruta de BeStrong

Todas las mejoras propuestas, ordenadas por fases. Cada fase se valida en la CI (tests, capturas,
build de release con R8, lint) antes de empezar la siguiente.

Leyenda: `[x]` hecho · `[ ]` pendiente · 🔑 necesita una cuenta, clave o decisión del propietario.

Fuera de esta hoja (se harán aparte): Firebase (cuenta, nube, social, Crashlytics) y publicación en Play Store.

---

## Fase 1 — Entrenar con menos toques ✅
- [x] Descanso automático según el tipo de ejercicio (básicos pesados 3 min, aislamiento 90 s), aprendiendo de tus ajustes
- [x] Comparación en vivo con la vez anterior en cada serie (+2,5 kg / +1 rep)
- [x] Autorregulación por RPE (RPE 10 → baja carga; RPE 7 → sube)
- [x] Tile de «Hoy toca» en el reloj
- [x] Botón de acción del Galaxy Watch Ultra configurable (completar serie / saltar descanso / siguiente ejercicio)
- [x] Recuperar exactamente el entreno y el descanso si Android cierra la app
- [x] Aviso de entreno olvidado (más de 3 h abierto)
- [x] Deshacer al borrar series, ejercicios o entrenos
- [x] Modo una mano en la pantalla exterior del Fold
- [x] Pausar el entreno
- [x] Cambiar de ejercicio sin perder la progresión (alternativa equivalente)
- [x] Perfiles de gimnasio (casa / gimnasio / hotel) con su material, discos y mancuernas disponibles
- [x] Botón «Invítame a un café» 🔑 (listo; falta tu enlace de Ko-fi/PayPal en `donationUrl`) y pantalla «Acerca de» con créditos y licencias
- [x] Releases en GitHub con APK y changelog automáticos al crear una etiqueta de versión

## Fase 2 — Registro más rico e historial ✅
- [x] Notas por serie
- [x] Ejercicios unilaterales (registro por lado y aviso de descompensación)
- [x] Ejercicios asistidos o lastrados (peso negativo/positivo)
- [x] Ejercicios por distancia o tiempo + distancia
- [x] Tipos de serie: clusters / rest-pause, parciales, negativas; marcar series con ayuda
- [x] Peso por mancuerna o total
- [x] Saltar un ejercicio y volver; lista «pendiente» con tiempo estimado; aviso de descanso excesivo
- [x] Valoración de la sesión (energía, dolor, motivación) y comparativa con la sesión anterior
- [x] Sugerencia para la próxima vez al terminar
- [x] Buscar y filtrar el historial; editar entrenos pasados con recálculo
- [x] Mapa de calor anual de días entrenados
- [x] Caché de imágenes y GIF para el gimnasio sin cobertura
- [x] Teclado numérico propio con ±2,5 / ±1, autocompletar pesos, gestos de deslizar
- [x] Búsqueda de ejercicios por sinónimos y por voz; ejercicios favoritos; filtro «sin material»
- [x] Variantes enlazadas (historial y récords juntos o separados)

## Fase 3 — Estadísticas y motivación ✅
- [x] Niveles de fuerza (1RM relativo al peso corporal)
- [x] Equilibrio empuje/tirón y pierna/torso con aviso de descompensación
- [x] Resumen semanal el domingo (notificación + tarjeta)
- [x] Línea de tiempo de récords
- [x] Logros y retos personales con predicción de fecha
- [x] Curva de fuerza por rango de reps; calculadora de 1RM con varias fórmulas y tabla de porcentajes
- [x] Volumen efectivo según RPE/RIR
- [x] Puntuación DOTS / Wilks / IPF GL; intentos de competición
- [x] Informe mensual (PDF/imagen)
- [x] Comparar fotos de progreso (locales, con bloqueo por huella)
- [x] Gráficas de todas las medidas corporales y relación peso/fuerza
- [x] Días desde el último entreno por músculo y agujetas en el mapa muscular

## Fase 4 — Reloj y Fold ✅
- [x] Registrar series por voz en el reloj (español e inglés: «80 kilos 8», «ochenta por ocho», «82 y medio por 5 RPE 8»)
- [x] Resumen al acabar con comparación (reloj)
- [x] Esfera propia (Watch Face Format, módulo `:watchface`) con «Hoy toca», entrenos de la semana, pulso y aro de pasos
- [x] Vibraciones distintas por tipo de aviso; AOD con pulso y series; modo bajo consumo
- [x] Zonas de pulso: aviso cuando el pulso baja lo suficiente para la siguiente serie
- [x] Gráfica del ejercicio recorrida con la corona; últimos 3 pesos como accesos rápidos
- [x] Crear rutinas en el reloj (se envían al móvil)
- [x] Sesión de cardio libre con GPS del Ultra (carrera, caminata, bici)
- [x] Velocidad de la barra y RIR estimado con el acelerómetro; contador de reps calibrable
- [x] Fold: cuenta atrás gigante en modo mesa, vista de dos paneles al desplegar, arrastrar para reordenar, arrastrar un entreno a otra app en pantalla dividida
- [x] Notificación de bloqueo con botones (completar serie, +15 s, saltar) y descanso en ventana flotante (picture-in-picture)
  - Burbuja flotante descartada: Android solo permite burbujas para conversaciones; el PiP cubre ese uso
- [x] Bloqueo de toques durante el entreno; control con botones de volumen

## Fase 5 — Planificación avanzada ✅
- [x] Editor de programas propios (días, ejercicios, progresión lineal, % fijo del TM o 5/3/1)
- [x] Mesociclos con descarga automática y detector de fatiga (estancamiento, RPE, energía y dolor)
- [x] Periodización ondulante (DUP)
- [x] Más programas: Texas Method, Madcow 5×5, nSuns, PHUL, PHAT, calistenia, powerlifting
- [x] Preparación para una fecha (pico y descarga, con pesos para tus básicos)
- [x] Generador de rutinas por días, material y objetivo
- [x] Calendario arrastrable; duplicar semana; tiempo estimado y aviso si no da tiempo
- [x] Progresiones de calistenia e isométricos por tiempo
- [x] Temporizadores HIIT/Tabata/EMOM/AMRAP, calentamiento y estiramientos guiados (cardio libre con GPS en el reloj)
- [x] Avisos de lesión (oculta ejercicios que cargan una zona) y límite de progresión semanal

## Fase 6 — Salud, datos e integraciones ✅
- [x] Peso corporal desde Health Connect
- [x] Preparación del día con sueño y pulso en reposo (Health Connect)
- [x] Proteína diaria y agua durante el entreno
- [x] Copia de seguridad cifrada con contraseña; borrar datos selectivamente; exportar por fechas
- [x] Exportar a Excel (abre en Google Sheets); formato JSON documentado
- [x] Importar desde JEFIT y FitNotes
- [x] Calendario del sistema (.ics); atajos de la app (también para Bixby Routines); mosaico de ajustes rápidos
- [x] Rutina por enlace/QR, exportar programa a PDF, tarjeta de récord, Quick Share
- [x] Modo pareja en el mismo móvil; modo clase/circuito
- [x] Controles de música para Apple Music, Spotify, YouTube Music o cualquier reproductor (teclas multimedia, sin cuentas) y botón para abrir la app elegida y avisos por voz por los auriculares (Galaxy Buds)
- [ ] Strava / Google Fit 🔑, API de Spotify 🔑, geovalla del gimnasio 🔑 — necesitan claves o cuentas del propietario

## Fase 7 — Accesibilidad, idiomas y personalización ✅
- [x] Más idiomas (catalán, gallego, portugués, francés, alemán) en móvil, reloj y esfera; idioma por app en Android 13+
- [x] Alto contraste, modo daltónico con símbolos, letra muy legible (Atkinson Hyperlegible) y para dislexia (OpenDyslexic), vibraciones a elegir (también en el reloj)
- [x] Leer en voz alta la siguiente serie
- [x] Temas de color (acento o el del fondo de pantalla) y negro AMOLED; emoji por rutina; unidades mixtas (cuerpo y cargas); columnas visibles a elegir
- [x] Glosario, consejos contextuales, preguntas frecuentes, tutorial inicial y novedades por versión

## Fase 8 — Ingeniería ✅ (salvo lo que necesita cuentas 🔑)
- [x] Capturas con comparación contra la referencia (resumen de la CI con imágenes de diferencias), en inglés y en modo oscuro
- [x] Prueba del emulador en su propia ejecución (Baseline Profile, manual)
- [x] Tests de sincronización con reloj simulado (200 secuencias aleatorias, sin perder series)
- [x] Conflictos de sincronización visibles (aviso en el entreno)
- [x] Rendimiento: historial de 1000 entrenos (prueba de tiempo), índices en Room
- [x] Módulo `:data` (Room, repositorio y ajustes separados de la interfaz); configuration cache, caché de compilación y tareas en paralelo en Gradle; detekt en la CI con línea base
- [x] Pantallas en módulos por función (`:feature:workout`, `routines`, `history`, `exercises`, `home`, `planning`, `profile`) sobre `:shared` (componentes, tema, textos y servicios); lint revisa todos los módulos
- [x] Tamaño del APK en cada CI y en cada release
- [x] Release automática al etiquetar; canal beta (etiquetas -beta/-rc); web en GitHub Pages (activar «Source: GitHub Actions»)
- [ ] Firma definitiva 🔑 (secretos KEYSTORE_*)
- [x] Revisión de seguridad: enlaces de rutina con límites (evita bombas zip), FileProvider sin exportar, servicios protegidos por permiso
- [ ] Pruebas en dispositivos reales con Firebase Test Lab 🔑

---

## Ya hecho
- [x] Entreno en vivo móvil ↔ reloj con fusión serie a serie; persistencia versionada
- [x] Programas predefinidos; 5/3/1 BBB y GZCLP con progresión; «Hoy toca»; semana planificada vs hecha
- [x] Mapa muscular semanal; compartir entreno como imagen; recordatorios; notificación de récords
- [x] Copia semanal (CSV + JSON), restaurar copia, Android Auto Backup
- [x] Reloj: progreso y discos, calentamiento, guía de tempo con vibraciones
- [x] Imágenes de ExerciseDB y wger con atribución; notas de técnica con foto/vídeo
- [x] Android 16 Live Updates (Now Bar); compileSdk 36; Dependabot; ktlint; accesibilidad (letra 200 %, TalkBack)
- [x] Tests de Room y Workers; medición de arranque; módulo `:designsystem`

## Fase 9 — Auditoría y mejoras del día a día ✅
- [x] Auditoría completa (lógica, entreno, reloj, datos, planificación, perfil, tiles): más de 60 fallos corregidos con tests de regresión
- [x] «Actualizar rutina» al terminar un entreno que cambió su estructura
- [x] Incremento de peso por ejercicio (botones ±, progresión, «la próxima vez», bisel del reloj)
- [x] Nota fija por ejercicio visible durante el entreno; notas del entreno durante la sesión; notas de serie en el historial
- [x] Registrar un entreno pasado y cambiar fecha, hora y duración; añadir series al editar
- [x] Editor de rutinas con tipos de serie, notas, tempo, cualquier descanso y aviso de cambios sin guardar
- [x] Editar ejercicios personalizados (músculos secundarios, unilateral, distancia)
- [x] Cuenta atrás para series por tiempo
- [x] Deshacer al borrar una rutina; confirmación al borrar rutinas y ejercicios personalizados

## Fase 10 — Rendimiento y organización ✅
- [x] Historial grande: decodificación incremental, estadísticas fuera del hilo principal, récords en una sola pasada
- [x] Reloj: historial que siempre cabe en la Data Layer y archivo propio para el entreno activo
- [x] Copias automáticas con rotación (8 últimas) y sin duplicados; borrado masivo en una consulta
- [x] Discos por lado en la tarjeta del ejercicio con barra
- [x] Biblioteca ordenable (recientes, A–Z, más usados)
- [x] Carpetas de rutinas plegables en Inicio
- [x] Nota fija del ejercicio también en el reloj
- [x] «Próxima vez» en la ficha de cada ejercicio

## Fase 11 — Más detalle en cada serie ✅
- [x] Descanso corto (máx. 60 s) tras las series de calentamiento
- [x] Récord en vivo (🏆) al marcar una serie que supera tu mejor 1RM estimado o peso máximo
- [x] Reloj: deshacer la última serie y quitar una serie pendiente
- [x] Health Connect: cada serie como segmento con repeticiones (Samsung Health / Fit)
- [x] Carga de entrenamiento semanal (RPE × minutos) con aviso de subida brusca (relación aguda:crónica > 1,5)

## Fase 12 — Seguridad y comodidad ✅
- [x] Auditoría de seguridad: restauración de ajustes a prueba de copias dañadas, fotos bloqueadas al salir y sin miniatura,
  CSV sin fórmulas ejecutables, copia cifrada con contraseña repetida (10+), copia en la nube solo cifrada
- [x] Calendario del historial: tocar un día filtra sus entrenos
- [x] Copia cifrada: «archivo dañado» distinto de «contraseña incorrecta»
- [x] Añadir drop set (−20 %) desde el menú del ejercicio
- [x] Calculadora de discos al revés y «Usar este peso»
- [x] Reloj: aviso de récord al marcar la serie

## Fase 13 — Detalle de progreso ✅
- [x] Ficha del ejercicio: mejores marcas por repeticiones (1RM, 3RM, 5RM…) y notas de cada sesión
- [x] Inicio: repetir el último entreno con un toque
- [x] Pruebas de interfaz de las novedades (drop set, registro de entrenos pasados)
- [x] Resumen semanal con aviso de carga alta
- [x] Peso corporal: media de 7 días y cambio frente a la semana anterior

## Fase 14 — Menos sorpresas ✅
- [x] Inicio: duración estimada de cada rutina
- [x] Health Connect: peso y % de grasa de Medidas
- [x] Biblioteca: última vez y mejor serie en cada ejercicio
- [x] Deshacer «Descartar entreno»
- [x] Descanso tras calentamiento configurable (también en el reloj)

## Fase 15 — Más contexto al entrenar ✅
- [x] Excel con hoja de Medidas
- [x] Mancuernas más cercanas si el peso no existe en el gimnasio
- [x] Nota de la serie de la última vez mientras está pendiente
- [x] Series por músculo de las últimas 8 semanas
- [x] Entreno medio (duración, series y volumen) en Perfil
