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

## Fase 16 — Detalles de entrenador ✅
- [x] Sin descanso antes de un drop set
- [x] Aviso de objetivo semanal cumplido en el resumen
- [x] Compartir la semana como texto para el entrenador
- [x] Reloj: duración estimada de cada rutina
- [x] Apuntar el peso de hoy desde Inicio

## Fase 17 — Ritmo de la sesión ✅
- [x] «Copiar a las siguientes» en el menú de cada serie
- [x] Hora estimada de fin junto al tiempo restante (móvil y reloj)
- [x] Historial: descanso real medio frente al previsto
- [x] Tendencia por músculo: media semanal frente al rango habitual de 10–20 series
- [x] Informe al entrenador con unidad, total de mancuernas y decimales coherentes

## Fase 18 — Control del tiempo ✅
- [x] Hora límite del entreno: aviso en rojo si la estimación se pasa
- [x] Marcar de golpe las series que faltan de un ejercicio
- [x] Duplicar una serie justo debajo
- [x] Historial: descanso real frente al previsto en cada ejercicio
- [x] Inicio: músculos que esta semana van por detrás de lo habitual (desde el jueves)

## Fase 19 — Llegar a tiempo ✅
- [x] «Ajustar a mi hora límite»: quita series del final hasta que quepa (con deshacer)
- [x] Historial: llevar el descanso real de un ejercicio a su rutina
- [x] Inicio: tocar un músculo atrasado abre la biblioteca filtrada
- [x] Notificación del entreno con tiempo restante y hora de fin
- [x] Resumen final con descanso real frente al previsto

## Fase 20 — Biblioteca y rutinas a tu medida ✅
- [x] Reps necesarias para batir tu récord en la siguiente serie
- [x] Compartir un entreno del historial como texto
- [x] Ocultar ejercicios de la biblioteca (y volver a mostrarlos)
- [x] Editor de rutinas: series por músculo y duplicar ejercicio

## Fase 21 — Más allá del entreno ✅
- [x] Reloj: reps que batirían tu récord en la serie actual
- [x] Exportar a CSV el historial de un ejercicio
- [x] «Tu año»: entrenos, volumen, récords, mejor racha, músculo estrella y mayores subidas (compartible)
- [x] Tutorial con página final de configuración (unidad y días por semana)
- [x] Widget «Músculos esta semana»

## Fase 22 — Metas y orden ✅
- [x] Widget de músculos: tocar un músculo abre sus ejercicios
- [x] Archivar rutinas (sin borrarlas; fuera de Inicio, reloj y widget)
- [x] Objetivo de 1RM por ejercicio con progreso y fecha estimada
- [x] Celebración al alcanzar el objetivo durante el entreno
- [x] Tendencia de esfuerzo (RPE) por ejercicio con aviso de fatiga

## Fase 23 — Metas para todo ✅
- [x] Gráficas con resumen para TalkBack (inicio, final, máximo y tendencia)
- [x] Peso corporal objetivo (para bajar o subir) con fecha estimada
- [x] Propuesta de archivar rutinas sin usar en 60 días
- [x] Objetivos de repeticiones o tiempo en ejercicios sin peso o isométricos
- [x] Mínimo semanal de series por músculo configurable (mapa, Inicio y widget)

## Fase 24 — Herramientas de entrenador ✅
- [x] Series de descarga (−10 %) desde el menú del ejercicio
- [x] Historial del ejercicio sin salir del entreno
- [x] Barra propia por ejercicio (EZ, hexagonal…) para discos y calentamiento
- [x] Recordatorio semanal para pesarse
- [x] Doble progresión con rango de repeticiones (p. ej. 8–12)

## Fase 25 — Fuera del gimnasio ✅
- [x] Reloj: drop set y series de descarga; calentamiento con la barra del gimnasio o del ejercicio
- [x] Apuntar el peso respondiendo a la notificación de pesarse
- [x] «Tu mejor hora»: rendimiento según la hora a la que entrenas
- [x] Compartir el progreso corporal (peso, objetivo y medidas)
- [x] Añadir una sesión planificada al calendario del móvil

## Fase 26 — Contexto y cuerpo ✅
- [x] Rango de repeticiones visible durante el entreno (móvil y reloj)
- [x] Recordatorio de entrenar con «Empezar» y «Dentro de 1 h»
- [x] Composición corporal: cintura/altura y FFMI
- [x] Aviso si el peso cambia más de un 1 % por semana
- [x] «Tus mejores días» de la semana

## Fase 27 — Planificar y cumplir ✅
- [x] Rango de repeticiones desde la ficha del ejercicio y para toda una rutina
- [x] Peso objetivo con fecha: ritmo semanal necesario y aviso si pasa del 1 %
- [x] Aviso en el entreno si el gimnasio activo no tiene el material (con «Sustituir»)
- [x] Sesión planificada saltada: hacerla hoy, pasarla a mañana o dejarla
- [x] Recordatorio mensual de medidas con cinta

