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
- `DartsCounter_backend/` – Spring Boot (Java 25): Kameras/Pfeilerkennung und Datenspeicher
- `dart_tracking.py`, `DartTracking.java` – Triangulation aus 3 Kamerawinkeln
- `3D-models/` – Kamerahalterungen
- `cpFolder*.sh` – Kopieren zwischen Rechner und Pi

## Starten

1. Backend: im Ordner `DartsCounter_backend` `./mvnw spring-boot:run` (läuft auf Port 8080)
2. Frontend: Ordner `DartsCounter_frontend` über einen Webserver ausliefern (auf dem Pi `/var/www/html`, lokal z. B. VS Code Live Server) und `mainPages/index.html` öffnen

Das Frontend sucht das Backend automatisch unter `http://<gleicher Rechner>:8080`. Eine andere Adresse kann in den Einstellungen eingetragen werden.

## Daten

Spieler, Spiele und Einstellungen speichert das Backend als JSON in `~/dartscounter-data` (änderbar über `dartscounter.data-dir` in `application.properties`). Ist das Backend nicht erreichbar, speichert das Frontend nur im Browser und zeigt einen Hinweis an. Backup: Einstellungen → „Backup herunterladen“.
