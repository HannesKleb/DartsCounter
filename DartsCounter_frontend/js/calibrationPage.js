/********* Kameras kalibrieren *********/
// Ablauf: Testwurf -> Backend erkennt ihn (pendingCameras) -> Stelle auf der Scheibe genau markieren
// -> "Position bestätigen" schickt {x, y} in mm (Ursprung Bullseye, x rechts, y unten) -> nach genug
// Würfen "Fertig", das Backend berechnet die Kameras.

const POLL_MS = 500;
const LENS_SPAN_MM = 24;          // Ausschnitt der Lupe (Breite in mm)
const SLOW_TIMEOUT_MS = 15000;    // Kameras starten / Berechnung dauern länger
const POINTS_KEY = "calibration.points"; // nur Klickpositionen dieser Kalibrierung, nichts Persönliches

let status = null;
let pollTimer = null;
let marker = null;         // {x, y} in mm, solange eine Position gewählt wird
let dragging = false;
let busy = false;
let boardSvg = null;
let lensSvg = null;

document.addEventListener("DOMContentLoaded", async () => {
    boardSvg = createBoardSvg();
    boardSvg.classList.add("calib-svg");
    boardSvg.append(svgEl("g", { class: "calib-points" }), svgEl("g", { class: "calib-marker" }));
    document.getElementById("calib-board").replaceChildren(boardSvg);

    lensSvg = createBoardSvg();
    lensSvg.removeAttribute("role");
    lensSvg.append(svgEl("g", { class: "calib-points" }));
    document.getElementById("calib-lens").replaceChildren(lensSvg, h("div", { class: "calib-lens-cross" }));

    initBoardInput();
    document.getElementById("start-btn").addEventListener("click", startCalibration);
    document.getElementById("confirm-btn").addEventListener("click", confirmPoint);
    document.getElementById("skip-btn").addEventListener("click", () => action("/calibration/skip", "Verworfen."));
    document.getElementById("undo-btn").addEventListener("click", undoPoint);
    document.getElementById("finish-btn").addEventListener("click", finishCalibration);
    document.getElementById("cancel-btn").addEventListener("click", cancelCalibration);
    document.querySelectorAll("[data-nudge]").forEach((btn) => {
        btn.addEventListener("click", () => {
            const [dx, dy] = btn.dataset.nudge.split(",").map(Number);
            nudge(dx, dy);
        });
    });
    document.addEventListener("keydown", onKey);

    // Abfrage beim Verlassen der Seite stoppen, im Hintergrund pausieren
    window.addEventListener("pagehide", stopPolling);
    document.addEventListener("visibilitychange", () => {
        if (document.hidden) stopPolling();
        else if (status?.active) startPolling();
    });

    await refresh();
});

/********* Backend *********/

// Aufruf an /services/calibration...; Fehler (auch 400/409) als Error mit der Meldung vom Backend
async function api(path, { method = "GET", body, timeoutMs } = {}) {
    let response;
    try {
        response = await servicesFetch(path, {
            method,
            cache: "no-store",
            headers: body === undefined ? {} : { "Content-Type": "application/json" },
            body: body === undefined ? undefined : JSON.stringify(body)
        }, timeoutMs ? { timeoutMs } : undefined);
    } catch (error) {
        // 401/403 kommen von servicesFetch schon mit Meldung
        if (error.name === "AbortError" || error instanceof TypeError || /übersprungen/.test(error.message)) {
            throw new Error("Backend nicht erreichbar. Läuft es auf dem Pi?");
        }
        throw error;
    }
    let data = null;
    try {
        data = response.status === 204 ? null : await response.json();
    } catch {
        data = null;
    }
    if (!response.ok) throw new Error(data?.message ?? `Fehler vom Backend (${response.status}).`);
    return data;
}

async function refresh() {
    try {
        applyStatus(await api("/calibration"));
        showError(null);
    } catch (error) {
        showError(error.message);
        if (!status) renderIntro(null);
    }
}

