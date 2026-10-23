"""Sube los .aab del móvil y del reloj a Google Play (API de publicación) en una sola edición.

Uso: play_upload.py <móvil.aab> <reloj.aab>
Variables: PLAY_SERVICE_ACCOUNT_JSON (clave de la cuenta de servicio), PLAY_PACKAGE (paquete, «com.copidev.bestrong» por
defecto), PLAY_TRACK (canal del móvil, «alpha» por defecto), PLAY_WEAR_TRACK (canal del reloj; si no existe se busca el
canal de Wear OS que ya tenga versiones), BESTRONG_VERSION (nombre de la versión).
Las notas de la versión salen de fastlane/metadata/android/<idioma>/changelogs/default.txt.
Mientras la app no esté publicada (borrador en Play Console), Play solo acepta versiones en borrador: se suben así y
hay que enviar a revisión esa primera versión a mano desde Play Console.
"""
import json
import os
import sys
from pathlib import Path
from urllib.parse import quote

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = os.environ.get("PLAY_PACKAGE") or "com.copidev.bestrong"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"


class PlayError(Exception):
    pass


def check(response):
    if not response.ok:
        raise PlayError(f"Error de Google Play ({response.status_code}): {response.text}")
    return response.json()


def release_notes():
    notes = []
    for path in sorted(Path("fastlane/metadata/android").glob("*/changelogs/default.txt")):
        text = path.read_text(encoding="utf-8").strip()
        if text:
            notes.append({"language": path.parent.parent.name, "text": text[:500]})
    return notes


def pick_wear_track(tracks, wanted):
    if wanted in tracks:
        return wanted
    # El canal cerrado del reloj tiene el nombre que se le dio en Play Console: se elige el de Wear OS con versiones
    used = [t for t, releases in tracks.items() if t.startswith("wear:") and t != "wear:production" and releases]
    if len(used) == 1:
        return used[0]
    sys.exit(f"No encuentro el canal del reloj «{wanted}». Canales: {', '.join(tracks)}. Pon el bueno en la variable PLAY_WEAR_TRACK.")


def upload(session, phone_aab, wear_aab, status):
    edit = check(session.post(f"{API}/edits"))["id"]
    tracks = {
        t["track"]: t.get("releases", [])
        for t in check(session.get(f"{API}/edits/{edit}/tracks")).get("tracks", [])
    }
    phone_track = os.environ.get("PLAY_TRACK") or "alpha"
    if phone_track not in tracks:
        sys.exit(f"No existe el canal del móvil «{phone_track}». Canales: {', '.join(tracks)}. Ponlo en la variable PLAY_TRACK.")
    wear_track = pick_wear_track(tracks, os.environ.get("PLAY_WEAR_TRACK") or "wear:Reloj - prueba cerrada")
    notes = release_notes()
    version = os.environ.get("BESTRONG_VERSION", "")

    for aab, track in ((phone_aab, phone_track), (wear_aab, wear_track)):
        with open(aab, "rb") as f:
            bundle = check(session.post(
                f"{UPLOAD}/edits/{edit}/bundles", params={"uploadType": "media"}, data=f,
                headers={"Content-Type": "application/octet-stream"}, timeout=600,
            ))
        code = str(bundle["versionCode"])
        release = {"name": version or code, "versionCodes": [code], "status": status}
        if notes:
            release["releaseNotes"] = notes
        check(session.put(f"{API}/edits/{edit}/tracks/{quote(track, safe='')}", json={"track": track, "releases": [release]}))
        print(f"{Path(aab).name} (versionCode {code}) → {track} [{status}]")

    check(session.post(f"{API}/edits/{edit}:commit"))


def main():
    phone_aab, wear_aab = sys.argv[1], sys.argv[2]
    info = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    credentials = service_account.Credentials.from_service_account_info(
        info, scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(credentials)

    try:
        try:
            upload(session, phone_aab, wear_aab, "completed")
        except PlayError as e:
            if "draft" not in str(e):
                raise
            # App aún en borrador: Play solo admite versiones en borrador (se repite la subida en una edición nueva)
            print("::warning::La app aún no está publicada en Play: la versión se sube como borrador. "
                  "Envíala a revisión desde Play Console (Prueba cerrada) y las siguientes saldrán solas.")
            upload(session, phone_aab, wear_aab, "draft")
    except PlayError as e:
        sys.exit(str(e))
    print("Enviado a Google Play")


if __name__ == "__main__":
    main()
