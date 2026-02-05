#!/bin/bash

# --- KONFIGURATION ---
REMOTE_USER="root"                # Benutzername auf dem Pi
REMOTE_HOST="darts-counter"    # IP-Adresse deines Pi
DIR_NAME="html"
REMOTE_DIR="/var/www/"  # Pfad auf dem Pi
LOCAL_DIR="./"     # Zielpfad lokal (aktueller Ordner)

echo "Copy: $REMOTE_HOST -> $LOCAL_DIR"


ssh ${REMOTE_USER}@${REMOTE_HOST} "rm -rf ${REMOTE_DIR}${DIR_NAME}" && scp -r "./DartsCounter_frontend" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}" && ssh ${REMOTE_USER}@${REMOTE_HOST} "mv /var/www/DartsCounter_frontend /var/www/html"