function startPolling() {
    if (pollTimer) return;
    pollTimer = setInterval(async () => {
        if (busy) return;
        try {
            applyStatus(await api("/calibration"));
            if (document.getElementById("calib-error").dataset.source === "poll") showError(null);
        } catch (error) {
            showError(error.message, "poll");
        }
    }, POLL_MS);
}

function stopPolling() {
    clearInterval(pollTimer);
    pollTimer = null;
}

// Knöpfe sperren, solange ein Aufruf läuft
async function withBusy(task) {
    if (busy) return;
    busy = true;
    setButtonsDisabled(true);
    try {
        await task();
        showError(null);
    } catch (error) {
        showError(error.message);
    } finally {
        busy = false;
        render();
    }
}

async function startCalibration() {
    await withBusy(async () => {
        savePoints([]);
        applyStatus(await api("/calibration/start", { method: "POST", timeoutMs: SLOW_TIMEOUT_MS }));
    });
}

async function confirmPoint() {
    if (!marker) return;
    const point = { x: round1(marker.x), y: round1(marker.y) };
    await withBusy(async () => {
        const next = await api("/calibration/point", { method: "POST", body: point });
        savePoints([...loadPoints(), point]);
        marker = null;
        applyStatus(next);
    });
}

async function undoPoint() {
    await withBusy(async () => {
        const next = await api("/calibration/undo", { method: "POST" });
        // nur entfernen, wenn das Backend wirklich einen Klick zurückgenommen hat
        if (status && next.samples < status.samples) savePoints(loadPoints().slice(0, -1));
        applyStatus(next);
    });
}

async function action(path) {
    await withBusy(async () => {
        marker = null;
        applyStatus(await api(path, { method: "POST" }));
    });
}

async function finishCalibration() {
    await withBusy(async () => {
        const result = await api("/calibration/finish", { method: "POST", timeoutMs: SLOW_TIMEOUT_MS });
        marker = null;
        applyStatus(result);
        renderResult(result);
    });
}

async function cancelCalibration() {
    if (!confirm("Kalibrierung abbrechen? Die bisherigen Würfe gehen verloren, die gespeicherte Kalibrierung bleibt.")) return;
    await withBusy(async () => {
        marker = null;
        savePoints([]);
        applyStatus(await api("/calibration/cancel", { method: "POST" }));
    });
}

/********* Status anzeigen *********/

function applyStatus(next) {
    const wasPending = Boolean(status?.pendingCameras);
    status = next;
    if (!status?.pendingCameras) {
        marker = null;
    } else if (!wasPending) {
        // neuer Wurf: Ergebnis vom letzten Mal ausblenden
        document.getElementById("calib-result").hidden = true;
    }
    if (status?.active) startPolling();
    else stopPolling();
    render();
}

function render() {
    const active = Boolean(status?.active);
    document.getElementById("calib-intro").hidden = active;
    document.getElementById("calib-run").hidden = !active;
    if (!active) {
        renderIntro(status);
        return;
    }
    renderRun();
}

function renderIntro(current) {
    const state = document.getElementById("calib-state");
    if (!current) {
        state.replaceChildren(h("p", { class: "hint", text: "Status konnte nicht geladen werden." }));
        return;
    }
    if (!current.calibrated) {
        state.replaceChildren(h("p", { text: "Noch nicht kalibriert. Die Erkennung rechnet mit der geplanten Montage und kann dadurch über 1 cm danebenliegen." }));
    } else {
        state.replaceChildren(
            h("p", { text: `Kalibriert am ${formatDate(current.createdAt)}.` }),
            h("p", { class: "hint", text: `Mittlerer Fehler ${mm(current.errorMm)}, größter Fehler ${mm(current.maxErrorMm)} (${rating(current.errorMm).label}).` }));
    }
    setButtonsDisabled(busy);
}

