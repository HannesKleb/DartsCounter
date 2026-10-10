import math
from dataclasses import dataclass
from typing import Optional, List

# =========================
# Konstanten
# =========================

DARTBOARD_RADIUS_CM = 22.5  # äußerster Rand der Scheibe

# =========================
# Datenklassen
# =========================

@dataclass
class Point:
    x: float
    y: float

@dataclass
class Camera:
    position: Point      # Position in cm
    angle_deg: float     # Blickrichtung (0 = Richtung Dartzentrum)
    focal_length_px: float

# =========================
# Winkel aus Pixelkoordinaten
# =========================

def angle_from_pixel_deg(camera: Camera, pixel_x: float) -> float:
    """
    Berechnet den Winkel in Grad relativ zur Kamera aus Pixelkoordinaten
    """
    angle_rad = math.radians(camera.angle_deg) + math.atan2(pixel_x, camera.focal_length_px)
    return math.degrees(angle_rad)

def angle_from_pixel_rad(camera: Camera, pixel_x: float) -> float:
    """
    Winkel in Radiant, für Berechnungen
    """
    return math.radians(camera.angle_deg) + math.atan2(pixel_x, camera.focal_length_px)

# =========================
# Schnittpunkt zweier Kamerastrahlen
# =========================

def intersect(cam1: Camera, angle1_rad: float, cam2: Camera, angle2_rad: float) -> Optional[Point]:
    x1, y1 = cam1.position.x, cam1.position.y
    x2, y2 = cam2.position.x, cam2.position.y

    dx1, dy1 = math.sin(angle1_rad), math.cos(angle1_rad)
    dx2, dy2 = math.sin(angle2_rad), math.cos(angle2_rad)

    det = dx1 * dy2 - dy1 * dx2
    if abs(det) < 1e-6:
        return None  # parallele Strahlen

    t = ((x2 - x1) * dy2 - (y2 - y1) * dx2) / det
    return Point(x1 + t * dx1, y1 + t * dy1)

# =========================
# Triangulation
# =========================

def triangulate_two(cam1: Camera, pixel1: float,
                    cam2: Camera, pixel2: float) -> Optional[Point]:
    a1 = angle_from_pixel_rad(cam1, pixel1)
    a2 = angle_from_pixel_rad(cam2, pixel2)
    return intersect(cam1, a1, cam2, a2)

def triangulate_three(cam1: Camera, pixel1: float,
                      cam2: Camera, pixel2: float,
                      cam3: Camera, pixel3: float) -> Optional[Point]:
    points: List[Optional[Point]] = [
        triangulate_two(cam1, pixel1, cam2, pixel2),
        triangulate_two(cam2, pixel2, cam3, pixel3),
        triangulate_two(cam3, pixel3, cam1, pixel1),
    ]
    valid = [p for p in points if p is not None]
    if not valid:
        return None
    x = sum(p.x for p in valid)/len(valid)
    y = sum(p.y for p in valid)/len(valid)
    return Point(x, y)

# =========================
# Dartboard Koordinaten
# =========================

def dart_radius_cm(p: Point) -> float:
    return math.sqrt(p.x**2 + p.y**2)

def dart_radius_normalized(p: Point) -> float:
    return min(max(dart_radius_cm(p)/DARTBOARD_RADIUS_CM, 0), 1)

def dart_angle_deg(p: Point) -> float:
    """
    Winkel in Grad:
    0° = Mitte 20 oben
    Rechts positiv, links negativ
    """
    return math.degrees(math.atan2(p.x, p.y))

# =========================
# Kamerapositionen automatisch setzen
# =========================

def create_cameras_around_board(radius_cm: float, fov_deg: float, image_width_px: int) -> List[Camera]:
    """
    Platziert 3 Kameras gleichmäßig um die Dartscheibe.
    Berechnet automatisch die Brennweite aus FOV und Bildbreite.
    """
    cameras: List[Camera] = []
    angles_deg = [0, 120, -120]  # Kamera 1 oben (20), Kamera 2 und 3 je 120° versetzt
    # Brennweite aus FOV und Bildbreite
    focal_length_px = image_width_px / (2 * math.tan(math.radians(fov_deg)/2))
    for angle in angles_deg:
        rad = math.radians(angle)
        x = radius_cm * math.sin(rad)
        y = -radius_cm * math.cos(rad)  # y negativ = vorne zur Scheibe
        # Blickrichtung zeigt zur Scheibenmitte: (-sin a, cos a) entspricht Winkel -a
        cameras.append(Camera(position=Point(x, y), angle_deg=-angle, focal_length_px=focal_length_px))
    return cameras

# =========================
# Main / Beispiel
# =========================

if __name__ == "__main__":
    # --- Einstellungen ---
    radius_cm = 50         # Abstand der Kameras zum Bullseye
    fov_deg = 60           # horizontales Sichtfeld der Kameras in Grad
    image_width_px = 1920  # Bildbreite der Kamera in Pixel

    # Pixelwerte für jede Kamera (-PIXEL_MAX .. +PIXEL_MAX)
    pixel1 = 0   # Kamera 1 sieht Pfeil leicht rechts
    pixel2 = 0  # Kamera 2 sieht Pfeil links
    pixel3 = 0    # Kamera 3 sieht Pfeil leicht rechts

    # Kameras erstellen
    cams = create_cameras_around_board(radius_cm, fov_deg, image_width_px)
    cam1, cam2, cam3 = cams

    # --- Winkel jeder Kamera in Grad ---
    print("Winkel jeder Kamera basierend auf Pixeln:")
    print(f"Kamera 1: Pixel {pixel1} → Winkel {angle_from_pixel_deg(cam1, pixel1):.2f}°")
    print(f"Kamera 2: Pixel {pixel2} → Winkel {angle_from_pixel_deg(cam2, pixel2):.2f}°")
    print(f"Kamera 3: Pixel {pixel3} → Winkel {angle_from_pixel_deg(cam3, pixel3):.2f}°")

    # --- Triangulation 3 Kameras ---
    hit3 = triangulate_three(cam1, pixel1, cam2, pixel2, cam3, pixel3)
    if hit3:
        print("\n🎯 Treffer mit 3 Kameras")
        print(f"X: {hit3.x:.2f} cm, Y: {hit3.y:.2f} cm")
        print(f"Normierter Radius: {dart_radius_normalized(hit3):.3f}")
        print(f"Scheibenwinkel: {dart_angle_deg(hit3):.2f}°")

    # --- Triangulation 2 Kameras ---
    hit2 = triangulate_two(cam1, pixel1, cam2, pixel2)
    if hit2:
        print("\n🎯 Treffer mit 2 Kameras")
        print(f"X: {hit2.x:.2f} cm, Y: {hit2.y:.2f} cm")
        print(f"Normierter Radius: {dart_radius_normalized(hit2):.3f}")
        print(f"Scheibenwinkel: {dart_angle_deg(hit2):.2f}°")
