#!/usr/bin/env python3
"""
Genera mobile/src/main/assets/exercise_media.json cruzando la biblioteca de BeStrong con las
imágenes de wger (https://wger.de), que son contenido abierto con licencia por imagen
(normalmente CC-BY-SA 4.0). Guarda autor, licencia y enlaces para mostrar la atribución.

Uso: python3 tools/exercise_media.py  (necesita acceso a wger.de; lo ejecuta el workflow «Exercise media»)
"""
import difflib
import json
import re
import sys
import time
import urllib.request

API = "https://wger.de/api/v2/exerciseinfo/?limit=100&offset={}"
NAMES_KT = "core/src/main/kotlin/com/bestrong/core/library/ExerciseNamesEn.kt"
OUT = "mobile/src/main/assets/exercise_media.json"
REPORT = "build/exercise_media_report.txt"
ENGLISH = 2

# Nombres alternativos con los que wger suele llamar a nuestros ejercicios
ALIASES = {
    "bench_press": ["Bench Press", "Barbell Bench Press"],
    "overhead_press": ["Overhead Press", "Shoulder Press, Barbell", "Military Press"],
    "squat": ["Squats", "Barbell Squat", "Squat", "Back Squat"],
    "front_squat": ["Front Squats", "Front Squat"],
    "incline_bench_press": ["Incline Bench Press", "Bench Press Incline", "Incline Barbell Bench Press"],
    "decline_bench_press": ["Decline Bench Press", "Bench Press Decline"],
    "db_bench_press": ["Dumbbell Bench Press", "Bench Press Dumbbells", "Dumbbell Press"],
    "db_incline_press": ["Incline Dumbbell Press", "Incline Dumbbell Bench Press", "Dumbbell Incline Bench Press"],
    "db_fly": ["Dumbbell Fly", "Fly With Dumbbells", "Flys"],
    "db_row": ["Dumbbell Row", "One Arm Dumbbell Row", "Bent Over Dumbbell Rows"],
    "db_shoulder_press": ["Dumbbell Shoulder Press", "Shoulder Press, Dumbbells"],
    "hammer_curl": ["Hammer Curls", "Hammercurls", "Hammer Curl"],
    "skull_crusher": ["Skull Crusher", "French Press (skullcrusher) SZ-bar", "Skullcrusher"],
    "seated_cable_row": ["Seated Cable Row", "Seated Cable Rows", "Cable Row"],
    "goblet_squat": ["Goblet Squat"],
    "lunge": ["Lunges", "Dumbbell Lunges", "Lunge"],
    "bulgarian_split_squat": ["Bulgarian Split Squat", "Split Squat"],
    "hanging_leg_raise": ["Hanging Leg Raises", "Leg Raises, Hanging"],
    "preacher_curl": ["Preacher Curls", "Biceps Curl on Preacher Bench"],
    "close_grip_bench": ["Close-grip Bench Press", "Bench Press Narrow Grip"],
    "deadlift": ["Deadlifts", "Deadlift"],
    "barbell_row": ["Bent Over Rowing", "Barbell Row", "Bent Over Barbell Row"],
    "pull_up": ["Pull-ups", "Pull Ups", "Pull-up"],
    "chin_up": ["Chin-ups", "Chin Ups"],
    "push_up": ["Push-Ups", "Push Ups", "Pushups"],
    "dips": ["Dips", "Dips Between Two Benches"],
    "lat_pulldown": ["Lat Pull Down", "Lat Pulldown", "Pulldown"],
    "db_curl": ["Dumbbell Curl", "Biceps Curls With Dumbbell"],
    "barbell_curl": ["Biceps Curls With Barbell", "Barbell Curl"],
    "triceps_pushdown": ["Triceps Pushdown", "Tricep Pushdown", "Pushdown"],
    "leg_press": ["Leg Press", "Leg Presses"],
    "leg_extension": ["Leg Extension", "Leg Extensions"],
    "leg_curl": ["Leg Curl", "Leg Curls (laying)", "Lying Leg Curl"],
    "lateral_raise": ["Lateral Raises", "Lateral Raise"],
    "plank": ["Plank"],
    "crunch": ["Crunches", "Crunch"],
    "hip_thrust": ["Hip Thrust", "Barbell Hip Thrust"],
    "romanian_deadlift": ["Romanian Deadlift", "Stiff-legged Deadlifts"],
    "face_pull": ["Face Pull", "Face Pulls"],
    "shrug": ["Shrugs, Barbells", "Barbell Shrug", "Shrugs"],
    "standing_calf_raise": ["Calf Raises", "Standing Calf Raises"],
}