## Fase 28 — Memoria y precisión ✅
- [x] «Hace un año»: tus pesos de entonces comparados con los de ahora (Inicio)
- [x] Peso de los cierres en el perfil del gimnasio (discos, calentamiento y sugerencias)
- [x] RPE objetivo por ejercicio en la rutina (móvil y reloj)
- [x] Cintura objetivo con lo que falta
- [x] El reloj redondea la sugerencia a los discos y mancuernas del gimnasio activo

## Fase 29 — Objetivos que se cumplen ✅
- [x] RPE medio frente al objetivo en el resumen del entreno
- [x] RPE objetivo y tempo en el PDF de la rutina
- [x] «Hace un año»: tocar un ejercicio abre su ficha y su progreso
- [x] Cintura objetivo con fecha estimada según la tendencia
- [x] RPE objetivo en los programas propios

## Fase 30 — Entrenar por RPE ✅
- [x] RPE objetivo para toda la rutina de una vez
- [x] Peso orientativo para el RPE objetivo en el entreno (móvil y reloj)
- [x] Tabla de cargas por RPE en la ficha del ejercicio
- [x] RPE objetivo y tempo en el CSV (exportar e importar)
- [x] Aviso cuando el tope semanal recorta la sugerencia

## Fase 31 — RPE en la mano ✅
- [x] Tocar el peso orientativo del RPE lo pone en las series pendientes (móvil) o en la serie actual (reloj)
- [x] El 1RM reciente cuenta las reps en recámara de las series con RPE
- [x] Tempo para toda la rutina de una vez
- [x] RPE de la última serie desde el menú del reloj

## Fase 32 — Coherencia y atajos ✅
- [x] Historial: cada ejercicio frente a la vez anterior (volumen y 1RM)
- [x] 1RM con las reps en recámara del RPE también en el historial y en «frente a la última vez» (revisado en la fase 33: ahí se usa el 1RM de siempre, como en récords y gráficas; el del RPE queda para el peso orientativo)
- [x] Widget: la rutina planificada para hoy primero y la que más tiempo lleva sin hacerse
- [x] El RPE anotado en el reloj propone en el móvil el mismo ajuste de series
- [x] Los avisos descartados con fecha se olvidan a los 60 días

## Fase 33 — Copias, reloj y primeros pasos ✅
- [x] Al restaurar una copia: qué trae (entrenos, fechas, rutinas, medidas) y si restaurar también los ajustes
- [x] Aviso en Perfil si la carpeta de copias se pierde o la última copia tiene más de dos semanas
- [x] Complicación «Descanso» con la cuenta atrás en cualquier esfera
- [x] Configuración inicial: «¿Dónde entrenas?» (gimnasio o casa) elige el material
- [x] Test de traducciones: las 7 lenguas con las mismas claves y argumentos
- [x] Tempo explosivo (X): el reloj también avisa de subir

## Fase 34 — Que nada se pare sin avisar ✅
- [x] Notificación si la copia automática se para (carpeta perdida o dos semanas fallando)
- [x] Al restaurar: cuántos entrenos son nuevos y aviso si la copia es más antigua que tu último entreno
- [x] Historial: comparación también en ejercicios sin peso (reps) y de tiempo
- [x] Cambiar de gimnasio desde el entreno (menú) y desde Inicio
- [x] Resumen del reloj: reps o tiempo frente a la última vez en ejercicios sin peso

## Fase 35 — Cada gimnasio en su sitio ✅
- [x] Cada entreno guarda el gimnasio donde se hizo: se ve en el historial
- [x] Buscar en el historial por gimnasio
- [x] Gimnasio por defecto de cada rutina (al empezarla pasa a ser el activo)
- [x] «Copiar ahora» en el aviso de copia automática fallida
- [x] Cardio y distancia frente a la vez anterior (km, o tiempo si no hay distancia), en el móvil y el reloj

## Fase 36 — Ritmo y sitio ✅
- [x] Ritmo (min/km) en las series de cardio con tiempo y distancia
- [x] Historial filtrado por gimnasio
- [x] El gimnasio de cada rutina en su tarjeta de Inicio
- [x] «Copiar ahora» en Perfil, diciendo si salió bien
- [x] Al rellenar y sugerir, lo último hecho en el mismo gimnasio (si allí ya se hizo el ejercicio)

## Fase 37 — Cardio y pareja ✅
- [x] Ritmo según el cardio: /500 m en remo, km/h en bici y min/km en el resto
- [x] Mejores marcas de cardio en la ficha: distancia, tiempo y mejor ritmo (desde 1 km)
- [x] Restaurar la última copia automática sin buscar el archivo
- [x] Modo pareja: su entreno empieza con sus pesos (su historial), no con los tuyos
- [x] El texto para compartir un entreno lleva el gimnasio y el ritmo del cardio