function renderRun() {
    const pending = status.pendingCameras;
    const prompt = document.getElementById("calib-prompt");
    prompt.classList.toggle("detected", Boolean(pending));
    if (pending) {
        const names = pending.map((c) => `Kamera ${c + 1}`);
        prompt.replaceChildren(
            h("strong", { text: "Wurf erkannt" }),
            h("span", { text: ` von ${joinGerman(names)}. ` }),
            h("span", { text: marker ? "Markierung genau ausrichten und bestätigen." : "Tippe auf der Scheibe die Stelle an, an der der Pfeil steckt." }));
    } else {
        prompt.replaceChildren(
            h("strong", { text: "Wirf einen Pfeil" }),
            h("span", { text: " und warte, bis er erkannt wird." }));
    }

    document.getElementById("calib-count").textContent =
        `${status.samples} von ${status.recommendedSamples} Würfen (mindestens ${status.minSamples})`;
    document.getElementById("calib-cameras").replaceChildren(...(status.cameras ?? []).map((cam) =>
        h("span", {
            class: `calib-cam${pending?.includes(cam.camera) ? " seen" : ""}`,
            text: `Kamera ${cam.camera + 1}: ${cam.samples}×`
        })));

    renderWarnings();
    renderPoints();
    renderMarker();

    boardSvg.classList.toggle("selectable", Boolean(pending));
    document.getElementById("calib-fine").hidden = !marker;
    setButtonsDisabled(busy);
}

function setButtonsDisabled(disabled) {
    const pending = Boolean(status?.pendingCameras);
    const set = (id, off) => { const el = document.getElementById(id); if (el) el.disabled = disabled || off; };
    set("start-btn", false);
    set("confirm-btn", !marker || !pending);
    set("skip-btn", !pending);
    set("undo-btn", !status || status.samples === 0);
    set("finish-btn", !status || status.samples < status.minSamples || pending);
    set("cancel-btn", false);
    document.querySelectorAll("[data-nudge]").forEach((btn) => { btn.disabled = disabled || !marker; });
}

// Hinweise: fehlende Bereiche, Kameras, die den Pfeil seltener sehen
function renderWarnings() {
    const list = document.getElementById("calib-warnings");
    const warnings = [];
    const points = loadPoints();

    if (points.length >= 4) {
        const missing = missingAreas(points);
        if (missing.length) warnings.push(`Noch keine Würfe ${joinGerman(missing)}. Verteile die Würfe über die ganze Scheibe.`);
    }

    const cams = status.cameras ?? [];
    const most = Math.max(0, ...cams.map((c) => c.samples));
    if (most >= 4) {
        for (const cam of cams) {
            if (cam.samples < most * 0.6) {
                warnings.push(`Kamera ${cam.camera + 1} hat den Pfeil deutlich seltener gesehen (${cam.samples} von ${status.samples} Würfen). Kamera prüfen und ausrichten.`);
            }
        }
    }
    list.replaceChildren(...warnings.map((text) => h("li", { text })));
    list.hidden = warnings.length === 0;
}

// grobe Bereiche: innen/außen und vier Seiten
function missingAreas(points) {
    const radius = (p) => Math.hypot(p.x, p.y);
    const missing = [];
    if (!points.some((p) => radius(p) < 70)) missing.push("innen (nahe Bull)");
    if (!points.some((p) => radius(p) > 120)) missing.push("außen (nahe Double)");
    const sides = { oben: false, rechts: false, unten: false, links: false };
    for (const p of points) {
        if (radius(p) < 30) continue;
        const angle = (Math.atan2(p.x, -p.y) * 180 / Math.PI + 360) % 360; // 0° = oben, im Uhrzeigersinn
        if (angle >= 315 || angle < 45) sides.oben = true;
        else if (angle < 135) sides.rechts = true;
        else if (angle < 225) sides.unten = true;
        else sides.links = true;
    }
    for (const [side, hit] of Object.entries(sides)) if (!hit) missing.push(side);
    return missing;
}

