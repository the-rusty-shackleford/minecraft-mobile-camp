"""Find positive-area, equally facing coplanar model faces (a source of z-fighting).

Checks repository JSON cuboids after Minecraft element rotations. Shared edges and
opposing faces at solid joints are allowed. This does not replace in-game visual
review or detect intersections between different placed blocks.
"""
from __future__ import annotations

from dataclasses import dataclass
import json
import math
from pathlib import Path
from typing import NotRequired, TypedDict, cast

type V3 = tuple[float, float, float]
type V2 = tuple[float, float]


class Rotation(TypedDict):
    origin: list[float]
    axis: str
    angle: float
    rescale: NotRequired[bool]


class Element(TypedDict):
    from_: NotRequired[list[float]]
    to: list[float]
    faces: dict[str, object]
    rotation: NotRequired[Rotation]


class Model(TypedDict):
    elements: NotRequired[list[Element]]


@dataclass(frozen=True)
class Face:
    label: str
    normal: V3
    vertices: tuple[V3, ...]


def dot(a: V3, b: V3) -> float:
    return sum(x * y for x, y in zip(a, b))


def rotate(point: V3, rotation: Rotation | None, vector: bool = False) -> V3:
    if rotation is None:
        return point
    assert not rotation.get("rescale", False), "rescaled elements need explicit support"
    origin = [0., 0., 0.] if vector else rotation["origin"]
    axis = "xyz".index(rotation["axis"])
    a, b = (axis + 1) % 3, (axis + 2) % 3
    p = [point[i] - origin[i] for i in range(3)]
    c, s = math.cos(math.radians(rotation["angle"])), math.sin(math.radians(rotation["angle"]))
    p[a], p[b] = p[a] * c - p[b] * s, p[a] * s + p[b] * c
    return p[0] + origin[0], p[1] + origin[1], p[2] + origin[2]


def faces(model: Model) -> list[Face]:
    result: list[Face] = []
    directions = {"west": (0, -1), "east": (0, 1), "down": (1, -1),
                  "up": (1, 1), "north": (2, -1), "south": (2, 1)}
    for index, element in enumerate(model.get("elements", [])):
        # JSON uses the Python keyword "from"; validate it at this boundary.
        raw = cast(dict[str, object], element)
        low = cast(list[float], raw["from"])
        high = element["to"]
        assert len(low) == len(high) == 3 and all(low[i] < high[i] for i in range(3))
        rotation = element.get("rotation")
        for name in element["faces"]:
            axis, sign = directions[name]
            a, b = (axis + 1) % 3, (axis + 2) % 3
            normal = [0., 0., 0.]
            normal[axis] = sign
            vertices: list[V3] = []
            for upper_a, upper_b in ((False, False), (True, False), (True, True), (False, True)):
                p = list(low)
                p[axis] = high[axis] if sign > 0 else low[axis]
                p[a] = high[a] if upper_a else low[a]
                p[b] = high[b] if upper_b else low[b]
                vertices.append(rotate((p[0], p[1], p[2]), rotation))
            result.append(Face(f"{index}:{name}",
                               rotate((normal[0], normal[1], normal[2]), rotation, True),
                               tuple(vertices)))
    return result


def cross(a: V2, b: V2, p: V2) -> float:
    return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])


def area(polygon: list[V2]) -> float:
    return sum(a[0] * b[1] - a[1] * b[0]
               for a, b in zip(polygon, polygon[1:] + polygon[:1])) / 2


def overlap(first: Face, second: Face) -> bool:
    if dot(first.normal, second.normal) < 1 - 1e-7:
        return False
    if abs(dot(first.normal, first.vertices[0]) - dot(first.normal, second.vertices[0])) > 1e-6:
        return False
    omitted = max(range(3), key=lambda i: abs(first.normal[i]))
    a, b = [i for i in range(3) if i != omitted]
    subject = [(p[a], p[b]) for p in first.vertices]
    clip = [(p[a], p[b]) for p in second.vertices]
    if area(clip) < 0:
        clip.reverse()
    for start, end in zip(clip, clip[1:] + clip[:1]):
        old, subject = subject, []
        for previous, current in zip(old[-1:] + old[:-1], old):
            d0, d1 = cross(start, end, previous), cross(start, end, current)
            if (d0 >= 0) != (d1 >= 0):
                fraction = d0 / (d0 - d1)
                subject.append((previous[0] + fraction * (current[0] - previous[0]),
                                previous[1] + fraction * (current[1] - previous[1])))
            if d1 >= 0:
                subject.append(current)
        if not subject:
            return False
    return abs(area(subject)) > 1e-5


def main() -> None:
    root = Path(__file__).resolve().parents[1] / "src/main/resources/assets/mobilecamp/models"
    failures: list[str] = []
    models = sorted(root.rglob("*.json"))
    for path in models:
        # Files are repository-owned models; JSON structure is checked as consumed.
        surface = faces(cast(Model, json.loads(path.read_text())))
        for index, face in enumerate(surface):
            for other in surface[index + 1:]:
                if overlap(face, other):
                    failures.append(f"{path.relative_to(root)} {face.label} overlaps {other.label}")
    for failure in failures[:30]:
        print(failure)
    print(f"{len(models)} models inspected; {len(failures)} coplanar overlaps")
    raise SystemExit(bool(failures))


if __name__ == "__main__":
    main()
