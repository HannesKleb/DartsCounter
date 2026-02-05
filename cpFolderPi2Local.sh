#!/bin/bash

# --- KONFIGURATION ---
REMOTE_USER="admin"                # Benutzername auf dem Pi
REMOTE_HOST="darts-counter"    # IP-Adresse deines Pi
REMOTE_DIR="/home/admin/DartsCounter_backend"  # Pfad auf dem Pi
LOCAL_DIR="./DartsCounter_backend"     # Zielpfad lokal (aktueller Ordner)

echo "Copy: $REMOTE_HOST -> $LOCAL_DIR"

rm -r "$LOCAL_DIR"

scp -r "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}" "./"
