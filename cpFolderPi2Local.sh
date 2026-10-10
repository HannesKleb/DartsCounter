#!/bin/bash
set -e

# --- KONFIGURATION ---
REMOTE_USER="admin"                # Benutzername auf dem Pi
REMOTE_HOST="darts-counter"    # IP-Adresse deines Pi
REMOTE_DIR="/home/admin/DartsCounter_backend"  # Pfad auf dem Pi
LOCAL_DIR="./DartsCounter_backend"     # Zielpfad lokal (aktueller Ordner)
TMP_DIR="./DartsCounter_backend_tmp"

echo "Copy: $REMOTE_HOST -> $LOCAL_DIR"

# erst in temporären Ordner kopieren, lokalen Stand nur bei Erfolg ersetzen
rm -rf "$TMP_DIR"
scp -r "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}" "$TMP_DIR"
rm -rf "$LOCAL_DIR"
mv "$TMP_DIR" "$LOCAL_DIR"