def norm(s):
    s = s.lower()
    s = re.sub(r"\(.*?\)", " ", s)
    s = re.sub(r"[^a-z0-9 ]", " ", s)
    s = re.sub(r"\b(the|with|a)\b", " ", s)
    s = re.sub(r"s\b", "", s)  # plurales simples
    return " ".join(s.split())


def fetch(url):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "BeStrong-media-script"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except Exception as e:  # noqa: BLE001
            print("reintento", url, e, file=sys.stderr)
            time.sleep(2 ** attempt)
    raise SystemExit("No se pudo descargar " + url)


EDB_API = "https://oss.exercisedb.dev/api/v1/exercises?offset={}&limit=100"

# Cómo suele llamar ExerciseDB a nuestros ejercicios (nombres en minúsculas, con el material delante)
EDB_ALIASES = {
    "bench_press": ["barbell bench press"],
    "incline_bench_press": ["barbell incline bench press"],
    "decline_bench_press": ["barbell decline bench press"],
    "db_bench_press": ["dumbbell bench press"],
    "db_incline_press": ["dumbbell incline bench press"],
    "db_fly": ["dumbbell fly"],
    "squat": ["barbell full squat", "barbell squat"],
    "front_squat": ["barbell front squat"],
    "deadlift": ["barbell deadlift"],
    "romanian_deadlift": ["barbell romanian deadlift"],
    "overhead_press": ["barbell standing military press", "barbell seated overhead press"],
    "barbell_row": ["barbell bent over row"],
    "db_row": ["dumbbell one arm bent-over row", "dumbbell bent over row"],
    "lat_pulldown": ["cable pulldown", "cable lat pulldown full range of motion"],
    "pull_up": ["pull-up"],
    "chin_up": ["chin-up"],
    "push_up": ["push-up"],
    "dips": ["chest dip", "triceps dip"],
    "barbell_curl": ["barbell curl"],
    "db_curl": ["dumbbell biceps curl"],
    "hammer_curl": ["dumbbell hammer curl"],
    "preacher_curl": ["ez barbell preacher curl", "barbell preacher curl"],
    "triceps_pushdown": ["cable pushdown"],
    "skull_crusher": ["ez barbell lying triceps extension", "barbell lying triceps extension skull crusher"],
    "lateral_raise": ["dumbbell lateral raise"],
    "face_pull": ["cable rear delt row (with rope)", "cable face pull"],
    "shrug": ["barbell shrug"],
    "leg_press": ["sled 45° leg press", "lever leg press"],
    "leg_extension": ["lever leg extension"],
    "leg_curl": ["lever lying leg curl"],
    "seated_leg_curl": ["lever seated leg curl"],
    "hip_thrust": ["barbell glute bridge", "barbell hip thrust"],
    "standing_calf_raise": ["lever standing calf raise", "barbell standing calf raise"],
    "seated_calf_raise": ["lever seated calf raise"],
    "plank": ["front plank", "weighted front plank"],
    "crunch": ["crunch floor"],
    "hanging_leg_raise": ["hanging leg raise"],
    "ab_wheel": ["wheel rollout", "wheel run"],
    "lunge": ["dumbbell lunge", "barbell lunge"],
    "bulgarian_split_squat": ["dumbbell single leg split squat"],
    "goblet_squat": ["dumbbell goblet squat"],
    "kb_swing": ["kettlebell swing"],
    "pec_deck": ["lever pec deck fly"],
    "cable_crossover": ["cable cross-over variation", "cable standing fly"],
    "seated_cable_row": ["cable seated row"],
    "t_bar_row": ["lever t bar row", "barbell lever t bar row"],
    "arnold_press": ["dumbbell arnold press"],
    "upright_row": ["barbell upright row"],
    "rear_delt_fly": ["dumbbell rear fly", "dumbbell rear lateral raise"],
}