## Fase 38 — Corregir y elegir ✅
- [x] Elegir qué copia automática restaurar (la más reciente primero)
- [x] Totales de cardio de los últimos 7 y 30 días en la ficha
- [x] Cardio de la semana en el resumen semanal
- [x] Corregir el gimnasio de un entreno pasado
- [x] Renombrar a la pareja actualiza su historial (se guarda al terminar de escribir)

## Fase 39 — Cardio con objetivo ✅
- [x] Objetivo semanal de km de cardio con su progreso en Inicio
- [x] Gráfica de cardio en la ficha (distancia o tiempo por sesión)
- [x] Al elegir una copia automática, cuántos entrenos trae cada una
- [x] Deshacer el cambio de gimnasio de un entreno pasado
- [x] Buscar en el historial por el nombre de la pareja

## Fase 40 — Papelera y cardio en minutos ✅
- [x] Papelera: los entrenos borrados se guardan 30 días y se pueden recuperar desde Perfil
- [x] Objetivo semanal de cardio en minutos (75/150/300) además de km
- [x] Cardio de la semana en el widget
- [x] Filtro Yo / Pareja / Los dos en el historial
- [x] Fecha y hora de cada copia automática al elegir cuál restaurar

## Fase 41 — Papelera completa y racha de cardio ✅
- [x] Papelera: fecha de borrado, de quién es, borrar para siempre y vaciar (con confirmación)
- [x] Calendario, mapa de calor y gimnasios siguen el filtro Yo / Pareja / Los dos
- [x] Health Connect al día: borrar un entreno quita su sesión y recuperarlo la vuelve a exportar
- [x] Racha de semanas cumpliendo el objetivo de cardio
- [x] Objetivo de cardio a medida (cualquier número de km o minutos)

## Fase 42 — Calendario útil y avisos de cardio ✅
- [x] Tocar un día pasado sin entreno en el calendario para apuntar el que se hizo
- [x] Resumen de lo que se ve en el historial (entrenos y tiempo con los filtros puestos)
- [x] Papelera: días que le quedan a cada entreno y «Recuperar todos»
- [x] Aviso al cumplir el objetivo semanal de cardio (una vez por semana)
- [x] Objetivo de cardio a medida con el actual ya escrito y aviso si el número no vale

## Fase 43 — Pareja al apuntar y cardio en el reloj ✅
- [x] Apuntar un entreno pasado para ti o para tu pareja (con sus pesos de la última vez)
- [x] Complicación del reloj con el cardio de la semana frente al objetivo
- [x] Avisos de objetivos en su propio canal (se silencian sin perder los de récords)
- [x] Historial vacío por los filtros: lo dice y deja quitarlos de un toque
- [x] Exportar a CSV lo que se ve en el historial (con sus filtros)

## Fase 44 — Copiar entrenos y ritmo de cardio ✅
- [x] Copiar un entreno pasado a otro día (serie a serie)
- [x] Cuánto falta para el objetivo de cardio y a qué ritmo diario
- [x] Racha de cardio en el widget
- [x] La rutina que se está editando no se pierde si Android cierra la app
- [x] «La próxima vez» se fija en las series más pesadas (las de descarga no cuentan)

## Fase 45 — Copias con aviso y empujón de cardio ✅
- [x] «Entreno copiado» con «Abrir» al copiar a otro día
- [x] Elegir qué ejercicios se copian a otro día
- [x] Aviso el jueves si el cardio de la semana va por detrás (lo que falta y a qué ritmo)
- [x] La tile «Hoy toca» del reloj enseña el primer objetivo (ejercicio, peso y reps)
- [x] La tile «Hoy toca» enseña el cardio de la semana frente al objetivo

## Fase 46 — Explicar y elegir ✅
- [x] El resumen explica cuándo «la próxima vez» se mantiene por falta de RPE
- [x] Interruptor para el aviso de cardio del jueves (en el objetivo de cardio)
- [x] La serie top seguida de descargas cuenta para subir el peso
- [x] El borrador de una rutina borrada se guarda como rutina nueva en vez de perderse
- [x] Nombres de ejercicios en tu idioma en la tile del reloj y al copiar un entreno

## Fase 47 — Plan y estancamiento ✅
- [x] Las series guardan las reps planeadas en la rutina: «la próxima vez» compara lo hecho con el plan
- [x] Aviso de estancamiento en la ficha del ejercicio (4+ sesiones sin mejorar el 1RM estimado)
- [x] Elegir el día del aviso de cardio
- [x] Gráficas de reps y volumen de la ficha con su formato (no como peso)
- [x] Borrador de una rutina borrada: se avisa de que se guardará como nueva

## Fase 48 — Medidas fiables y plan a la vista ✅
- [x] Corregir una medida pasada (tocándola)
- [x] Medidas imposibles (grasa 185 %, cintura 0,8) se marcan y no se guardan
- [x] «Plan de la rutina: 5 reps» en cada ejercicio del entreno
- [x] Estancamiento con botón para probar otro rango de reps
- [x] Copias automáticas sin duplicados «(1)» en proveedores que no dejan renombrar
