# DartsCounter

Automatischer Darts-Zähler für den Raspberry Pi: 3 Kameras erkennen den Pfeil, das Backend berechnet die Position, die Weboberfläche zählt die Punkte. Punkte können auch per Tastatur oder durch Klick auf die Scheibe eingegeben werden.

## Aufbau

- `DartsCounter_frontend/` – Weboberfläche (HTML/CSS/JS, kein Build nötig)
  - `mainPages/` – Hauptseite, Spieler, Statistiken, Alle Spiele, Einstellungen
  - `gameModes/` – Spielseiten: X01, Cricket, Around the Clock, 121, Freies Spiel
  - `js/storage.js` – Speichern über das Backend (Ersatz: Browser-Speicher)
  - `js/dartboard.js` – Scheiben-Geometrie, Checkout-Wege, Bot-Würfe
  - `js/rules.js` – Spielregeln aller Modi
  - `js/stats.js` – Statistiken
  - `js/accounts.js` – Konten und Anmeldung (über das Backend, Demo-Modus als Ersatz)
- `DartsCounter_backend/` – Spring Boot (Java 25): Kameras/Pfeilerkennung, Datenspeicher und Konten
- `dart_tracking.py`, `DartTracking.java` – Triangulation aus 3 Kamerawinkeln
- `3D-models/` – Kamerahalterungen
- `cpFolder*.sh` – Kopieren zwischen Rechner und Pi

## Starten

1. Backend: im Ordner `DartsCounter_backend` `./mvnw spring-boot:run` (läuft auf Port 8080)
2. Frontend: Ordner `DartsCounter_frontend` über einen Webserver ausliefern (auf dem Pi `/var/www/html`, lokal z. B. VS Code Live Server) und `mainPages/index.html` öffnen

Das Frontend sucht das Backend automatisch unter `http://<gleicher Rechner>:8080`. Eine andere Adresse kann in den Einstellungen eingetragen werden.

## Daten

Spieler, Spiele und Einstellungen speichert das Backend als JSON in `~/dartscounter-data` (änderbar über `dartscounter.data-dir` in `application.properties`). Ist das Backend nicht erreichbar, speichert das Frontend nur im Browser und zeigt einen Hinweis an. Backup: Einstellungen → „Backup herunterladen“.


## Konten

Konten sind freiwillig, gespielt werden kann auch ohne Anmeldung. Ein Konto besteht nur aus id, Spielername (max. 16 Zeichen), Rolle (`admin` oder `player`) und dem Datum. Keine E-Mail, kein echter Name, keine IP-Adressen.

- Anmelden mit Spielername und Zugangscode (`XXXX-XXXX-XXXX`), den der Admin erzeugt und weitergibt.
- Der Code wird nur als BCrypt-Hash gespeichert. Ein vergessener Code lässt sich nicht anzeigen, nur neu erzeugen.
- Nach 5 Fehlversuchen pro Spielername ist die Anmeldung 15 Minuten gesperrt.
- Eine Anmeldung gilt 30 Tage. Das Token liegt im Browser, das Backend speichert nur seinen SHA-256.
- Ist das Backend nicht erreichbar, läuft das Frontend im Demo-Modus: Konten liegen dann nur im Browser und sind nicht geschützt.

Speicherort: `accounts.json` (Konten) und `sessions.json` (Anmeldungen) im gleichen Ordner wie die übrigen Daten (`dartscounter.data-dir`, Standard `~/dartscounter-data`). Die Dateien sind nur für den Benutzer lesbar, unter dem das Backend läuft, und nicht über `/api/data` abrufbar. Alle Konten zurücksetzen: Backend stoppen, beide Dateien löschen, neu starten. Danach erscheint wieder „Erster Start“.

### Endpunkte

Angemeldete Anfragen schicken `Authorization: Bearer <token>`. Fehler kommen als `{"message": "..."}`.

| Methode | Pfad | Body | Antwort | Wer |
|---|---|---|---|---|
| GET | `/api/auth/setup` | – | `{needed}` | alle |
| POST | `/api/auth/setup` | `{name}` | `{account, code}` | nur solange kein Konto existiert |
| POST | `/api/auth/login` | `{name, code}` | `{token, account}` | alle |
| POST | `/api/auth/logout` | – | 204 | angemeldet |
| GET | `/api/auth/me` | – | `account` | angemeldet |
| GET | `/api/accounts` | – | `[account]` | Admin |
| POST | `/api/accounts` | `{name, role}` | `{account, code}` | Admin |
| POST | `/api/accounts/{id}/code` | – | `{code}` | Admin (alter Code und alle Anmeldungen des Kontos werden ungültig) |
| PATCH | `/api/accounts/{id}` | `{role}` | `account` | Admin |
| DELETE | `/api/accounts/{id}` | – | 204 | Admin |

`account` = `{id, name, role, createdAt}`. Statuscodes: 400 ungültige Eingabe, 401 nicht angemeldet oder falscher Code, 403 kein Admin, 404 Konto nicht gefunden, 409 Regelverstoß (Name vergeben, letzter Admin, eigenes Konto löschen), 429 zu viele Fehlversuche.

Die Daten-Endpunkte `/api/data/{key}` sind weiterhin ohne Anmeldung nutzbar.
