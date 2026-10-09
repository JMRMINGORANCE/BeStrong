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

## Fase 3 — Estadísticas y motivación (en curso)
- [ ] Niveles de fuerza (1RM relativo al peso corporal)
- [ ] Equilibrio empuje/tirón y pierna/torso con aviso de descompensación
- [ ] Resumen semanal el domingo (notificación + tarjeta)
- [ ] Línea de tiempo de récords
- [ ] Logros y retos personales con predicción de fecha
- [ ] Curva de fuerza por rango de reps; calculadora de 1RM con varias fórmulas y tabla de porcentajes
- [ ] Volumen efectivo según RPE/RIR
- [ ] Puntuación DOTS / Wilks / IPF GL; intentos de competición
- [ ] Informe mensual (PDF/imagen)
- [ ] Comparar fotos de progreso (locales, con bloqueo por huella)
- [ ] Gráficas de todas las medidas corporales y relación peso/fuerza
- [ ] Días desde el último entreno por músculo y agujetas en el mapa muscular

## Fase 4 — Reloj y Fold
- [ ] Registrar series por voz en el reloj
- [ ] Resumen al acabar con comparación (reloj)
- [ ] Esfera propia (Watch Face Format) con racha, «Hoy toca» y pulso
- [ ] Vibraciones distintas por tipo de aviso; AOD más rico; modo bajo consumo
- [ ] Zonas de pulso: sugerir la siguiente serie cuando baja el pulso
- [ ] Gráfica del ejercicio recorrida con la corona; últimos 3 pesos como accesos rápidos
- [ ] Crear y editar rutinas en el reloj (modo sin móvil completo)
- [ ] Sesión de cardio libre con GPS del Ultra
- [ ] Velocidad de la barra y RIR estimado con el acelerómetro; contador de reps calibrable
- [ ] Fold: modo mesa con temporizador gigante, vista de dos columnas en el entreno, arrastrar y soltar, ventanas múltiples
- [ ] Notificación de bloqueo con botones, burbuja flotante y picture-in-picture del descanso
- [ ] Bloqueo de toques durante el entreno; control con botones de volumen

## Fase 5 — Planificación avanzada
- [ ] Editor de programas propios (semanas, porcentajes, progresión)
- [ ] Mesociclos con descarga automática por fatiga
- [ ] Periodización ondulante (DUP)
- [ ] Más programas: Texas Method, Madcow 5×5, nSuns, PHUL, PHAT, calistenia, powerlifting
- [ ] Preparación para una fecha (pico y descarga)
- [ ] Generador de rutinas por días, material y objetivo
- [ ] Calendario arrastrable; duplicar semana; tiempo estimado y aviso si no da tiempo
- [ ] Progresiones de calistenia e isométricos por tiempo
- [ ] Bloques de cardio, HIIT/EMOM/AMRAP, calentamiento y estiramientos guiados
- [ ] Avisos de lesión (ocultar ejercicios que cargan una zona) y límite de progresión semanal

## Fase 6 — Salud, datos e integraciones
- [ ] Peso corporal desde Health Connect
- [ ] Preparación del día con sueño y pulso en reposo (Health Connect)
- [ ] Proteína diaria y agua durante el entreno
- [ ] Copia de seguridad cifrada con contraseña; borrar datos selectivamente; exportar por fechas
- [ ] Exportar a Google Sheets/Excel; formato JSON documentado
- [ ] Importar desde JEFIT y FitNotes
- [ ] Calendario del sistema; atajos de la app; mosaico de ajustes rápidos
- [ ] Rutina por enlace/QR, exportar programa a PDF, tarjeta de récord, Quick Share
- [ ] Modo pareja en el mismo móvil; modo clase/circuito
- [ ] Strava / Google Fit 🔑, controles de Spotify/YouTube Music 🔑, avisos por Galaxy Buds 🔑, Bixby Routines / geovalla 🔑

## Fase 7 — Accesibilidad, idiomas y personalización
- [ ] Más idiomas (catalán, gallego, portugués, francés, alemán)
- [ ] Alto contraste, modo daltónico con patrones, fuente para dislexia, vibraciones personalizables
- [ ] Leer en voz alta la siguiente serie
- [ ] Temas de color y negro AMOLED; emoji por rutina; unidades mixtas; columnas visibles a elegir
- [ ] Glosario, consejos contextuales, preguntas frecuentes, tutorial interactivo, novedades por versión

## Fase 8 — Ingeniería
- [ ] Capturas con comparación contra referencia (también inglés y modo oscuro)
- [ ] Separar la prueba del emulador en su propia ejecución
- [ ] Tests de sincronización con reloj simulado
- [ ] Recuperación ante cierres y conflictos de sincronización visibles
- [ ] Rendimiento: historial de 1000 entrenos, índices y paginación en Room, consumo de batería del reloj
- [ ] Modularizar por funciones; configuration cache de Gradle; detekt; tamaño del APK
- [ ] Firma y release automáticos; canal beta; web en GitHub Pages
- [ ] Revisión de seguridad (permisos mínimos, FileProvider, exportaciones)
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
