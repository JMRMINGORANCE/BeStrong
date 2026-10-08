#!/usr/bin/env python3
"""
Genera mobile/src/main/assets/exercise_media.json cruzando la biblioteca de BeStrong con las
imágenes de wger (https://wger.de), que son contenido abierto con licencia por imagen
(normalmente CC-BY-SA 4.0). Guarda autor, licencia y enlaces para mostrar la atribución.

Uso: python3 tools/wger_media.py  (necesita acceso a wger.de; lo ejecuta el workflow «Exercise media»)
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
    import os
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
    open(REPORT, "w", encoding="utf-8").write("\n".join(report) + "\n")
    print(f"{len(out)} de {len(ours)} ejercicios con imagen")


if __name__ == "__main__":
    main()
