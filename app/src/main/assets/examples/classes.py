# Dataclasses, sorting and f-strings.
from dataclasses import dataclass, field


@dataclass(order=True)
class Planet:
    sort_index: float = field(init=False, repr=False)
    name: str
    moons: int
    distance_au: float

    def __post_init__(self):
        self.sort_index = self.distance_au


planets = [
    Planet("Saturn", 146, 9.5),
    Planet("Earth", 1, 1.0),
    Planet("Mars", 2, 1.5),
    Planet("Jupiter", 95, 5.2),
]
for p in sorted(planets):
    print(f"{p.name:8} {p.distance_au:4.1f} AU  {p.moons:3} moons")
