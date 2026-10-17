# Anleitung: DartsCounter auf dem Raspberry Pi

Alles Schritt für Schritt: vom leeren Pi bis zum Spielen, dazu Updates und Hilfe bei Fehlern.

**Wo wird was eingegeben?**

- **PC-Terminal:** Windows: PowerShell. Mac: Terminal. Normales Fenster auf deinem PC.
- **Pi-Terminal:** ein PC-Terminal, in dem du mit `ssh admin@darts-counter` angemeldet bist. Die Zeile beginnt mit `admin@darts-counter:~ $`.
- **Browser:** Chrome, Edge oder Firefox auf PC oder Handy (im selben WLAN).

Beim Eintippen von Passwörtern erscheint nichts auf dem Bildschirm. Das ist normal.

---

## Inhalt

1. [Einmalig: SD-Karte einrichten](#1-einmalig-sd-karte-einrichten)
2. [Einmalig: PC vorbereiten](#2-einmalig-pc-vorbereiten)
3. [Einmalig: Pi vorbereiten](#3-einmalig-pi-vorbereiten)
4. [Backend aufspielen und als Dienst einrichten](#4-backend-aufspielen-und-als-dienst-einrichten)
5. [App (Oberfläche) aufspielen](#5-app-oberfläche-aufspielen)
6. [Kameras anschließen und kalibrieren](#6-kameras-anschließen-und-kalibrieren)
7. [Updates einspielen](#7-updates-einspielen)
8. [Wichtige Befehle](#8-wichtige-befehle)
9. [Hilfe bei Fehlern](#9-hilfe-bei-fehlern)

---

## 1. Einmalig: SD-Karte einrichten

Du brauchst einen SD-Kartenleser am PC.

1. Auf dem PC **Raspberry Pi Imager** von `raspberrypi.com/software` installieren und starten.
2. Auswählen:
   - Gerät: dein Pi-Modell (z. B. Raspberry Pi 3)
   - Betriebssystem: **Raspberry Pi OS Lite (64-bit)**
   - Speicher: die SD-Karte (alles darauf wird gelöscht)
3. Bei **Einstellungen bearbeiten** eintragen:
   - Hostname: `darts-counter`
   - Benutzername: `admin`, Passwort aufschreiben
   - WLAN: Name und Passwort (gesichertes Netzwerk), Land: `DE`. Der Pi 3 kann nur 2,4 GHz.
   - Reiter Dienste: **SSH aktivieren**, Passwort zur Anmeldung verwenden
   - Raspberry Pi Connect: aus
4. Schreiben, Karte in den Pi, LAN-Kabel (oder WLAN) und Strom anschließen, 3 Minuten warten.
5. **PC-Terminal:**
   ```
   ping darts-counter
   ```
   Kommt eine Antwort, ist der Pi im Netz.

## 2. Einmalig: PC vorbereiten

**PC-Terminal:**

```
git --version
java -version
```

- Fehlt Git: installieren von `git-scm.com`, Terminal neu öffnen.
- Ist Java nicht Version 25: **Temurin 25 (JDK)** von `adoptium.net` installieren, Terminal neu öffnen.

Projekt herunterladen (nur beim ersten Mal):

```
git clone --branch claude/funny-hypatia-8rhbc7 https://github.com/HannesKleb/DartsCounter.git
```

Das legt den Ordner `DartsCounter` an. Alle weiteren PC-Befehle laufen in diesem Ordner:

```
cd DartsCounter
```

## 3. Einmalig: Pi vorbereiten

**PC-Terminal:** anmelden

```
ssh admin@darts-counter
```

Beim ersten Mal kommt eine Frage zum *fingerprint*: `yes` eintippen, Enter, dann das Pi-Passwort.

**Pi-Terminal:**

```
sudo apt update
sudo apt install -y openjdk-25-jre-headless v4l-utils nginx
sudo usermod -aG video admin
sudo reboot
```

- `openjdk-25-jre-headless`: Java für das Backend. Gibt es das Paket nicht, sag Bescheid, dann wird das Projekt auf eine andere Java-Version umgestellt.
- `v4l-utils`: Werkzeuge für die Kameras.
- `nginx`: Webserver für die App.
- `usermod … video`: erlaubt dem Benutzer `admin`, die Kameras zu öffnen.

Nach `reboot` bricht die Verbindung ab. 1 Minute warten, dann wieder anmelden und prüfen:

```
java -version
groups
```

`java -version` muss `25` zeigen, `groups` muss `video` enthalten.

## 4. Backend aufspielen und als Dienst einrichten

### 4.1 Backend bauen (PC-Terminal, im Ordner `DartsCounter`)

```
cd DartsCounter_backend
```

- Windows: `.\mvnw.cmd -DskipTests package`
- Mac: `./mvnw -DskipTests package`

Warten bis `BUILD SUCCESS`. Die fertige Datei liegt in `target/DartsCounter_backend-0.0.1-SNAPSHOT.jar`.

### 4.2 Auf den Pi schicken (PC-Terminal, im Ordner `DartsCounter_backend`)

```
scp target/DartsCounter_backend-0.0.1-SNAPSHOT.jar admin@darts-counter:~/
```

### 4.3 Als Dienst einrichten (nur beim ersten Mal, Pi-Terminal)

Damit startet das Backend beim Einschalten des Pi von selbst, und du musst kein Fenster offen lassen.

Den ganzen Block auf einmal kopieren und einfügen:

```
sudo tee /etc/systemd/system/darts.service > /dev/null <<'EOF'
[Unit]
Description=DartsCounter Backend
After=network-online.target

[Service]
User=admin
WorkingDirectory=/home/admin
ExecStart=/usr/bin/java -Xmx400m -jar /home/admin/DartsCounter_backend-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload
sudo systemctl enable --now darts
```

Prüfen:

```
systemctl status darts
```

Dort muss `active (running)` stehen (beenden mit `q`). Der Start dauert auf dem Pi 3 bis zu 1 Minute.

**Browser:** `http://darts-counter:8080/api/hallo` muss „Verbindung steht!“ zeigen.

Hat `java` einen anderen Pfad (Fehler `status=203/EXEC`): Mit `which java` den Pfad anzeigen und in der Zeile `ExecStart=` statt `/usr/bin/java` eintragen (`sudo nano /etc/systemd/system/darts.service`, speichern mit Strg+O, Enter, Strg+X), danach `sudo systemctl daemon-reload` und `sudo systemctl restart darts`.

## 5. App (Oberfläche) aufspielen

**PC-Terminal**, im Ordner `DartsCounter`:

```
scp -r DartsCounter_frontend admin@darts-counter:~/
```

**Pi-Terminal:**

```
sudo cp -r ~/DartsCounter_frontend/. /var/www/html/
sudo chmod -R a+rX /var/www/html
```

**Browser:** `http://darts-counter/mainPages/index.html`

Beim ersten Öffnen oben rechts **Anmelden** → „Erster Start“ → Admin-Konto anlegen. Den **Zugangscode** aufschreiben, er wird nur einmal angezeigt.

## 6. Kameras anschließen und kalibrieren

### 6.1 Strom

- Drei Kameras ziehen mehr Strom, als der Pi 3 über USB liefern kann. Am besten einen **USB-Hub mit eigenem Netzteil** verwenden.
- Pi-Netzteil: 5,1 V, mindestens 2,5 A.
- Prüfen (Pi-Terminal): `vcgencmd get_throttled`. `throttled=0x0` heißt: kein Stromproblem.

### 6.2 Kameras prüfen

**Pi-Terminal:**

```
v4l2-ctl --list-devices
```

Jede „Web Camera“ hat zwei Einträge, z. B. `/dev/video0` und `/dev/video1`. Gebraucht wird die erste (gerade) Nummer. Bei drei Kameras: `0`, `2`, `4`.

**Browser:** `http://darts-counter:8080/services/scan-cameras` muss `[0,2,4]` zeigen.

- Kameras immer in dieselben USB-Buchsen stecken, sonst ändern sich die Nummern.
- Welche Kamera welche ist: eine abziehen, `v4l2-ctl --list-devices` erneut ausführen, die fehlende Nummer ist diese Kamera. Kabel beschriften (1 = video0, 2 = video2, 3 = video4).
- Andere Nummern: in `DartsCounter_backend/src/main/resources/application.properties` bei `dartscounter.cameras` eintragen, dann [Update](#7-updates-einspielen).

### 6.3 Kalibrieren

1. App → **Einstellungen** → **Kameras kalibrieren** → **Kalibrierung starten**.
2. Pfeil werfen, warten bis „Wurf erkannt“ erscheint.
3. Genau die Stelle anklicken, an der der Pfeil steckt, und bestätigen.
4. Wird das Herausziehen als Wurf erkannt: **Verwerfen**.
5. Mindestens 8, besser 15 Würfe, über die ganze Scheibe verteilt.
6. **Fertig**. Ziel: mittlerer Fehler unter 2 mm.

Neu kalibrieren, wenn eine Kamera verstellt wurde oder die Auflösung geändert wurde.

## 7. Updates einspielen

Wenn es eine neue Version gibt:

**Backend** (PC-Terminal, im Ordner `DartsCounter`):

```
git pull
cd DartsCounter_backend
```

- Windows: `.\mvnw.cmd -DskipTests package`
- Mac: `./mvnw -DskipTests package`

```
scp target/DartsCounter_backend-0.0.1-SNAPSHOT.jar admin@darts-counter:~/
ssh admin@darts-counter "sudo systemctl restart darts"
```

**App** (PC-Terminal, im Ordner `DartsCounter`):

```
scp -r DartsCounter_frontend admin@darts-counter:~/
ssh admin@darts-counter "sudo cp -r ~/DartsCounter_frontend/. /var/www/html/ && sudo chmod -R a+rX /var/www/html"
```

Danach im Browser **Strg+F5** (Mac: Cmd+Shift+R), damit die neue Version geladen wird.

## 8. Wichtige Befehle

**Pi-Terminal:**

| Was | Befehl |
|---|---|
| Backend-Status | `systemctl status darts` |
| Backend-Log live ansehen (beenden mit Strg+C) | `journalctl -u darts -f` |
| Backend neu starten | `sudo systemctl restart darts` |
| Backend stoppen | `sudo systemctl stop darts` |
| Kameras anzeigen | `v4l2-ctl --list-devices` |
| Stromprobleme | `vcgencmd get_throttled` |
| Speicher / Auslastung | `free -m` und `uptime` |
| IP-Adresse des Pi | `hostname -I` |
| Pi neu starten | `sudo reboot` |
| Pi ausschalten | `sudo shutdown now` (erst wenn die grüne LED aus ist, Strom ziehen) |

**Adressen im Browser:**

| Was | Adresse |
|---|---|
| App | `http://darts-counter/mainPages/index.html` |
| Backend-Test | `http://darts-counter:8080/api/hallo` |
| Kameras | `http://darts-counter:8080/services/scan-cameras` |
| Kalibrierung (Rohdaten) | `http://darts-counter:8080/services/calibration` |
| Letzter erkannter Treffer | `http://darts-counter:8080/services/lastHit` |

Gespeicherte Daten (Spieler, Spiele, Konten, Kalibrierung) liegen auf dem Pi in `~/dartscounter-data`. Backup: App → Einstellungen → „Backup herunterladen“.

## 9. Hilfe bei Fehlern

| Meldung | Ursache | Lösung |
|---|---|---|
| `ping` / `ssh`: *Connection timed out* oder *Host nicht gefunden* | Pi aus, hängt oder nicht im Netz | Strom 10 s ziehen, wieder einstecken, 2 Min. warten. Statt `darts-counter` die IP aus dem Router nehmen (FRITZ!Box: Heimnetz → Netzwerk). Unter Windows hilft manchmal `darts-counter.local`. |
| `ssh`: *REMOTE HOST IDENTIFICATION HAS CHANGED* | SD-Karte neu beschrieben | PC-Terminal: `ssh-keygen -R darts-counter`, dann erneut verbinden. |
| Browser: Seite nicht erreichbar bei `:8080` | Backend läuft nicht | `systemctl status darts`, Log mit `journalctl -u darts -n 50`. Neu starten mit `sudo systemctl restart darts`. |
| App: „Backend nicht erreichbar“ | Backend läuft nicht oder Pi überlastet | Erst `http://darts-counter:8080/api/hallo` testen. Klappt das: Seite mit Strg+F5 neu laden. Hängt der Pi bei laufenden Kameras: weniger Kameras testen, `vcgencmd get_throttled` prüfen. |
| „Bitte anmelden“ im Heimnetz | alte Backend-Version | [Update](#7-updates-einspielen) einspielen. |
| Browser: *404 Not Found* | App-Dateien fehlen oder liegen falsch | [Schritt 5](#5-app-oberfläche-aufspielen) wiederholen. `ls /var/www/html` muss `mainPages` zeigen. |
| Browser: *403 Forbidden*, `ls`: *Permission denied* | fehlende Leserechte | `sudo chmod -R a+rX /var/www/html` |
| `scan-cameras` zeigt weniger als 3 Kameras | Kamera nicht erkannt, falsche Nummer oder fehlende Rechte | `v4l2-ctl --list-devices`, `groups` muss `video` enthalten. Kameras neu einstecken. |
| `vcgencmd get_throttled` nicht `0x0` | zu wenig Strom | Stärkeres Netzteil, USB-Hub mit eigenem Netzteil für die Kameras. |
| `fswebcam` / Kamera: *Device or resource busy* | Backend benutzt die Kamera gerade | `sudo systemctl stop darts`, danach wieder `start`. |
| Kalibrierung erkennt Würfe, obwohl keiner geworfen wurde | Rauschen, Schatten, Licht | Gleichmäßiges Licht, nichts vor den Kameras bewegen. In `application.properties` `dartscounter.detection.stable-frames` erhöhen (z. B. 5), dann [Update](#7-updates-einspielen). Werden echte Würfe übersehen: Wert senken. |
| Pi hängt komplett, sobald Kameras laufen | Überlastung oder Strom | Neueste Version einspielen (640×480), Kameras einzeln dazunehmen, Strom prüfen. |

Hilft nichts davon: die genaue Meldung und die Ausgabe von `journalctl -u darts -n 50` kopieren und nachfragen.