def exercisedb(ours):
    """GIF animados del dataset gratuito v1 de ExerciseDB (AscendAPI): uso no comercial con atribución."""
    items, offset = [], 0
    while True:
        page = fetch(EDB_API.format(offset))
        data = page.get("data", page) if isinstance(page, dict) else page
        if offset == 0:
            print("Ejemplo ExerciseDB:", json.dumps(data[:1], ensure_ascii=False)[:800])
        items += data
        if len(data) < 100 or offset > 3000:
            break
        offset += 100
    by_name = {}
    for it in items:
        if it.get("gifUrl") and it.get("name"):
            by_name.setdefault(norm(it["name"]), it)
    keys = list(by_name)
    out, report = {}, []
    for eid, name in sorted(ours.items()):
        tried = EDB_ALIASES.get(eid, []) + [name]
        hit = next((by_name[norm(n)] for n in tried if norm(n) in by_name), None)
        how = "exacto"
        if hit is None:
            close = difflib.get_close_matches(norm(name), keys, n=1, cutoff=0.9)
            if close:
                hit, how = by_name[close[0]], "parecido"
        if hit is None:
            report.append(f"EDB SIN GIF  {eid:28} {name}")
            continue
        report.append(f"EDB {how:9} {eid:28} {name}  ->  {hit['name']}")
        out[eid] = {
            "url": hit["gifUrl"],
            "source": "exercisedb",
            "isGif": True,
            "wgerName": hit["name"],
            "author": "AscendAPI (ExerciseDB)",
            "license": "ExerciseDB v1 · uso no comercial",
            "licenseUrl": "https://oss.exercisedb.dev/docs",
            "wgerUrl": "https://exercisedb.dev",
        }
    print(f"ExerciseDB: {len(out)} de {len(ours)} con GIF ({len(items)} ejercicios en el dataset)")
    return out, report


def main():
    src = open(NAMES_KT, encoding="utf-8").read()
    # Solo el mapa de nombres (el archivo también tiene las instrucciones en inglés)
    block = src.split("englishNames", 1)[1].split("\n)", 1)[0]
    # Licencias de wger por id (p. ej. 1 = CC-BY-SA 4.0)
    licenses = {}
    page = fetch("https://wger.de/api/v2/license/?limit=100")
    for lic in page["results"]:
        licenses[lic["id"]] = {"short": lic.get("short_name") or lic.get("full_name"), "url": lic.get("url") or ""}
    print("Licencias:", {k: v["short"] for k, v in licenses.items()})
    ours = dict(re.findall(r'"([a-z0-9_]+)" to "([^"]+)"', block))
    candidates = {}  # nombre normalizado -> (nombre, imagen)
    offset = 0
    while True:
        page = fetch(API.format(offset))
        for ex in page["results"]:
            # Solo imágenes con licencia abierta conocida (Creative Commons o dominio público)
            images = [
                i for i in ex.get("images", [])
                if i.get("image") and re.search(r"CC|Creative|Public", str(licenses.get(i.get("license"), {}).get("short", "")), re.I)
            ]
            if not images:
                continue
            main_img = next((i for i in images if i.get("is_main")), images[0])
            translations = ex.get("translations") or ex.get("exercises") or []
            for t in translations:
                if t.get("language") == ENGLISH and t.get("name"):
                    candidates.setdefault(norm(t["name"]), (t["name"], main_img, ex.get("id")))
        if not page.get("next"):
            break
        offset += 100
    keys = list(candidates)
    out, report = {}, []
    for eid, name in sorted(ours.items()):
        tried = [name] + ALIASES.get(eid, [])
        hit = next((candidates[norm(n)] for n in tried if norm(n) in candidates), None)
        how = "exacto"
        if hit is None:
            close = difflib.get_close_matches(norm(name), keys, n=1, cutoff=0.88)
            if close:
                hit, how = candidates[close[0]], "parecido"
        if hit is None:
            report.append(f"SIN IMAGEN  {eid:28} {name}")
            continue
        wname, img, wid = hit
        report.append(f"{how:10}  {eid:28} {name}  ->  {wname}")
        out[eid] = {
            "url": img["image"],
            "wgerName": wname,
            "wgerUrl": f"https://wger.de/exercise/{wid}/view/",
            "author": img.get("license_author") or "",
            "authorUrl": img.get("license_author_url") or "",
            "title": img.get("license_title") or "",
            "license": licenses[img["license"]]["short"],
            "licenseUrl": licenses[img["license"]]["url"],
            "sourceUrl": img.get("license_object_url") or "",
        }
    # ExerciseDB primero (GIF animado); wger como respaldo para los que no tenga
    try:
        edb, edb_report = exercisedb(ours)
    except SystemExit as e:
        print("ExerciseDB no disponible:", e)
        edb, edb_report = {}, ["ExerciseDB no disponible"]
    for eid, v in out.items():
        v.setdefault("source", "wger")
    out = {**out, **edb}
    report = edb_report + report
    import os
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
    open(REPORT, "w", encoding="utf-8").write("\n".join(report) + "\n")
    print(f"{len(out)} de {len(ours)} ejercicios con imagen")


if __name__ == "__main__":
    main()
