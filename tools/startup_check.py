#!/usr/bin/env python3
"""Lee el JSON de Macrobenchmark, guarda la mediana del arranque en frío y la compara con la anterior."""
import glob
import json
import sys

LIMIT = 1.25  # avisar si es un 25 % más lento

files = glob.glob("baselineprofile/build/**/*benchmarkData.json", recursive=True)
if not files:
    sys.exit("No se encontró benchmarkData.json")
# Puede haber varios JSON (la prueba de humo también genera uno): buscamos el del arranque
found = [(json.load(open(f)), f) for f in files]
data, bench = next(((d, b) for d, _ in found for b in d.get("benchmarks", []) if b.get("name") == "coldStartup"), (None, None))
if bench is None:
    sys.exit("No hay resultados de coldStartup")
median = bench["metrics"]["timeToInitialDisplayMs"]["median"]
result = {"timeToInitialDisplayMs": round(median, 1), "device": data.get("context", {}).get("build", {}).get("model", "")}
json.dump(result, open("/tmp/bp/startup.json", "w"), indent=1)
print(f"Arranque en frío (mediana): {median:.0f} ms")
try:
    previous = json.load(open(sys.argv[1]))["timeToInitialDisplayMs"]
except Exception:  # noqa: BLE001
    print("Sin medición anterior")
    sys.exit(0)
print(f"Anterior: {previous:.0f} ms")
if median > previous * LIMIT:
    sys.exit(f"El arranque empeoró más de un {int((LIMIT - 1) * 100)} %: {previous:.0f} → {median:.0f} ms")
