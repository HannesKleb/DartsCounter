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

### Abgleich (keine Daten verlieren)

Ist das Backend nicht erreichbar oder verlangt es eine Anmeldung (401), speichert das Frontend im Browser und merkt sich den Schlüssel als ausstehend (`localStorage` → `store.pending`). Sobald das Backend wieder antwortet und den Zugriff erlaubt (beim Laden einer Seite, alle 30 Sekunden, direkt nach dem Anmelden), wird abgeglichen:

- `games`: lokale und Server-Liste werden nach `id` zusammengeführt, kein Spiel fällt weg. Bei gleicher `id` gewinnt das Spiel mit mehr Würfen, sonst das später beendete.
- `players`: nach Namen zusammengeführt (Groß-/Kleinschreibung egal).
- `settings` und `currentGame`: der lokale Stand gewinnt.
- Bewusst gelöschte Spiele und Spieler merkt sich das Frontend (`store.removed.<key>`), damit sie beim Zusammenführen nicht zurückkommen. „Alle Spiele löschen“, „Alle Spieler löschen“ und „Backup einspielen“ ersetzen den Server-Stand.
- Der Abgleich läuft nie doppelt. Eine lokale Kopie, die noch aussteht, wird beim Lesen nicht vom Server überschrieben.
- In den Einstellungen steht, ob noch etwas aussteht („Jetzt hochladen“).


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

### Zugriff von außen

- **Im Heimnetz:** alles frei, Spielen ohne Konto geht wie bisher.
- **Von außen:** `/api/data` (Spieler, Spiele, Einstellungen) nur mit Anmeldung, egal ob Admin oder Spieler. Die Kamera-Endpunkte `/services` nur für Admins. Ohne Anmeldung kommt 401, das Frontend speichert dann nur im Browser und zeigt einen Hinweis.
- Als „von außen“ zählt eine Anfrage, wenn
  - sie den Header `CF-Connecting-IP` hat (Cloudflare Tunnel; cloudflared selbst verbindet sich von 127.0.0.1), oder
  - die Absender-Adresse keine Heimnetz-Adresse ist (z. B. bei einer Portweiterleitung im Router).
  - Heimnetz-Adressen sind Loopback, 10.x, 172.16–31.x, 192.168.x, link-local und IPv6 fc00::/7.
  - Die Adresse wird nur für diese Prüfung angesehen, nie gespeichert oder geloggt.
- Einstellbar in `application.properties` über `dartscounter.data.require-login` und `dartscounter.services.require-login`: `tunnel` (Standard), `always` oder `never`.
- Nicht geschützt sind `/api/auth/*` (die Anmeldung muss erreichbar sein) und `/api/hallo`.

### Cloudflare Tunnel einrichten

Von unterwegs läuft alles über eine Adresse mit HTTPS (z. B. `https://darts.deine-domain.de`). Der Tunnel leitet `/api/` und `/services/` an das Backend, alles andere an den Webserver mit dem Frontend. Das Frontend erkennt HTTPS und benutzt dann automatisch dieselbe Domain (kein Port 8080). Im Heimnetz (`http://darts-counter`) bleibt es bei Port 8080.

`config.yml` (Tunnel-ID und Domain ersetzen):

```yaml
tunnel: <TUNNEL-ID>
credentials-file: /etc/cloudflared/<TUNNEL-ID>.json

ingress:
  # Backend (Spring Boot)
  - hostname: darts.deine-domain.de
    path: ^/(api|services)/
    service: http://localhost:8080
  # Frontend (Webserver mit /var/www/html)
  - hostname: darts.deine-domain.de
    service: http://localhost:80
  # alles andere ablehnen
  - service: http_status:404
```

- Liegt auf dem Pi unter `/etc/cloudflared/config.yml`, wenn cloudflared als Dienst installiert ist (`sudo cloudflared service install`). Startet man cloudflared von Hand, liegt sie unter `~/.cloudflared/config.yml`.
- Prüfen: `cloudflared tunnel ingress validate` und `cloudflared tunnel ingress rule https://darts.deine-domain.de/api/hallo` (muss die Backend-Regel treffen).
- Neu starten nach Änderungen: `sudo systemctl restart cloudflared`, Status mit `systemctl status cloudflared`.
- Eine eigene Backend-Adresse lässt sich in den Einstellungen eintragen. Leer lassen ist der Normalfall.