function renderPoints() {
    const dots = loadPoints().map((p) => svgEl("circle", { cx: p.x, cy: p.y, r: 3.2, class: "calib-dot" }));
    boardSvg.querySelector(".calib-points").replaceChildren(...dots);
    lensSvg.querySelector(".calib-points").replaceChildren(
        ...loadPoints().map((p) => svgEl("circle", { cx: p.x, cy: p.y, r: 0.8, class: "calib-dot" })));
}

function renderMarker() {
    const group = boardSvg.querySelector(".calib-marker");
    const lens = document.getElementById("calib-lens");
    if (!marker) {
        group.replaceChildren();
        lens.hidden = true;
        return;
    }
    const { x, y } = marker;
    group.replaceChildren(
        svgEl("circle", { cx: x, cy: y, r: 9, class: "calib-marker-ring" }),
        svgEl("line", { x1: x - 14, y1: y, x2: x - 4, y2: y, class: "calib-marker-line" }),
        svgEl("line", { x1: x + 4, y1: y, x2: x + 14, y2: y, class: "calib-marker-line" }),
        svgEl("line", { x1: x, y1: y - 14, x2: x, y2: y - 4, class: "calib-marker-line" }),
        svgEl("line", { x1: x, y1: y + 4, x2: x, y2: y + 14, class: "calib-marker-line" }));

    // Lupe: Ausschnitt um die Markierung, über bzw. neben dem Finger
    lensSvg.setAttribute("viewBox", `${x - LENS_SPAN_MM / 2} ${y - LENS_SPAN_MM / 2} ${LENS_SPAN_MM} ${LENS_SPAN_MM}`);
    positionLens();
    lens.hidden = false;

    const field = fieldFromXY(x, y);
    document.getElementById("calib-position").textContent =
        `Feld ${field === "M" ? "außerhalb" : fieldLabel(field)}, ${mm(Math.hypot(x, y))} von der Mitte. Pfeiltasten verschieben um 1 mm, Enter bestätigt.`;
}

function positionLens() {
    const lens = document.getElementById("calib-lens");
    const wrap = document.getElementById("calib-board-wrap").getBoundingClientRect();
    const point = toScreen(marker.x, marker.y);
    const size = lens.offsetWidth || 150;
    const gap = dragging ? 70 : 40; // beim Ziehen weiter weg, damit der Finger sie nicht verdeckt
    let left = point.x - wrap.left - size / 2;
    let top = point.y - wrap.top - size - gap;
    if (top < -wrap.top + 8) top = point.y - wrap.top + gap; // oben kein Platz: darunter
    left = Math.max(-8, Math.min(left, wrap.width - size + 8));
    lens.style.transform = `translate(${Math.round(left)}px, ${Math.round(top)}px)`;
}

function renderResult(result) {
    const section = document.getElementById("calib-result");
    const verdict = rating(result.errorMm);
    section.replaceChildren(...[
        h("h3", { text: "Ergebnis" }),
        h("p", { class: `calib-verdict ${verdict.cls}`, text: verdict.label }),
        h("div", { class: "calib-figures" },
            figure("Mittlerer Fehler", mm(result.errorMm)),
            figure("Größter Fehler", mm(result.maxErrorMm)),
            ...(result.cameras ?? []).map((cam) =>
                figure(`Kamera ${cam.camera + 1}`, cam.calibrated ? `${fmtNum(cam.rmsPx, 1)} px` : "nicht kalibriert"))),
        verdict.tips
            ? h("ul", { class: "calib-tips" }, verdict.tips.map((tip) => h("li", { text: tip })))
            : null,
        h("div", { class: "settings-row" },
            h("button", { class: "input-btn", text: "Neu kalibrieren", onclick: startCalibration }),
            h("button", { class: "input-btn primary-btn", text: "Zu den Einstellungen", onclick: () => bar("settings.html") }))
    ].filter(Boolean));
    section.hidden = false;
    section.classList.add("panel-in");
}

function figure(label, value) {
    return h("div", { class: "calib-figure" },
        h("span", { class: "hint", text: label }),
        h("strong", { text: value }));
}

