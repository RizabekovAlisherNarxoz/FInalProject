package com.zombietd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class GameScreen implements Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    static final int W = 960, H = 600;

    // 3 floors aligned with TMX open corridors (rows 2-5, 8-11, 14-17 at 2x scale)
    static final float FLOOR_H    = 128f;
    static final float[] FLOOR_Y  = { 448f, 256f, 64f };   // bottom-left Y of each floor rect
    static final float ZONE_W     = 128f;   // left defense zone = 4 TMX tiles × 32px
    static final float SPAWN_X    = W - 10f; // zombies start here

    // Slot layout (3 slots per floor, inside defense zone)
    static final float SLOT_W     = 32f, SLOT_H = 28f;
    static final float[] SLOT_X   = { 4f, 46f, 88f };
    static final float SLOT_BOTTOM_PAD = 8f; // from floor bottom

    // Platform buttons between floors
    static final float PLAT_W = ZONE_W - 8f, PLAT_H = 18f;

    // ── Game state ────────────────────────────────────────────────────────────
    ZombieTD game;
    int lives = 20, money = 150, score = 0, wave = 0;
    boolean waveRunning = false;
    boolean gameOver = false;
    boolean victory = false;
    // Drag-and-drop state
    boolean isDragging = false;
    boolean wasTouched = false;
    Soldier.Type dragType = Soldier.Type.SOLDIER;
    float dragX, dragY;

    // slots[floor][slot] = Soldier or null
    Soldier[][] slots = new Soldier[3][3];

    // platform open/closed between floor 0-1 and 1-2
    boolean[] platformOpen = { false, false };

    Array<Zombie>    zombies   = new Array<>();
    Array<Bullet>    bullets   = new Array<>();
    Array<FloatText> floats    = new Array<>();

    int zombiesToSpawn = 0;
    float spawnTimer   = 0f;
    int[] spawnFloors;

    // ── Rendering ─────────────────────────────────────────────────────────────
    ShapeRenderer sr;
    BitmapFont    font, fontBig, fontSmall;
    GlyphLayout   layout = new GlyphLayout();

    // TMX map
    TiledMap                   tiledMap;
    OrthogonalTiledMapRenderer mapRenderer;
    OrthographicCamera         mapCamera;

    // Soldier sprite animation
    Texture                    soldierTex;
    Animation<TextureRegion>   soldierIdleAnim;
    float                      soldierStateTime = 0f;

    // Enemy sprite animations (null = fallback to shape rendering until PNGs are exported)
    Texture                    zombieIdleTex, zombieHurtTex, zombieDeathTex;
    Animation<TextureRegion>   zombieWalkAnim, zombieHurtAnim, zombieDeathAnim;

    // Button rects (screen coords)
    Rectangle[] slotRects    = new Rectangle[9];   // [floor*3+slot]
    Rectangle[] platformRects = new Rectangle[2];
    Rectangle   btnWave;
    static final Soldier.Type[] HERO_TYPES = {
        Soldier.Type.SOLDIER, Soldier.Type.SNIPER, Soldier.Type.MG,
        Soldier.Type.ARCHER,  Soldier.Type.MAGE
    };
    Rectangle[] heroCardRects = new Rectangle[HERO_TYPES.length];

    public GameScreen(ZombieTD game) {
        this.game = game;
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────
    @Override
    public void show() {
        sr        = new ShapeRenderer();
        font      = new BitmapFont();
        fontBig   = new BitmapFont();
        fontSmall = new BitmapFont();
        font.getData().setScale(1.1f);
        fontBig.getData().setScale(2.2f);
        fontSmall.getData().setScale(0.85f);

        // Load TMX map (30x20 tiles @ 16px; scale 2 → 960x640, camera centers on 960x600)
        tiledMap    = new TmxMapLoader().load("level 1.tmx");
        float unitScale = 2f;
        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap, unitScale);
        mapCamera   = new OrthographicCamera();
        mapCamera.setToOrtho(false, W, H);
        // Show Y=0-600 of the 640-tall map (top 40px trimmed)
        mapCamera.position.set(W / 2f, 300f, 0);
        mapCamera.update();

        // Load female soldier sprite (48x64 per frame, 8 frames horizontal)
        soldierTex = new Texture(Gdx.files.internal("heroes/soldier_idle.png"));
        TextureRegion[][] tmp    = TextureRegion.split(soldierTex, 48, 64);
        TextureRegion[]   frames = tmp[0]; // single row
        soldierIdleAnim = new Animation<>(0.1f, frames);
        soldierIdleAnim.setPlayMode(Animation.PlayMode.LOOP);

        // Load enemy sprites — graceful fallback if PNGs not yet exported from sprite_viewer.html
        // Place files in assets/enemies/: goblin_idle.png, goblin_hurt.png, goblin_death.png
        // Each file is a horizontal strip of 128×128 frames (idle=24, hurt=10, death=20 frames)
        try {
            zombieIdleTex  = new Texture(Gdx.files.internal("enemies/goblin_idle.png"));
            zombieHurtTex  = new Texture(Gdx.files.internal("enemies/goblin_hurt.png"));
            zombieDeathTex = new Texture(Gdx.files.internal("enemies/goblin_death.png"));
            zombieWalkAnim = buildStrip(zombieIdleTex,  128, 128, 0.07f, Animation.PlayMode.LOOP);
            zombieHurtAnim = buildStrip(zombieHurtTex,  128, 128, 0.07f, Animation.PlayMode.NORMAL);
            zombieDeathAnim= buildStrip(zombieDeathTex, 128, 128, 0.08f, Animation.PlayMode.NORMAL);
        } catch (Exception e) {
            zombieWalkAnim = null; zombieHurtAnim = null; zombieDeathAnim = null;
        }

        buildRects();
    }

    void buildRects() {
        for (int f = 0; f < 3; f++) {
            float floorBot = FLOOR_Y[f];
            for (int s = 0; s < 3; s++) {
                float sx = SLOT_X[s];
                float sy = floorBot + SLOT_BOTTOM_PAD;
                slotRects[f * 3 + s] = new Rectangle(sx, sy, SLOT_W, SLOT_H);
            }
        }

        // Platform buttons sit between floors
        for (int i = 0; i < 2; i++) {
            float py = FLOOR_Y[i] - PLAT_H;   // just below floor i
            platformRects[i] = new Rectangle(10, py, PLAT_W, PLAT_H);
        }

        // Wave button top-right
        btnWave = new Rectangle(W - 155f, H - 36f, 145f, 28f);

        // Hero cards in bottom panel (Y=2..56)
        float cardW = (W - 20f) / HERO_TYPES.length;
        for (int i = 0; i < HERO_TYPES.length; i++) {
            heroCardRects[i] = new Rectangle(10f + i * cardW, 2f, cardW - 4f, 54f);
        }
    }

    // ── Main loop ─────────────────────────────────────────────────────────────
    @Override
    public void render(float delta) {
        if (!gameOver) {
            update(delta);
            soldierStateTime += delta;
        }
        draw();
        handleInput();
    }

    void update(float delta) {
        // Spawn zombies
        if (waveRunning && zombiesToSpawn > 0) {
            spawnTimer -= delta;
            if (spawnTimer <= 0f) {
                int f = spawnFloors[spawnFloors.length - zombiesToSpawn];
                float floorMid = FLOOR_Y[f] + FLOOR_H / 2f;
                zombies.add(new Zombie(f, SPAWN_X, floorMid, wave,
                    zombieWalkAnim, zombieHurtAnim, zombieDeathAnim));
                zombiesToSpawn--;
                spawnTimer = Math.max(0.5f, 1.0f - wave * 0.05f);
            }
        }

        // Move zombies
        for (int i = zombies.size - 1; i >= 0; i--) {
            Zombie z = zombies.get(i);
            z.update(delta);

            // Zombie reaches defense zone
            if (!z.dead && z.x <= ZONE_W + 5f) {
                lives--;
                addFloat("-1 ❤", ZONE_W, z.y + 20, Color.RED);
                zombies.removeIndex(i);
                if (lives <= 0) { gameOver = true; return; }
                continue;
            }

            if (z.dead) {
                if (!z.rewardGiven) {
                    score  += 10 + wave * 2;
                    money  += z.reward;
                    addFloat("+" + z.reward + "$", z.x, z.y + 40, Color.YELLOW);
                    z.rewardGiven = true;
                }
                if (z.isDone()) zombies.removeIndex(i);
            }
        }

        // Soldiers shoot
        for (int f = 0; f < 3; f++) {
            for (int s = 0; s < 3; s++) {
                Soldier sol = slots[f][s];
                if (sol == null) continue;
                sol.update(delta);
                if (!sol.canShoot()) continue;

                // Find closest zombie on same floor within range
                Zombie target = null;
                float minDist = sol.range;
                for (Zombie z : zombies) {
                    if (z.floor != f) continue;
                    float dist = z.x - sol.x;
                    if (dist > 0 && dist < minDist) { minDist = dist; target = z; }
                }
                if (target != null) {
                    sol.shoot();
                    bullets.add(new Bullet(sol.x + 18f, sol.y + 14f, sol.damage, target));
                }
            }
        }

        // Move bullets
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.update(delta);
            if (b.dead) bullets.removeIndex(i);
        }

        // Float texts
        for (int i = floats.size - 1; i >= 0; i--) {
            FloatText ft = floats.get(i);
            ft.update(delta);
            if (ft.dead) floats.removeIndex(i);
        }

        // Wave end check
        if (waveRunning && zombiesToSpawn == 0 && zombies.size == 0) {
            waveRunning = false;
            int bonus = 30 + wave * 15;
            money += bonus;
            addFloat("Волна " + wave + " пройдена! +" + bonus + "$", W / 2f - 80, H / 2f, Color.GREEN);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    void handleInput() {
        boolean touched      = Gdx.input.isTouched();
        float   mx           = Gdx.input.getX();
        float   my           = H - Gdx.input.getY();
        boolean justPressed  = touched && !wasTouched;
        boolean justReleased = !touched && wasTouched;

        if (justPressed) {
            if (gameOver) { restartGame(); wasTouched = true; return; }

            // Wave button
            if (btnWave.contains(mx, my) && !waveRunning) { startWave(); wasTouched = true; return; }

            // Platform buttons
            for (int i = 0; i < 2; i++) {
                if (platformRects[i].contains(mx, my)) {
                    platformOpen[i] = !platformOpen[i];
                    if (platformOpen[i]) moveSoldiersDown(i);
                    wasTouched = true;
                    return;
                }
            }

            // R + click on occupied slot = sell
            if (Gdx.input.isKeyPressed(Input.Keys.R)) {
                for (int f = 0; f < 3; f++) {
                    for (int s = 0; s < 3; s++) {
                        if (slotRects[f * 3 + s].contains(mx, my) && slots[f][s] != null) {
                            money += slots[f][s].sellValue;
                            addFloat("Продано +" + slots[f][s].sellValue + "$", mx, my + 20, Color.GOLD);
                            slots[f][s] = null;
                            wasTouched = true;
                            return;
                        }
                    }
                }
            }

            // Start drag from hero panel
            for (int i = 0; i < HERO_TYPES.length; i++) {
                if (heroCardRects[i].contains(mx, my)) {
                    isDragging = true;
                    dragType   = HERO_TYPES[i];
                    dragX = mx; dragY = my;
                    wasTouched = true;
                    return;
                }
            }
        }

        // Update ghost position while dragging
        if (touched && isDragging) {
            dragX = mx;
            dragY = my;
        }

        // Release: try to drop onto a slot
        if (justReleased && isDragging) {
            for (int f = 0; f < 3; f++) {
                for (int s = 0; s < 3; s++) {
                    if (slotRects[f * 3 + s].contains(mx, my)) {
                        handleSlotDrop(f, s, dragType, mx, my);
                        break;
                    }
                }
            }
            isDragging = false;
        }

        wasTouched = touched;
    }

    void handleSlotDrop(int f, int s, Soldier.Type type, float mx, float my) {
        if (slots[f][s] != null) return; // occupied
        int cost = Soldier.getCost(type);
        if (money >= cost) {
            money -= cost;
            float sx = SLOT_X[s] + SLOT_W / 2f;
            float sy = FLOOR_Y[f] + SLOT_BOTTOM_PAD + SLOT_H + 2f;
            slots[f][s] = new Soldier(type, f, s, sx, sy);
            addFloat("-" + cost + "$", mx, my + 20, Color.ORANGE);
        } else {
            addFloat("Нет денег!", mx, my + 20, Color.RED);
        }
    }

    /** When platform i opens, move soldiers from floor i down to floor i+1 if slot free */
    void moveSoldiersDown(int platformIdx) {
        int fromFloor = platformIdx;
        int toFloor   = platformIdx + 1;
        for (int s = 0; s < 3; s++) {
            if (slots[fromFloor][s] != null && slots[toFloor][s] == null) {
                Soldier sol = slots[fromFloor][s];
                slots[fromFloor][s] = null;
                float newY = FLOOR_Y[toFloor] + SLOT_BOTTOM_PAD + SLOT_H + 2f;
                sol.y = newY;
                sol.floor = toFloor;
                slots[toFloor][s] = sol;
                addFloat("↓ Перешёл!", sol.x, sol.y + 30, Color.CYAN);
            }
        }
    }

    void startWave() {
        wave++;
        waveRunning = true;
        int count = 4 + wave * 3;
        zombiesToSpawn = count;
        spawnTimer = 0f;
        spawnFloors = new int[count];
        for (int i = 0; i < count; i++)
            spawnFloors[i] = (int)(Math.random() * 3);
    }

    void addFloat(String text, float x, float y, Color color) {
        floats.add(new FloatText(text, x, y, color));
    }

    private Animation<TextureRegion> buildStrip(Texture tex, int fw, int fh, float dur, Animation.PlayMode mode) {
        TextureRegion[][] grid = TextureRegion.split(tex, fw, fh);
        Animation<TextureRegion> anim = new Animation<>(dur, grid[0]);
        anim.setPlayMode(mode);
        return anim;
    }

    void restartGame() {
        lives = 20; money = 150; score = 0; wave = 0;
        waveRunning = false; gameOver = false;
        isDragging = false; wasTouched = false;
        zombies.clear(); bullets.clear(); floats.clear();
        slots = new Soldier[3][3];
        platformOpen[0] = false; platformOpen[1] = false;
    }

    // ── Drawing ───────────────────────────────────────────────────────────────
    void draw() {
        Gdx.gl.glClearColor(0.07f, 0.07f, 0.11f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // ── TMX map background ──
        mapRenderer.setView(mapCamera);
        mapRenderer.render();

        // ── ShapeRenderer pass ──
        sr.setProjectionMatrix(game.batch.getProjectionMatrix());
        sr.begin(ShapeRenderer.ShapeType.Filled);
        drawPlatforms();
        drawSlots();
        drawSoldiers();
        drawZombies();
        drawBullets();
        drawBottomPanel();
        if (isDragging && dragType != Soldier.Type.SOLDIER) drawDragGhost();
        sr.end();

        // ── SpriteBatch pass (text + sprites) ──
        game.batch.begin();
        drawSpriteSoldiers();
        drawZombieSprites();
        if (isDragging && dragType == Soldier.Type.SOLDIER) drawSpriteDragGhost();
        drawHUD();
        drawSlotLabels();
        drawBottomPanelText();
        drawFloatTexts();
        if (gameOver) drawGameOver();
        game.batch.end();
    }

    void drawSpriteSoldiers() {
        if (soldierIdleAnim == null) return;
        TextureRegion frame = soldierIdleAnim.getKeyFrame(soldierStateTime);
        // 48x64 actual size — one tile wide, fits within floor height
        float dw = 48f, dh = 64f;
        for (int f = 0; f < 3; f++) {
            for (int s = 0; s < 3; s++) {
                Soldier sol = slots[f][s];
                if (sol == null || sol.type != Soldier.Type.SOLDIER) continue;
                game.batch.setColor(1f, 1f, 1f, 1f);
                game.batch.draw(frame, sol.x - dw / 2f, sol.y - 4f, dw, dh);
            }
        }
        game.batch.setColor(1f, 1f, 1f, 1f);
    }

    void drawSpriteDragGhost() {
        if (soldierIdleAnim == null) return;
        TextureRegion frame = soldierIdleAnim.getKeyFrame(soldierStateTime);
        float dw = 48f, dh = 64f;
        game.batch.setColor(1f, 1f, 1f, 0.75f);
        game.batch.draw(frame, dragX - dw / 2f, dragY - dh / 2f, dw, dh);
        game.batch.setColor(1f, 1f, 1f, 1f);
    }

    void drawBuilding() {
        Color[] floorColors = {
            new Color(0.11f, 0.11f, 0.18f, 1f),
            new Color(0.09f, 0.09f, 0.16f, 1f),
            new Color(0.11f, 0.11f, 0.18f, 1f)
        };
        for (int f = 0; f < 3; f++) {
            sr.setColor(floorColors[f]);
            sr.rect(0, FLOOR_Y[f], W, FLOOR_H);

            // Floor line top
            sr.setColor(0.22f, 0.22f, 0.36f, 1f);
            sr.rect(0, FLOOR_Y[f] + FLOOR_H - 3, W, 3);

            // Floor line bottom
            sr.setColor(0.05f, 0.05f, 0.10f, 1f);
            sr.rect(0, FLOOR_Y[f], W, 3);

            // Spawn zone
            sr.setColor(0.22f, 0.06f, 0.06f, 0.85f);
            sr.rect(W - 55f, FLOOR_Y[f] + 3, 55f, FLOOR_H - 6);

            // Defense zone bg
            sr.setColor(0.05f, 0.10f, 0.17f, 0.7f);
            sr.rect(0, FLOOR_Y[f], ZONE_W, FLOOR_H);

            // Decorative windows
            for (int w = 280; w < W - 70; w += 80) {
                sr.setColor(0.16f, 0.23f, 0.38f, 0.4f);
                sr.rect(w, FLOOR_Y[f] + 20, 30, 20);
            }
        }

        // Left wall
        sr.setColor(0.14f, 0.14f, 0.24f, 1f);
        sr.rect(0, FLOOR_Y[2], 6, FLOOR_Y[0] + FLOOR_H - FLOOR_Y[2]);
        // Right wall
        sr.rect(W - 6, FLOOR_Y[2], 6, FLOOR_Y[0] + FLOOR_H - FLOOR_Y[2]);
    }

    void drawZombiePathLines() {}

    void drawPlatforms() {
        for (int i = 0; i < 2; i++) {
            Rectangle r = platformRects[i];
            boolean open = platformOpen[i];
            sr.setColor(open ? new Color(0.15f, 0.45f, 0.15f, 1f) : new Color(0.12f, 0.24f, 0.12f, 1f));
            sr.rect(r.x, r.y, r.width, r.height);
            // Border
            sr.setColor(open ? new Color(0.3f, 0.8f, 0.3f, 1f) : new Color(0.2f, 0.5f, 0.2f, 1f));
            sr.rectLine(r.x, r.y, r.x + r.width, r.y, 1);
            sr.rectLine(r.x, r.y + r.height, r.x + r.width, r.y + r.height, 1);
        }
    }

    void drawSlots() {
        for (int f = 0; f < 3; f++) {
            for (int s = 0; s < 3; s++) {
                Rectangle r = slotRects[f * 3 + s];
                boolean occupied = slots[f][s] != null;
                if (occupied) {
                    Color c = getSoldierColor(slots[f][s].type);
                    sr.setColor(c.r * 0.4f, c.g * 0.4f, c.b * 0.4f, 0.7f);
                } else {
                    sr.setColor(0.10f, 0.17f, 0.24f, 1f);
                }
                sr.rect(r.x, r.y, r.width, r.height);
                // Border
                sr.setColor(occupied ? 0.4f : 0.17f, occupied ? 0.55f : 0.35f, occupied ? 0.8f : 0.48f, 1f);
                sr.rectLine(r.x, r.y, r.x + r.width, r.y, 1);
                sr.rectLine(r.x, r.y + r.height, r.x + r.width, r.y + r.height, 1);
                sr.rectLine(r.x, r.y, r.x, r.y + r.height, 1);
                sr.rectLine(r.x + r.width, r.y, r.x + r.width, r.y + r.height, 1);

                // HP bar if occupied
                if (occupied) {
                    Soldier sol = slots[f][s];
                    float hpPct = sol.hp / sol.maxHp;
                    sr.setColor(0.2f, 0.2f, 0.2f, 1f);
                    sr.rect(r.x + 2, r.y + 2, r.width - 4, 3);
                    sr.setColor(hpPct > 0.5f ? Color.GREEN : Color.YELLOW);
                    sr.rect(r.x + 2, r.y + 2, (r.width - 4) * hpPct, 3);
                }
            }
        }
    }

    Color getSoldierColor(Soldier.Type t) {
        switch (t) {
            case SOLDIER: return new Color(0.23f, 0.45f, 0.8f,  1f);
            case SNIPER:  return new Color(0.55f, 0.3f,  0.85f, 1f);
            case MG:      return new Color(0.85f, 0.55f, 0.2f,  1f);
            case ARCHER:  return new Color(0.20f, 0.75f, 0.30f, 1f);
            case MAGE:    return new Color(0.85f, 0.20f, 0.85f, 1f);
            default: return Color.WHITE;
        }
    }

    void drawSoldiers() {
        for (int f = 0; f < 3; f++)
            for (int s = 0; s < 3; s++)
                if (slots[f][s] != null) slots[f][s].draw(sr);
    }

    void drawZombies() {
        for (Zombie z : zombies) {
            if (zombieWalkAnim != null) z.drawHPBar(sr);
            else z.draw(sr);
        }
    }

    void drawZombieSprites() {
        if (zombieWalkAnim == null) return;
        game.batch.setColor(1f, 1f, 1f, 1f);
        for (Zombie z : zombies) z.drawSprite(game.batch);
        game.batch.setColor(1f, 1f, 1f, 1f);
    }

    void drawBullets() {
        for (Bullet b : bullets) b.draw(sr);
    }

    void drawHUD() {
        // Background bar
        game.batch.setColor(0.08f, 0.08f, 0.14f, 1f);

        // Stats
        font.setColor(Color.CYAN);
        font.draw(game.batch, "❤ " + lives, 8, H - 8);
        font.setColor(Color.YELLOW);
        font.draw(game.batch, "$ " + money, 90, H - 8);
        font.setColor(Color.GREEN);
        font.draw(game.batch, "Волна: " + wave, 185, H - 8);
        font.setColor(Color.WHITE);
        font.draw(game.batch, "Счёт: " + score, 295, H - 8);

        // Shop button removed — heroes are dragged from bottom panel
        drawButton(btnWave, waveRunning ? "⌛ Идёт волна" : "▶ Волна " + (wave + 1), false);

        // Floor labels
        fontSmall.setColor(0.3f, 0.3f, 0.5f, 1f);
        for (int f = 0; f < 3; f++) {
            fontSmall.draw(game.batch, (3 - f) + "F", 6, FLOOR_Y[f] + FLOOR_H - 5);
        }

        // Spawn labels
        fontSmall.setColor(0.8f, 0.2f, 0.2f, 1f);
        for (int f = 0; f < 3; f++) {
            fontSmall.draw(game.batch, "СПАВН", W - 52f, FLOOR_Y[f] + FLOOR_H / 2f + 6);
        }

        // Platform labels
        for (int i = 0; i < 2; i++) {
            Rectangle r = platformRects[i];
            fontSmall.setColor(platformOpen[i] ? Color.GREEN : new Color(0.4f, 0.8f, 0.4f, 1f));
            String label = platformOpen[i] ? "▲▼ открыта (нажми)" : "▼ платформа (нажми)";
            fontSmall.draw(game.batch, label, r.x + 6, r.y + r.height - 3);
        }

        // Hint
        fontSmall.setColor(0.35f, 0.35f, 0.55f, 1f);
        fontSmall.draw(game.batch, "Перетащивай героя снизу | R+клик = продать", 400, H - 8);
    }

    void drawButton(Rectangle r, String label, boolean selected) {
        // Drawn via ShapeRenderer would need end/begin — just use font color trick
        fontSmall.setColor(selected ? Color.WHITE : new Color(0.6f, 0.75f, 1f, 1f));
        fontSmall.draw(game.batch, "[" + label + "]", r.x + 4, r.y + r.height - 5);
    }

    void drawSlotLabels() {
        for (int f = 0; f < 3; f++) {
            for (int s = 0; s < 3; s++) {
                Rectangle r = slotRects[f * 3 + s];
                if (slots[f][s] == null) {
                    fontSmall.setColor(0.3f, 0.45f, 0.6f, 1f);
                    fontSmall.draw(game.batch, "слот", r.x + 8, r.y + r.height - 8);
                } else {
                    fontSmall.setColor(getSoldierColor(slots[f][s].type));
                    String lbl;
                    switch (slots[f][s].type) {
                        case SOLDIER: lbl = "СЛД"; break;
                        case SNIPER:  lbl = "СНП"; break;
                        case MG:      lbl = "МГН"; break;
                        case ARCHER:  lbl = "ЛУЧ"; break;
                        case MAGE:    lbl = "МАГ"; break;
                        default:      lbl = "???"; break;
                    }
                    fontSmall.draw(game.batch, lbl, r.x + 6, r.y + r.height - 8);
                }
            }
        }
    }

    void drawFloatTexts() {
        for (FloatText ft : floats) ft.draw(game.batch, font);
    }

    void drawBottomPanel() {
        // Dark panel background
        sr.setColor(0.07f, 0.07f, 0.12f, 1f);
        sr.rect(0, 0, W, 58);
        // Hero cards (non-SOLDIER types get shape mini-body here)
        for (int i = 0; i < HERO_TYPES.length; i++) {
            Soldier.Type t = HERO_TYPES[i];
            Rectangle r = heroCardRects[i];
            Color c = getSoldierColor(t);
            sr.setColor(c.r * 0.22f, c.g * 0.22f, c.b * 0.22f, 1f);
            sr.rect(r.x, r.y, r.width, r.height);
            // Border
            sr.setColor(c.r * 0.65f, c.g * 0.65f, c.b * 0.65f, 1f);
            sr.rectLine(r.x, r.y,           r.x + r.width, r.y,            1.5f);
            sr.rectLine(r.x, r.y + r.height,r.x + r.width, r.y + r.height, 1.5f);
            sr.rectLine(r.x, r.y,           r.x,           r.y + r.height, 1.5f);
            sr.rectLine(r.x + r.width, r.y, r.x + r.width, r.y + r.height, 1.5f);
            // Shape mini body only for non-SOLDIER types
            if (t != Soldier.Type.SOLDIER) {
                float cx = r.x + r.width / 2f;
                sr.setColor(c);
                sr.rect(cx - 5, r.y + 16, 10, 12);
                sr.setColor(new Color(0.78f, 0.66f, 0.51f, 1f));
                sr.circle(cx, r.y + 34, 5);
            }
        }
    }

    void drawBottomPanelText() {
        for (int i = 0; i < HERO_TYPES.length; i++) {
            Soldier.Type t = HERO_TYPES[i];
            Rectangle r = heroCardRects[i];
            // Sprite icon for SOLDIER card
            if (t == Soldier.Type.SOLDIER && soldierIdleAnim != null) {
                TextureRegion frame = soldierIdleAnim.getKeyFrame(soldierStateTime);
                float iw = 28f, ih = 36f;
                float cx = r.x + r.width / 2f;
                game.batch.setColor(1f, 1f, 1f, 1f);
                game.batch.draw(frame, cx - iw / 2f, r.y + 14f, iw, ih);
                game.batch.setColor(1f, 1f, 1f, 1f);
            }
            fontSmall.setColor(getSoldierColor(t));
            fontSmall.draw(game.batch, Soldier.getName(t), r.x + 4, r.y + r.height - 3);
            fontSmall.setColor(Color.YELLOW);
            fontSmall.draw(game.batch, "$" + Soldier.getCost(t), r.x + 4, r.y + 13);
        }
    }

    void drawDragGhost() {
        Color c = getSoldierColor(dragType);
        sr.setColor(c.r, c.g, c.b, 0.75f);
        sr.rect(dragX - 9, dragY, 18, 22);
        sr.setColor(0.78f, 0.66f, 0.51f, 0.75f);
        sr.circle(dragX, dragY + 30, 9);
        sr.setColor(c.r * 0.7f, c.g * 0.7f, c.b * 0.7f, 0.75f);
        sr.rect(dragX - 9, dragY + 25, 18, 10);
    }

    void drawGameOver() {
        fontBig.setColor(Color.RED);
        fontBig.draw(game.batch, "ИГРА ОКОНЧЕНА", W / 2f - 140, H / 2f + 30);
        font.setColor(Color.WHITE);
        font.draw(game.batch, "Счёт: " + score + " | Волна: " + wave, W / 2f - 80, H / 2f - 10);
        font.setColor(Color.YELLOW);
        font.draw(game.batch, "Нажмите для перезапуска", W / 2f - 100, H / 2f - 40);
    }

    // ── Screen boilerplate ────────────────────────────────────────────────────
    @Override public void resize(int width, int height) {
        game.batch.getProjectionMatrix().setToOrtho2D(0, 0, W, H);
        sr.getProjectionMatrix().setToOrtho2D(0, 0, W, H);
    }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}
    @Override public void dispose() {
        sr.dispose();
        font.dispose();
        fontBig.dispose();
        fontSmall.dispose();
        if (tiledMap    != null) tiledMap.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        if (soldierTex   != null) soldierTex.dispose();
        if (zombieIdleTex  != null) zombieIdleTex.dispose();
        if (zombieHurtTex  != null) zombieHurtTex.dispose();
        if (zombieDeathTex != null) zombieDeathTex.dispose();
    }
}
