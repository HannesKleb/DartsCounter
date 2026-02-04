import math
from typing import Optional


# =========================
# Datenklassen
# =========================

class Point:
    def __init__(self, x: float, y: float):
        self.x = x
        self.y = y


class Camera:
    def __init__(self, x: float, y: float):
        self.position = Point(x, y)


# =========================
# Winkel aus Bilddaten
# =========================

def angle_from_image(
        image_center_x: float,
        detected_x: float,
        focal_length_px: float) -> float:
    """
    Berechnet den Winkel (Radiant) aus Bildkoordinaten
    """
    dx = detected_x - image_center_x
    return math.atan2(dx, focal_length_px)


# =========================
# Triangulation
# =========================

def intersect(cam1: Camera, angle1: float,
              cam2: Camera, angle2: float) -> Optional[Point]:
    """
    Schnittpunkt zweier Kamerastrahlen
    """
    x1, y1 = cam1.position.x, cam1.position.y
    x2, y2 = cam2.position.x, cam2.position.y

    dx1, dy1 = math.sin(angle1), math.cos(angle1)
    dx2, dy2 = math.sin(angle2), math.cos(angle2)

    det = dx1 * dy2 - dy1 * dx2
    if abs(det) < 1e-6:
        return None

    t = ((x2 - x1) * dy2 - (y2 - y1) * dx2) / det

    return Point(x1 + t * dx1, y1 + t * dy1)


def triangulate_two(
        cam1: Camera, a1: float,
        cam2: Camera, a2: float) -> Optional[Point]:
    return intersect(cam1, a1, cam2, a2)


def triangulate_three(
        cam1: Camera, a1: float,
        cam2: Camera, a2: float,
        cam3: Camera, a3: float) -> Optional[Point]:

    points = [
        intersect(cam1, a1, cam2, a2),
        intersect(cam2, a2, cam3, a3),
        intersect(cam3, a3, cam1, a1)
    ]

    valid = [p for p in points if p is not None]
    if not valid:
        return None

    x = sum(p.x for p in valid) / len(valid)
    y = sum(p.y for p in valid) / len(valid)

    return Point(x, y)


# =========================
# Dartboard-Berechnung
# =========================

def dart_radius(p: Point) -> float:
    return math.sqrt(p.x ** 2 + p.y ** 2)


def dart_angle(p: Point) -> float:
    """
    0° = Mitte der 20, gegen Uhrzeigersinn
    """
    angle = math.atan2(p.x, p.y)
    if angle < 0:
        angle += 2 * math.pi
    return math.degrees(angle)


# =========================
# Beispiel / Main
# =========================

if __name__ == "__main__":

    # Kamerapositionen (Beispiel)
    cam1 = Camera(-1000, 0)
    cam2 = Camera(1000, 0)
    cam3 = Camera(0, -1200)

    # Beispiel-Winkel (normalerweise aus Bildern)
    angle1 = math.radians(10)
    angle2 = math.radians(-12)
    angle3 = math.radians(2)

    # --- 2 Kameras ---
    hit2 = triangulate_two(cam1, angle1, cam2, angle2)

    if hit2:
        print("🎯 Treffer mit 2 Kameras")
        print(f"X: {hit2.x:.2f}, Y: {hit2.y:.2f}")
        print(f"Radius: {dart_radius(hit2):.2f}")
        print(f"Winkel: {dart_angle(hit2):.2f}°\n")

    # --- 3 Kameras ---
    hit3 = triangulate_three(
        cam1, angle1,
        cam2, angle2,
        cam3, angle3
    )

    if hit3:
        print("🎯 Treffer mit 3 Kameras")
        print(f"X: {hit3.x:.2f}, Y: {hit3.y:.2f}")
        print(f"Radius: {dart_radius(hit3):.2f}")
        print(f"Winkel: {dart_angle(hit3):.2f}°")