function rating(errorMm) {
    if (errorMm === null || errorMm === undefined) return { label: "unbekannt", cls: "" };
    if (errorMm <= 2) return { label: "sehr gut", cls: "good" };
    if (errorMm <= 4) return { label: "brauchbar", cls: "ok" };
    return {
        label: "neu kalibrieren",
        cls: "bad",
        tips: [
            "Mehr Würfe machen (15 bis 20), über die ganze Scheibe verteilt.",
            "Genauer klicken: Markierung mit der Lupe auf die Einstichstelle setzen.",
            "Kameras fest montieren, damit sie sich beim Werfen nicht bewegen."
        ]
    };
}

/********* Markierung setzen und verschieben *********/

function initBoardInput() {
    boardSvg.addEventListener("pointerdown", (event) => {
        if (!status?.pendingCameras || busy) return;
        event.preventDefault();
        dragging = true;
        boardSvg.setPointerCapture(event.pointerId);
        moveMarkerTo(event);
    });
    boardSvg.addEventListener("pointermove", (event) => {
        if (dragging) moveMarkerTo(event);
    });
    const end = () => {
        if (!dragging) return;
        dragging = false;
        if (marker) positionLens();
    };
    boardSvg.addEventListener("pointerup", end);
    boardSvg.addEventListener("pointercancel", end);
    window.addEventListener("resize", () => { if (marker) positionLens(); });
}

function moveMarkerTo(event) {
    const p = toBoard(event.clientX, event.clientY);
    marker = clampToBoard(p.x, p.y);
    renderRun();
}

function nudge(dx, dy) {
    if (!marker || busy) return;
    marker = clampToBoard(marker.x + dx, marker.y + dy);
    renderRun();
}

function onKey(event) {
    if (!marker || event.target.closest?.("input, select, textarea")) return;
    const steps = { ArrowUp: [0, -1], ArrowDown: [0, 1], ArrowLeft: [-1, 0], ArrowRight: [1, 0] };
    if (steps[event.key]) {
        event.preventDefault();
        nudge(...steps[event.key]);
    } else if (event.key === "Enter" && !event.target.closest?.("button")) {
        event.preventDefault();
        confirmPoint();
    } else if (event.key === "Escape") {
        marker = null;
        renderRun();
    }
}

// Bildschirm <-> Scheiben-Koordinaten (mm, wie die SVG-Koordinaten von createBoardSvg)
function toBoard(clientX, clientY) {
    const point = new DOMPoint(clientX, clientY).matrixTransform(boardSvg.getScreenCTM().inverse());
    return { x: point.x, y: point.y };
}

function toScreen(x, y) {
    return new DOMPoint(x, y).matrixTransform(boardSvg.getScreenCTM());
}

function clampToBoard(x, y) {
    const r = Math.hypot(x, y);
    const max = BOARD_RADIUS.board;
    return r > max ? { x: (x / r) * max, y: (y / r) * max } : { x, y };
}

/********* Hilfen *********/

function loadPoints() {
    try {
        const list = JSON.parse(sessionStorage.getItem(POINTS_KEY) ?? "[]");
        return Array.isArray(list) ? list : [];
    } catch {
        return [];
    }
}

function savePoints(points) {
    try {
        sessionStorage.setItem(POINTS_KEY, JSON.stringify(points));
    } catch {
        // egal, nur Anzeige
    }
}

function showError(message, source = "action") {
    const el = document.getElementById("calib-error");
    el.hidden = !message;
    el.textContent = message ?? "";
    el.dataset.source = message ? source : "";
}

function round1(value) {
    return Math.round(value * 10) / 10;
}

function fmtNum(value, digits) {
    return (value ?? 0).toLocaleString("de-DE", { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

function mm(value) {
    return value === null || value === undefined ? "-" : `${fmtNum(value, 1)} mm`;
}

function joinGerman(items) {
    if (items.length <= 1) return items.join("");
    return `${items.slice(0, -1).join(", ")} und ${items.at(-1)}`;
}
