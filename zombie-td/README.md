# Hero's Path Defense

A 2D tower defense game built with [libGDX](https://libgdx.com/).

Place heroes on three defensive lanes, survive 10 waves of enemies per level, and protect your base across 2 unique maps — winter and ocean.

---

## Team

| Name | Role | Responsibilities |
|---|---|---|
| Alisher Rizabekov | Lead Programmer | Core architecture, GameScreen, Zombie & Soldier logic, bullet system, wave spawner, HUD, tiled map integration |
| Zhadyra Kurmanova | Designer / Programmer | Level design (TMX maps), sprite & animation assets, UI screens (Menu, Level Select), enemy configs |

---

## How to Run

**Requirements:** Java 11 or higher installed.

**Run from source:**
```bash
./gradlew lwjgl3:run
```

**Build a runnable JAR:**
```bash
./gradlew lwjgl3:jar
```
The JAR will be at `lwjgl3/build/libs/`. Run it with:
```bash
java -jar lwjgl3/build/libs/zombie-td-1.0.0.jar
```

> On Windows use `gradlew.bat` instead of `./gradlew`.

---

## Controls

| Input | Action |
|---|---|
| **Mouse** — drag hero card → drop on slot | Place a hero on the battlefield |
| **Mouse** — click on placed hero | Sell hero (get sell value back) |
| **Mouse** — click **Start Wave** button | Begin the next enemy wave |
| **Mouse** — click **Settings** icon | Open settings (music volume) |
| **ENTER** or **SPACE** | Start the game from the main menu |
| **ESC** | Back to previous screen / open settings |

---

## Gameplay

- **3 lanes** — enemies walk from right to left along the top, middle, and bottom floors
- **5 hero types** you can place in defense slots:

| Hero | Cost | Damage | Range | Fire Rate |
|---|---|---|---|---|
| Soldier | $50 | 12 | 750 | 1.0/s |
| Sniper | $80 | 30 | 900 | 0.4/s |
| Machine Gun | $120 | 7 | 700 | 3.5/s |
| Archer | $150 | 55 | 850 | 0.7/s |
| Mage | $200 | 95 | 780 | 0.25/s |

- **Start:** 3 lives, $200
- **10 waves** per level — enemies get stronger each wave
- Lose a life when a zombie reaches the left edge
- Game over when lives reach 0; victory when all waves on all levels are cleared

---

## Levels

| Level | Map | Enemy Type |
|---|---|---|
| Level 1 | Winter | Icy blue zombies |
| Level 2 | Ocean / Underwater | Aqua zombies |

---

## Project Structure

```
core/src/main/java/com/zombietd/
├── ZombieTD.java          — main Game class, screen manager
├── MenuScreen.java        — main menu
├── LevelSelectScreen.java — level selection
├── GameScreen.java        — core gameplay loop
├── Zombie.java            — enemy entity
├── Soldier.java           — hero/tower entity
├── Bullet.java            — projectile
├── EnemyConfig.java       — enemy data loaded from enemies.json
└── FloatText.java         — floating damage numbers
```

---

## Known Issues

None reported.

---

## Built With

- [libGDX](https://libgdx.com/) — game framework
- [gdx-liftoff](https://github.com/libgdx/gdx-liftoff) — project generator
- [Tiled](https://www.mapeditor.org/) — level map editor (.tmx)
