#!/bin/bash

# --- KONFIGURATION ---
REMOTE_USER="admin"                # Benutzername auf dem Pi
REMOTE_HOST="darts-counter"    # IP-Adresse deines Pi
DIR_NAME="DartsCounter_backend"
REMOTE_DIR="/home/admin/"  # Pfad auf dem Pi
LOCAL_DIR="./DartsCounter_backend"     # Zielpfad lokal (aktueller Ordner)

echo "Copy: $REMOTE_HOST -> $LOCAL_DIR"


ssh ${REMOTE_USER}@${REMOTE_HOST} "rm -rf ${REMOTE_DIR}${DIR_NAME}" && scp -r "$LOCAL_DIR" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}"
