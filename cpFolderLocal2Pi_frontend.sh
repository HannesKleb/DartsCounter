#!/bin/bash
set -e

# --- KONFIGURATION ---
REMOTE_USER="root"                # Benutzername auf dem Pi
REMOTE_HOST="darts-counter"    # IP-Adresse deines Pi
DIR_NAME="html"
REMOTE_DIR="/var/www/"  # Pfad auf dem Pi
LOCAL_DIR="./DartsCounter_frontend"     # Quellpfad lokal

echo "Copy: $LOCAL_DIR -> $REMOTE_HOST:${REMOTE_DIR}${DIR_NAME}"

# erst in temporären Ordner kopieren, alten Stand erst danach ersetzen
ssh ${REMOTE_USER}@${REMOTE_HOST} "rm -rf ${REMOTE_DIR}${DIR_NAME}_tmp"
scp -r "$LOCAL_DIR" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}${DIR_NAME}_tmp"
ssh ${REMOTE_USER}@${REMOTE_HOST} "rm -rf ${REMOTE_DIR}${DIR_NAME} && mv ${REMOTE_DIR}${DIR_NAME}_tmp ${REMOTE_DIR}${DIR_NAME}"
