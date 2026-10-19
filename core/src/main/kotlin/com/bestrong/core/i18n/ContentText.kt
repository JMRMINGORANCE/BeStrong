package com.bestrong.core.i18n

/**
 * Textos de contenido (programas, sus días y descripciones) en el resto de idiomas de la app. En el código solo
 * existen en español e inglés; aquí van las traducciones, indexadas por el texto en español.
 */
object ContentText {
    private val LANGS = listOf("ca", "gl", "pt", "fr", "de")

    /** El texto en [lang] (código ISO); sin traducción, español para es/ca/gl/eu e inglés para el resto. */
    fun pick(es: String, en: String, lang: String): String =
        TEXTS[es]?.getOrNull(LANGS.indexOf(lang)) ?: if (lang in SPANISH_FALLBACK) es else en

    /** ¿Hay traducción de este texto a [lang]? (para los tests) */
    fun has(es: String, lang: String): Boolean = TEXTS[es]?.getOrNull(LANGS.indexOf(lang)) != null

    private val SPANISH_FALLBACK = setOf("es", "ca", "gl", "eu")

    // Orden: ca, gl, pt, fr, de
    private val TEXTS: Map<String, List<String>> = mapOf(
        // Nombres de programas
        "Full Body 3 días" to listOf("Full Body 3 dies", "Full Body 3 días", "Full Body 3 dias", "Full Body 3 jours", "Full Body 3 Tage"),
        "Torso / Pierna 4 días" to listOf("Tors / Cama 4 dies", "Torso / Perna 4 días", "Tronco / Pernas 4 dias", "Haut / Bas du corps 4 jours", "Ober-/Unterkörper 4 Tage"),
        "5×5 Fuerza" to listOf("5×5 Força", "5×5 Forza", "5×5 Força", "5×5 Force", "5×5 Kraft"),
        "Glúteo y pierna" to listOf("Glutis i cama", "Glúteo e perna", "Glúteos e pernas", "Fessiers et jambes", "Po & Beine"),
        "Calistenia 3 días" to listOf("Calistènia 3 dies", "Calistenia 3 días", "Calistenia 3 dias", "Callisthénie 3 jours", "Calisthenics 3 Tage"),
        "Powerlifting 4 días" to listOf("Powerlifting 4 dies", "Powerlifting 4 días", "Powerlifting 4 dias", "Powerlifting 4 jours", "Powerlifting 4 Tage"),
        "Ondulante (DUP) 3 días" to listOf("Ondulant (DUP) 3 dies", "Ondulante (DUP) 3 días", "Ondulatória (DUP) 3 dias", "Ondulatoire (DUP) 3 jours", "Undulierend (DUP) 3 Tage"),
        "nSuns 5/3/1 LP (4 días)" to listOf("nSuns 5/3/1 LP (4 dies)", "nSuns 5/3/1 LP (4 días)", "nSuns 5/3/1 LP (4 dias)", "nSuns 5/3/1 LP (4 jours)", "nSuns 5/3/1 LP (4 Tage)"),
        // Descripciones (programas de rutinas)
        "Cuerpo completo tres veces por semana. Ideal para empezar o con poco tiempo." to listOf(
            "Cos sencer tres cops per setmana. Ideal per començar o amb poc temps.",
            "Corpo completo tres veces por semana. Ideal para comezar ou con pouco tempo.",
            "Corpo inteiro três vezes por semana. Ideal para começar ou com pouco tempo.",
            "Corps entier trois fois par semaine. Idéal pour débuter ou quand on a peu de temps.",
            "Ganzkörper dreimal pro Woche. Ideal für den Einstieg oder bei wenig Zeit.",
        ),
        "Empuje, tirón y pierna. Repite el ciclo dos veces por semana para máxima hipertrofia." to listOf(
            "Empenta, estirada i cama. Repeteix el cicle dos cops per setmana per a la màxima hipertròfia.",
            "Empuxe, tirón e perna. Repite o ciclo dúas veces por semana para a máxima hipertrofia.",
            "Empurrar, puxar e pernas. Repete o ciclo duas vezes por semana para máxima hipertrofia.",
            "Poussée, tirage et jambes. Répète le cycle deux fois par semaine pour une hypertrophie maximale.",
            "Drücken, Ziehen und Beine. Wiederhole den Zyklus zweimal pro Woche für maximale Hypertrophie.",
        ),
        "Dos días de torso y dos de pierna, alternando fuerza e hipertrofia." to listOf(
            "Dos dies de tors i dos de cama, alternant força i hipertròfia.",
            "Dous días de torso e dous de perna, alternando forza e hipertrofia.",
            "Dois dias de tronco e dois de pernas, alternando força e hipertrofia.",
            "Deux jours haut du corps et deux jours bas du corps, en alternant force et hypertrophie.",
            "Zwei Oberkörper- und zwei Unterkörpertage, abwechselnd Kraft und Hypertrophie.",
        ),
        "Dos rutinas alternas con básicos a 5×5 (peso muerto 1×5). Sube peso cada sesión." to listOf(
            "Dues rutines alternes amb bàsics a 5×5 (pes mort 1×5). Puja el pes cada sessió.",
            "Dúas rutinas alternas con básicos a 5×5 (peso morto 1×5). Sobe peso cada sesión.",
            "Duas rotinas alternadas com exercícios básicos a 5×5 (peso morto 1×5). Aumenta o peso em cada sessão.",
            "Deux séances alternées d'exercices de base en 5×5 (soulevé de terre 1×5). Augmente la charge à chaque séance.",
            "Zwei abwechselnde Einheiten mit Grundübungen im 5×5 (Kreuzheben 1×5). Steigere das Gewicht in jeder Einheit.",
        ),
        "Dos días centrados en glúteos y piernas más uno de torso." to listOf(
            "Dos dies centrats en glutis i cames i un de tors.",
            "Dous días centrados en glúteos e pernas e un de torso.",
            "Dois dias focados em glúteos e pernas e um de tronco.",
            "Deux jours axés sur les fessiers et les jambes, plus un jour haut du corps.",
            "Zwei Tage mit Fokus auf Po und Beine plus ein Oberkörpertag.",
        ),
        "Power Hypertrophy Upper Lower: dos días de fuerza (torso y pierna) y dos de hipertrofia." to listOf(
            "Power Hypertrophy Upper Lower: dos dies de força (tors i cama) i dos d'hipertròfia.",
            "Power Hypertrophy Upper Lower: dous días de forza (torso e perna) e dous de hipertrofia.",
            "Power Hypertrophy Upper Lower: dois dias de força (tronco e pernas) e dois de hipertrofia.",
            "Power Hypertrophy Upper Lower : deux jours de force (haut et bas du corps) et deux d'hypertrophie.",
            "Power Hypertrophy Upper Lower: zwei Krafttage (Ober- und Unterkörper) und zwei Hypertrophietage.",
        ),
        "Power Hypertrophy Adaptive Training: dos días de potencia y tres de hipertrofia por grupos." to listOf(
            "Power Hypertrophy Adaptive Training: dos dies de potència i tres d'hipertròfia per grups musculars.",
            "Power Hypertrophy Adaptive Training: dous días de potencia e tres de hipertrofia por grupos musculares.",
            "Power Hypertrophy Adaptive Training: dois dias de potência e três de hipertrofia por grupos musculares.",
            "Power Hypertrophy Adaptive Training : deux jours de puissance et trois d'hypertrophie par groupe musculaire.",
            "Power Hypertrophy Adaptive Training: zwei Power-Tage und drei Hypertrophietage nach Muskelgruppen.",
        ),
        "Solo peso corporal. Empieza por el nivel que puedas y avanza con las progresiones de Planificación." to listOf(
            "Només pes corporal. Comença pel nivell que puguis i avança amb les progressions de Planificació.",
            "Só peso corporal. Comeza polo nivel que poidas e avanza coas progresións de Planificación.",
            "Só peso corporal. Começa no nível que conseguires e avança com as progressões de Planeamento.",
            "Poids du corps uniquement. Commence à ton niveau et progresse avec les progressions de Planification.",
            "Nur Körpergewicht. Starte auf deinem Niveau und steigere dich mit den Progressionen unter Planung.",
        ),
        "Sentadilla, banca y peso muerto con variantes y accesorios para el press. Combínalo con el pico de Planificación antes de competir." to listOf(
            "Esquat, banca i pes mort amb variants i accessoris per al press. Combina'l amb la preparació de Planificació abans de competir.",
            "Agachamento, banco e peso morto con variantes e accesorios para o press. Combínao coa preparación de Planificación antes de competir.",
            "Agachamento, supino e peso morto com variantes e acessórios para o supino. Combina-o com o peaking de Planeamento antes de competir.",
            "Squat, développé couché et soulevé de terre avec variantes et accessoires pour le développé. Combine-le avec la préparation de Planification avant une compétition.",
            "Kniebeuge, Bankdrücken und Kreuzheben mit Varianten und Zusatzübungen fürs Drücken. Kombiniere es vor einem Wettkampf mit dem Peaking unter Planung.",
        ),
        // Descripciones (programas con progresión)
        "Ciclos de 4 semanas (5s, 3s, 5/3/1 y descarga) sobre tu training max, más 5 × 10 al 50 %. El TM sube solo al acabar cada ciclo." to listOf(
            "Cicles de 4 setmanes (5s, 3s, 5/3/1 i descàrrega) sobre el teu training max, més 5 × 10 al 50 %. El TM puja sol en acabar cada cicle.",
            "Ciclos de 4 semanas (5s, 3s, 5/3/1 e descarga) sobre o teu training max, máis 5 × 10 ao 50 %. O TM sobe só ao rematar cada ciclo.",
            "Ciclos de 4 semanas (5s, 3s, 5/3/1 e descarga) sobre o teu training max, mais 5 × 10 a 50 %. O TM sobe automaticamente no fim de cada ciclo.",
            "Cycles de 4 semaines (5s, 3s, 5/3/1 et décharge) sur ton training max, plus 5 × 10 à 50 %. Le TM augmente automatiquement à la fin de chaque cycle.",
            "4-Wochen-Zyklen (5er, 3er, 5/3/1 und Deload) auf Basis deines Trainingsmaximums, plus 5 × 10 mit 50 %. Das TM steigt nach jedem Zyklus automatisch.",
        ),
        "Progresión lineal por niveles: T1 pesado (5 × 3+), T2 volumen (3 × 10) y T3 accesorios (3 × 15+). Sube carga cada vez que completas las series; dos fallos seguidos → descarga al 90 %." to listOf(
            "Progressió lineal per nivells: T1 pesat (5 × 3+), T2 volum (3 × 10) i T3 accessoris (3 × 15+). Puja la càrrega cada cop que completes les sèries; dues fallades seguides → descàrrega al 90 %.",
            "Progresión lineal por niveis: T1 pesado (5 × 3+), T2 volume (3 × 10) e T3 accesorios (3 × 15+). Sobe a carga cada vez que completas as series; dous fallos seguidos → descarga ao 90 %.",
            "Progressão linear por níveis: T1 pesado (5 × 3+), T2 volume (3 × 10) e T3 acessórios (3 × 15+). A carga sobe sempre que completas as séries; duas falhas seguidas → descarga para 90 %.",
            "Progression linéaire par niveaux : T1 lourd (5 × 3+), T2 volume (3 × 10) et T3 accessoires (3 × 15+). La charge augmente chaque fois que tu termines toutes les séries ; deux échecs de suite → décharge à 90 %.",
            "Lineare Progression in Stufen: schweres T1 (5 × 3+), Volumen-T2 (3 × 10) und T3-Zusatzübungen (3 × 15+). Das Gewicht steigt jedes Mal, wenn du alle Sätze schaffst; zwei Fehlversuche in Folge → Deload auf 90 %.",
        ),
        "Sentadilla, banca y peso muerto cada semana en tres registros: fuerza (5 × 3 al 85 %), hipertrofia (4 × 8 al 70 %) y potencia/técnica (6 × 2 al 75 %). El TM sube cada 3 semanas." to listOf(
            "Esquat, banca i pes mort cada setmana en tres registres: força (5 × 3 al 85 %), hipertròfia (4 × 8 al 70 %) i potència/tècnica (6 × 2 al 75 %). El TM puja cada 3 setmanes.",
            "Agachamento, banco e peso morto cada semana en tres rexistros: forza (5 × 3 ao 85 %), hipertrofia (4 × 8 ao 70 %) e potencia/técnica (6 × 2 ao 75 %). O TM sobe cada 3 semanas.",
            "Agachamento, supino e peso morto todas as semanas em três registos: força (5 × 3 a 85 %), hipertrofia (4 × 8 a 70 %) e potência/técnica (6 × 2 a 75 %). O TM sobe a cada 3 semanas.",
            "Squat, développé couché et soulevé de terre chaque semaine sur trois registres : force (5 × 3 à 85 %), hypertrophie (4 × 8 à 70 %) et puissance/technique (6 × 2 à 75 %). Le TM augmente toutes les 3 semaines.",
            "Kniebeuge, Bankdrücken und Kreuzheben jede Woche in drei Varianten: Kraft (5 × 3 mit 85 %), Hypertrophie (4 × 8 mit 70 %) und Power/Technik (6 × 2 mit 75 %). Das TM steigt alle 3 Wochen.",
        ),
        "Volumen el lunes (5 × 5), recuperación el miércoles (ligero) e intensidad el viernes (1 × 5 récord). El TM sube cada semana." to listOf(
            "Volum el dilluns (5 × 5), recuperació el dimecres (lleuger) i intensitat el divendres (1 × 5 rècord). El TM puja cada setmana.",
            "Volume o luns (5 × 5), recuperación o mércores (lixeiro) e intensidade o venres (1 × 5 récord). O TM sobe cada semana.",
            "Volume à segunda (5 × 5), recuperação à quarta (leve) e intensidade à sexta (1 × 5 recorde). O TM sobe todas as semanas.",
            "Volume le lundi (5 × 5), récupération le mercredi (léger) et intensité le vendredi (1 × 5 record). Le TM augmente chaque semaine.",
            "Volumen am Montag (5 × 5), Erholung am Mittwoch (leicht) und Intensität am Freitag (1 × 5 Rekord). Das TM steigt jede Woche.",
        ),
        "Series en rampa: lunes pesado (5 × 5 hasta el 80 %), miércoles ligero y viernes récord de 5 con una de 3 y una de 8. Sube un 2,5 % cada semana." to listOf(
            "Sèries en rampa: dilluns pesat (5 × 5 fins al 80 %), dimecres lleuger i divendres rècord de 5 amb una de 3 i una de 8. Puja un 2,5 % cada setmana.",
            "Series en rampla: luns pesado (5 × 5 ata o 80 %), mércores lixeiro e venres récord de 5 cunha de 3 e unha de 8. Sobe un 2,5 % cada semana.",
            "Séries em rampa: segunda pesada (5 × 5 até 80 %), quarta leve e sexta recorde de 5 com uma série de 3 e outra de 8. Sobe 2,5 % todas as semanas.",
            "Séries pyramidales : lundi lourd (5 × 5 jusqu'à 80 %), mercredi léger et vendredi record sur 5 avec une série de 3 et une de 8. Augmente d'environ 2,5 % chaque semaine.",
            "Ansteigende Sätze: schwerer Montag (5 × 5 bis 80 %), leichter Mittwoch und am Freitag ein 5er-Rekord plus ein 3er und ein 8er. Steigt jede Woche um ca. 2,5 %.",
        ),
        "Mucho volumen con porcentajes: 9 series del básico (con una de 1+ y otra de máximas reps) y 8 de un secundario. El TM sube cada semana según tus AMRAP." to listOf(
            "Molt volum amb percentatges: 9 sèries del bàsic (amb una d'1+ i una altra de màximes reps) i 8 d'un secundari. El TM puja cada setmana segons els teus AMRAP.",
            "Moito volume con porcentaxes: 9 series do básico (cunha de 1+ e outra de máximas reps) e 8 dun secundario. O TM sobe cada semana segundo os teus AMRAP.",
            "Muito volume com percentagens: 9 séries do exercício principal (com uma de 1+ e outra de máximo de reps) e 8 de um secundário. O TM sobe todas as semanas conforme os teus AMRAP.",
            "Beaucoup de volume en pourcentages : 9 séries du mouvement principal (dont une de 1+ et une au max de reps) et 8 d'un secondaire. Le TM augmente chaque semaine selon tes AMRAP.",
            "Viel Volumen mit Prozentwerten: 9 Sätze der Hauptübung (mit einem 1+-Satz und einem Satz bis zum Maximum) und 8 einer Nebenübung. Das TM steigt jede Woche je nach deinen AMRAPs.",
        ),
        // Días y plantillas
        "Empuje" to listOf("Empenta", "Empuxe", "Empurrar", "Poussée", "Drücken"),
        "Tirón" to listOf("Estirada", "Tirón", "Puxar", "Tirage", "Ziehen"),
        "Pierna" to listOf("Cama", "Perna", "Pernas", "Jambes", "Beine"),
        "Torso" to listOf("Tors", "Torso", "Tronco", "Haut du corps", "Oberkörper"),
        "Tren inferior" to listOf("Tren inferior", "Tren inferior", "Membros inferiores", "Bas du corps", "Unterkörper"),
        "Cuerpo completo" to listOf("Cos sencer", "Corpo completo", "Corpo inteiro", "Corps entier", "Ganzkörper"),
        "Torso fuerza" to listOf("Tors força", "Torso forza", "Tronco força", "Haut du corps force", "Oberkörper Kraft"),
        "Pierna fuerza" to listOf("Cama força", "Perna forza", "Pernas força", "Jambes force", "Beine Kraft"),
        "Torso hipertrofia" to listOf("Tors hipertròfia", "Torso hipertrofia", "Tronco hipertrofia", "Haut du corps hypertrophie", "Oberkörper Hypertrophie"),
        "Pierna hipertrofia" to listOf("Cama hipertròfia", "Perna hipertrofia", "Pernas hipertrofia", "Jambes hypertrophie", "Beine Hypertrophie"),
        "Glúteo" to listOf("Glutis", "Glúteo", "Glúteos", "Fessiers", "Po"),
        "Torso potencia" to listOf("Tors potència", "Torso potencia", "Tronco potência", "Haut du corps puissance", "Oberkörper Power"),
        "Pierna potencia" to listOf("Cama potència", "Perna potencia", "Pernas potência", "Jambes puissance", "Beine Power"),
        "Espalda y hombro" to listOf("Esquena i espatlla", "Costas e ombro", "Costas e ombros", "Dos et épaules", "Rücken & Schultern"),
        "Pecho y brazos" to listOf("Pit i braços", "Peito e brazos", "Peito e braços", "Pectoraux et bras", "Brust & Arme"),
        "Empuje y pierna" to listOf("Empenta i cama", "Empuxe e perna", "Empurrar e pernas", "Poussée et jambes", "Drücken & Beine"),
        "Tirón y core" to listOf("Estirada i core", "Tirón e core", "Puxar e core", "Tirage et gainage", "Ziehen & Core"),
        "Sentadilla" to listOf("Esquat", "Agachamento", "Agachamento", "Squat", "Kniebeuge"),
        "Banca" to listOf("Banca", "Banco", "Supino", "Développé couché", "Bankdrücken"),
        "Peso muerto" to listOf("Pes mort", "Peso morto", "Peso morto", "Soulevé de terre", "Kreuzheben"),
        "Banca volumen" to listOf("Banca volum", "Banco volume", "Supino volume", "Développé couché volume", "Bankdrücken Volumen"),
        "Press militar" to listOf("Press militar", "Press militar", "Press militar", "Développé militaire", "Schulterdrücken"),
        "Press banca" to listOf("Press de banca", "Press de banco", "Supino", "Développé couché", "Bankdrücken"),
        "Fuerza" to listOf("Força", "Forza", "Força", "Force", "Kraft"),
        "Hipertrofia" to listOf("Hipertròfia", "Hipertrofia", "Hipertrofia", "Hypertrophie", "Hypertrophie"),
        "Potencia" to listOf("Potència", "Potencia", "Potência", "Puissance", "Power"),
        "Volumen" to listOf("Volum", "Volume", "Volume", "Volume", "Volumen"),
        "Recuperación" to listOf("Recuperació", "Recuperación", "Recuperação", "Récupération", "Erholung"),
        "Intensidad" to listOf("Intensitat", "Intensidade", "Intensidade", "Intensité", "Intensität"),
        "Pesado" to listOf("Pesat", "Pesado", "Pesado", "Lourd", "Schwer"),
        "Ligero" to listOf("Lleuger", "Lixeiro", "Leve", "Léger", "Leicht"),
        "Récord" to listOf("Rècord", "Récord", "Recorde", "Record", "Rekord"),
        "Banca + press" to listOf("Banca + press", "Banco + press", "Supino + press militar", "Développé couché + militaire", "Bankdrücken + Schulterdrücken"),
        "Sentadilla + muerto" to listOf("Esquat + pes mort", "Agachamento + peso morto", "Agachamento + peso morto", "Squat + soulevé de terre", "Kniebeuge + Kreuzheben"),
        "Press + banca" to listOf("Press + banca inclinada", "Press + banco inclinado", "Press militar + supino inclinado", "Développé militaire + incliné", "Schulterdrücken + Schrägbank"),
        "Muerto + frontal" to listOf("Pes mort + esquat frontal", "Peso morto + agachamento frontal", "Peso morto + agachamento frontal", "Soulevé de terre + squat avant", "Kreuzheben + Frontkniebeuge"),
    )
}
