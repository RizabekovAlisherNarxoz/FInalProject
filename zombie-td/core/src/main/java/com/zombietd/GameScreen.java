package com.zombietd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
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
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

public class GameScreen implements Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    static final int W = 960, H = 600;

    // 3 floors aligned with TMX open corridors (rows 2-5, 8-11, 14-17 at 2x scale)
    static final float FLOOR_H            = 128f;
    static final float[] FLOOR_Y          = { 448f, 256f, 64f }; // bottom-left Y of each floor
    static final float FLOOR_GROUND_OFFSET = 20f;  // Y from FLOOR_Y[f] where герои стоят
    static final float FLOOR_ZOMBIE_OFFSET = 18f;  // Y от FLOOR_Y[f] где зомби идут (ещё ниже)

    // Per-level zombie spawn Y per floor (floor 0=3F top, 1=2F, 2=1F bottom).
    // Level 1 (winter map), Level 2 (forest map), Level 3 (underwater map)
    static final float[][] LEVEL_ZOMBIE_FLOOR_Y = {
        { 506f, 314f,  90f },  // Level 1: winter
        { 506f, 314f, 122f },  // Level 2: forest
        { 538f, 346f, 122f },  // Level 3: underwater — raised +32 to sit on platform surface
    };

    // Camera center Y per level
    static final float[] LEVEL_CAMERA_Y = {
        260f,   // Level 1: winter
        260f,   // Level 2: forest
        260f,   // Level 3: underwater — same floor layout as Level 1
    };

    // Camera center X per level
    static final float[] LEVEL_CAMERA_X = {
        480f,   // Level 1
        480f,   // Level 2
        480f,   // Level 3: content fills cols 0..39, no shift needed
    };
    static final float ZONE_W             = 128f;  // left defense zone = 4 TMX tiles × 32px
    static final float SPAWN_X    = W - 10f; // zombies start here

    // Slot layout (3 slots per floor, inside defense zone)
    static final float SLOT_W     = 32f, SLOT_H = 28f;
    static final float[] SLOT_X   = { 30f, 82f, 134f };
    static final float SLOT_BOTTOM_PAD = 8f; // from floor bottom

    // Platform buttons between floors
    static final float PLAT_W = ZONE_W - 8f, PLAT_H = 18f;

    // ── Game state ────────────────────────────────────────────────────────────
    static final int WAVES_PER_LEVEL = 10;
    static final int TOTAL_LEVELS    = 3;

    // Enemy location names per level (matches keys in enemies.json)
    // Level 1 = winter map, Level 2 = forest map, Level 3 = underwater map
    static final String[] LEVEL_ENEMY_POOL = { "winter", "forest", "ocean" };

    // Sprite tint per level so enemies look different even with the same base sprite
    static final Color[] LEVEL_TINT = {
        new Color(0.70f, 0.92f, 1.00f, 1f),  // Level 1 winter: icy blue
        new Color(0.60f, 1.00f, 0.60f, 1f),  // Level 2 forest: green
        new Color(0.50f, 0.85f, 1.00f, 1f),  // Level 3 ocean:  aqua
    };

    ZombieTD game;
    int currentLevel = 1;
    int lives = 3, money = 200, score = 0, wave = 0;
    boolean waveRunning = false;
    boolean gameOver = false;
    boolean victory = false;
    boolean levelComplete = false;
    // Drag-and-drop state
    boolean isDragging = false;
    boolean wasTouched = false;
    Soldier.Type dragType = Soldier.Type.SOLDIER;
    float dragX, dragY;

    // Window-to-game coordinate scale (updated on resize)
    float scaleX = 1f, scaleY = 1f;

    // Platform drag-to-swap state (-1 = not holding a platform gap)
    int   platDragIdx    = -1;
    float platDragStartX, platDragStartY;
    float platDragCurX,   platDragCurY;

    // slots[floor][slot] = Soldier or null
    Soldier[][] slots = new Soldier[3][3];

    // brief visual flash after a floor swap (counts down from 0.5 → 0)
    float[] platformSwapTimer = { 0f, 0f };

    Array<Zombie>    zombies   = new Array<>();
    Array<Bullet>    bullets   = new Array<>();
    Array<FloatText> floats    = new Array<>();

    int zombiesToSpawn = 0;
    float spawnTimer   = 0f;
    int[] spawnFloors;
    EnemyConfig[] spawnConfigs;

    // Enemy pools loaded from enemies.json, indexed by level (0-based)
    Array<EnemyConfig>[] enemyPools;

    // All textures loaded for enemy sprites — kept for proper disposal
    Array<com.badlogic.gdx.graphics.Texture> enemyTexList = new Array<>();

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

    // Hero card sprite sheets (16 frames × 64px, exported from heroes_final.html)
    Texture                    sniperTex, mgTex, archerTex, mageTex;
    Animation<TextureRegion>   sniperIdleAnim, mgIdleAnim, archerIdleAnim, mageIdleAnim;

    // Enemy sprite animations (null = fallback to shape rendering until PNGs are exported)
    Texture                    zombieIdleTex, zombieHurtTex, zombieDeathTex;

    // HUD pixel-art icons (generated at runtime from heart_coin_icons.html data)
    Texture iconHeart, iconCoin;

    // Hero slot indicator sprites — hero_slots_tileset.png (100×67, 3 cols × 2 rows, 32×32 each)
    // col 0=forest, col 1=winter, col 2=other; row 0=full, row 1=alt
    Texture slotTex;
    TextureRegion slotWinterRegion;
    TextureRegion slotUnderwaterRegion;
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

    // Overlay textures
    Texture playBtnTex, settingsIconTex, gameOverTex, winTex;

    // Settings modal
    boolean settingsOpen = false;
    float   musicVolume  = 1f;
    boolean draggingSlider = false;
    Rectangle btnSettings, settingsModal, settingsSlider, btnCloseSettings, btnBackToMenu;

    public GameScreen(ZombieTD game) {
        this.game = game;
    }

    public GameScreen(ZombieTD game, int startLevel) {
        this.game = game;
        this.currentLevel = startLevel;
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

        game.playMusic(currentLevel == 1 ? "winter.mp3" : "sea.mp3");

        // Load TMX map
        loadMap(currentLevel);
        mapCamera   = new OrthographicCamera();
        mapCamera.setToOrtho(false, W, H);
        mapCamera.position.set((float)Math.round(W / 2f), LEVEL_CAMERA_Y[currentLevel - 1], 0);
        mapCamera.update();

        // Load level-specific hero sprites (16 frames × 64×64 each)
        String[] heroFiles = currentLevel == 1
            ? new String[]{ "winter_bm_idle.png", "winter_vk_idle.png", "winter_il_idle.png",
                            "winter_gm_idle.png", "winter_ew_idle.png" }
            : new String[]{ "ocean_dv_idle.png",  "ocean_tc_idle.png",  "ocean_sg_idle.png",
                            "ocean_lr_idle.png",  "ocean_og_idle.png" };
        soldierTex = tryLoadTexture(heroFiles[0]);
        if (soldierTex != null) soldierIdleAnim = buildStrip(soldierTex, 64, 64, 0.08f, Animation.PlayMode.LOOP);
        sniperTex  = tryLoadTexture(heroFiles[1]);
        if (sniperTex  != null) sniperIdleAnim  = buildStrip(sniperTex,  64, 64, 0.08f, Animation.PlayMode.LOOP);
        mgTex      = tryLoadTexture(heroFiles[2]);
        if (mgTex      != null) mgIdleAnim      = buildStrip(mgTex,      64, 64, 0.08f, Animation.PlayMode.LOOP);
        archerTex  = tryLoadTexture(heroFiles[3]);
        if (archerTex  != null) archerIdleAnim  = buildStrip(archerTex,  64, 64, 0.08f, Animation.PlayMode.LOOP);
        mageTex    = tryLoadTexture(heroFiles[4]);
        if (mageTex    != null) mageIdleAnim    = buildStrip(mageTex,    64, 64, 0.08f, Animation.PlayMode.LOOP);

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

        // Slot indicator sprites: 3 cols × 2 rows at 32×32, with 2px H-gap and 3px V-gap
        slotTex = tryLoadTexture("hero_slots_tileset.png");
        if (slotTex != null) {
            slotWinterRegion     = new TextureRegion(slotTex, 34, 0, 32, 32); // col 1 = winter
            slotUnderwaterRegion = new TextureRegion(slotTex, 68, 0, 32, 32); // col 2 = ocean
        }

        loadEnemyConfigs();
        buildHudIcons();
        buildRects();

        playBtnTex      = tryLoadTexture("play_button.png");
        settingsIconTex = tryLoadTexture("settings_icon.png");
        gameOverTex     = tryLoadTexture("gameover_screen.png");
        winTex          = tryLoadTexture("win_screen.png");
        musicVolume = 1f;
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

        // Platform hit zones: full gap between adjacent floors, full screen width
        for (int i = 0; i < 2; i++) {
            float gapBottom = FLOOR_Y[i + 1] + FLOOR_H; // top edge of lower floor
            float gapTop    = FLOOR_Y[i];               // bottom edge of upper floor
            platformRects[i] = new Rectangle(0, gapBottom, W, gapTop - gapBottom);
        }

        // Wave button top-right (play icon)
        btnWave = new Rectangle(W - 205f, H - 38f, 32f, 32f);

        // Settings icon (top-right corner)
        btnSettings = new Rectangle(W - 42f, H - 38f, 32f, 32f);

        // Settings modal (centred)
        settingsModal      = new Rectangle(W / 2f - 170f, H / 2f - 120f, 340f, 240f);
        settingsSlider     = new Rectangle(W / 2f - 110f, H / 2f + 20f,  220f, 14f);
        btnCloseSettings   = new Rectangle(W / 2f + 50f,  H / 2f - 90f,  110f, 30f);
        btnBackToMenu      = new Rectangle(W / 2f - 160f, H / 2f - 90f,  130f, 30f);

        // Hero cards in bottom panel (Y=2..56)
        float cardW = (W - 20f) / HERO_TYPES.length;
        for (int i = 0; i < HERO_TYPES.length; i++) {
            heroCardRects[i] = new Rectangle(10f + i * cardW, 2f, cardW - 4f, 54f);
        }
    }

    // ── Main loop ─────────────────────────────────────────────────────────────
    @Override
    public void render(float delta) {
        if (!gameOver && !levelComplete && !victory && !settingsOpen) {
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
                int idx = spawnFloors.length - zombiesToSpawn;
                int f = spawnFloors[idx];
                float floorZombie = LEVEL_ZOMBIE_FLOOR_Y[currentLevel - 1][f];
                if (currentLevel == 3) {
                    float[] lvl3Off = { -16f, -16f, -32f };
                    floorZombie += lvl3Off[f];
                }
                EnemyConfig cfg = (spawnConfigs != null) ? spawnConfigs[idx] : null;
                Animation<TextureRegion> walk  = (cfg != null && cfg.walkAnim  != null) ? cfg.walkAnim  : zombieWalkAnim;
                // Don't fall back to goblin hurt/death for custom enemies — null makes
                // drawSprite() reuse the walk animation instead of flashing the goblin.
                Animation<TextureRegion> hurt  = (cfg != null) ? cfg.hurtAnim  : zombieHurtAnim;
                Animation<TextureRegion> death = (cfg != null) ? cfg.deathAnim : zombieDeathAnim;
                Zombie z = new Zombie(f, SPAWN_X, floorZombie, wave, walk, hurt, death);
                if (cfg != null) {
                    z.maxHp  = cfg.hp * (1f + (wave - 1) * 0.10f);
                    z.hp     = z.maxHp;
                    z.speed  = cfg.speed * 30f * (1f + (wave - 1) * 0.02f);
                    z.reward = cfg.level * 5 + wave * 3;
                }
                z.tintColor = LEVEL_TINT[currentLevel - 1];
                zombies.add(z);
                zombiesToSpawn--;
                spawnTimer = Math.max(0.55f, 1.5f - wave * 0.095f);
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
                    bullets.add(new Bullet(sol.x + 18f, sol.y + 14f, sol.damage, target, sol.type));
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

        // Tick platform swap flash timers
        for (int i = 0; i < 2; i++)
            if (platformSwapTimer[i] > 0) platformSwapTimer[i] = Math.max(0, platformSwapTimer[i] - delta);

        // Wave end check
        if (waveRunning && zombiesToSpawn == 0 && zombies.size == 0) {
            waveRunning = false;
            int bonus = 20 + wave * 10;
            money += bonus;
            addFloat("Wave " + wave + " clear! +" + bonus + "$", W / 2f - 80, H / 2f, Color.GREEN);
            if (wave >= WAVES_PER_LEVEL) {
                levelComplete = true;
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    void handleInput() {
        boolean touched      = Gdx.input.isTouched();
        float   mx           = Gdx.input.getX() * scaleX;
        float   my           = H - Gdx.input.getY() * scaleY;
        boolean justPressed  = touched && !wasTouched;
        boolean justReleased = !touched && wasTouched;

        // Settings modal intercepts all input while open
        if (settingsOpen) {
            handleSettingsInput(mx, my, touched, justPressed, justReleased);
            wasTouched = touched;
            return;
        }

        if (justPressed) {
            if (levelComplete) { nextLevel(); wasTouched = true; return; }
            if (victory)       { restartGame(); wasTouched = true; return; }
            if (gameOver)      { restartGame(); wasTouched = true; return; }

            // Settings button (check before wave to handle overlap)
            if (btnSettings.contains(mx, my)) { settingsOpen = true; wasTouched = true; return; }

            // Wave button
            if (btnWave.contains(mx, my) && !waveRunning) { startWave(); wasTouched = true; return; }

            // Platform buttons — begin drag-to-swap gesture
            for (int i = 0; i < 2; i++) {
                if (platformRects[i].contains(mx, my)) {
                    platDragIdx    = i;
                    platDragStartX = mx; platDragCurX = mx;
                    platDragStartY = my; platDragCurY = my;
                    wasTouched = true;
                    return;
                }
            }

            // R + click near hero sprite = sell
            if (Gdx.input.isKeyPressed(Input.Keys.R)) {
                for (int f = 0; f < 3; f++) {
                    for (int s = 0; s < 3; s++) {
                        Soldier sol = slots[f][s];
                        if (sol == null) continue;
                        float heroY = LEVEL_ZOMBIE_FLOOR_Y[currentLevel - 1][f] - FLOOR_ZOMBIE_OFFSET - 8f + (currentLevel == 1 ? 8f : 0f);
                        if (Math.abs(mx - sol.x) < 36f && my >= heroY && my <= heroY + 72f) {
                            money += sol.sellValue;
                            addFloat("Sold +" + sol.sellValue + "$", mx, my + 20, Color.GOLD);
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

        // Update platform drag cursor
        if (touched && platDragIdx >= 0) {
            platDragCurX = mx;
            platDragCurY = my;
        }

        // Update ghost position while dragging
        if (touched && isDragging) {
            dragX = mx;
            dragY = my;
        }

        // Release platform drag — direction-sensitive swap:
        // zone[0] = gap between 3F(f=0) and 2F(f=1) → always swapFloors(0,1)
        // zone[1] = gap between 2F(f=1) and 1F(f=2):
        //   drag UP (dy>0)  → swapFloors(0,1)  [2F moves up to 3F]
        //   drag DOWN (dy<0)→ swapFloors(1,2)  [2F moves down to 1F]
        if (justReleased && platDragIdx >= 0) {
            float dy = platDragCurY - platDragStartY;
            float dx = platDragCurX - platDragStartX;
            if (Math.abs(dy) > 20f || Math.abs(dx) > 20f) {
                int a, b;
                if (platDragIdx == 1 && dy > 0) {
                    a = 0; b = 1;  // zone 1 dragged UP: 2F goes up to 3F
                } else {
                    a = platDragIdx; b = platDragIdx + 1;  // default: swap own pair
                }
                swapFloors(a, b);
                platformSwapTimer[platDragIdx] = 0.5f;
            }
            platDragIdx = -1;
        }

        // Release: detect floor from drop Y, snap hero to nearest empty slot
        if (justReleased && isDragging) {
            int floorHit = -1;
            for (int f = 0; f < 3; f++) {
                float surface = LEVEL_ZOMBIE_FLOOR_Y[currentLevel - 1][f] - FLOOR_ZOMBIE_OFFSET;
                if (my >= surface) { floorHit = f; break; }
            }
            if (floorHit >= 0) {
                // Find nearest empty slot by X distance
                int slotHit = -1;
                float bestDist = Float.MAX_VALUE;
                for (int s = 0; s < 3; s++) {
                    if (slots[floorHit][s] == null) {
                        float cx = SLOT_X[s] + SLOT_W / 2f;
                        float dist = Math.abs(mx - cx);
                        if (dist < bestDist) { bestDist = dist; slotHit = s; }
                    }
                }
                if (slotHit >= 0) {
                    handleSlotDrop(floorHit, slotHit, dragType, mx, my);
                } else {
                    addFloat("No slots!", mx, my + 20, Color.RED);
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
            float sx = SLOT_X[s] + SLOT_W / 2f; // snap to slot center
            float sy = FLOOR_Y[f] + FLOOR_GROUND_OFFSET;
            slots[f][s] = new Soldier(type, f, s, sx, sy);
            addFloat("-" + cost + "$", sx, my + 20, Color.ORANGE);
        } else {
            addFloat("No gold!", mx, my + 20, Color.RED);
        }
    }

    /** Swap entire teams between two floors (all 3 slots). */
    void swapFloors(int a, int b) {
        for (int s = 0; s < 3; s++) {
            Soldier sa = slots[a][s];
            Soldier sb = slots[b][s];
            if (sa != null) {
                sa.y     = FLOOR_Y[b] + FLOOR_GROUND_OFFSET;
                sa.floor = b;
            }
            if (sb != null) {
                sb.y     = FLOOR_Y[a] + FLOOR_GROUND_OFFSET;
                sb.floor = a;
            }
            slots[a][s] = sb;
            slots[b][s] = sa;
        }
        addFloat("⇅ " + (a + 1) + "↔" + (b + 1) + "F !",
                 SLOT_X[1], (FLOOR_Y[a] + FLOOR_Y[b]) / 2f + 10, Color.CYAN);
    }

    void startWave() {
        wave++;
        waveRunning = true;
        // wave 1→3 enemies, wave 2→4, ..., wave 10→12
        int count = 2 + wave;
        zombiesToSpawn = count;
        spawnTimer = 0f;
        spawnFloors  = new int[count];
        spawnConfigs = new EnemyConfig[count];
        Array<EnemyConfig> pool = (enemyPools != null) ? enemyPools[currentLevel - 1] : null;

        // Gate enemy types: only enemies up to a level tier unlock each wave pair
        // wave 1-2: tier 1 | wave 3-4: tier 2 | wave 5-6: tier 3 | wave 7-8: tier 4 | wave 9-10: tier 5
        int maxEnemyTier = 1 + (wave - 1) / 2;
        Array<EnemyConfig> filteredPool = new Array<>();
        if (pool != null) {
            for (EnemyConfig ec : pool) {
                if (ec.level <= maxEnemyTier) filteredPool.add(ec);
            }
        }
        if (filteredPool.size == 0 && pool != null) filteredPool.addAll(pool);

        for (int i = 0; i < count; i++) {
            spawnFloors[i]  = (int)(Math.random() * 3);
            spawnConfigs[i] = filteredPool.size > 0
                ? filteredPool.get((int)(Math.random() * filteredPool.size))
                : null;
        }
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

    @SuppressWarnings("unchecked")
    void loadEnemyConfigs() {
        enemyPools = new Array[TOTAL_LEVELS];
        for (int i = 0; i < TOTAL_LEVELS; i++) enemyPools[i] = new Array<>();
        try {
            JsonValue root = new JsonReader().parse(Gdx.files.internal("enemies.json"));
            for (int lvl = 0; lvl < TOTAL_LEVELS; lvl++) {
                String key = LEVEL_ENEMY_POOL[lvl];
                JsonValue arr = root.get(key);
                if (arr == null) continue;
                for (JsonValue item = arr.child; item != null; item = item.next) {
                    EnemyConfig cfg = new EnemyConfig();
                    cfg.id     = item.getString("id",     "");
                    cfg.name   = item.getString("name",   "ENEMY");
                    cfg.hp     = item.getFloat("hp",      60f);
                    cfg.damage = item.getFloat("damage",  5f);
                    cfg.speed  = item.getFloat("speed",   1.5f);
                    cfg.level  = item.getInt("level",     1);
                    loadEnemySprites(cfg);
                    enemyPools[lvl].add(cfg);
                }
            }
        } catch (Exception e) {
            Gdx.app.log("EnemyConfig", "Could not load enemies.json: " + e.getMessage());
        }
    }

    void loadEnemySprites(EnemyConfig cfg) {
        if (cfg.id == null || cfg.id.isEmpty()) return;
        cfg.walkAnim  = tryLoadStrip("enemies/" + cfg.id + "_walk.png",  128, 128, 0.07f, com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP);
        cfg.hurtAnim  = tryLoadStrip("enemies/" + cfg.id + "_hurt.png",  128, 128, 0.07f, com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL);
        cfg.deathAnim = tryLoadStrip("enemies/" + cfg.id + "_death.png", 128, 128, 0.08f, com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL);
    }

    Animation<TextureRegion> tryLoadStrip(String path, int fw, int fh, float dur,
                                           Animation.PlayMode mode) {
        try {
            com.badlogic.gdx.graphics.Texture tex =
                new com.badlogic.gdx.graphics.Texture(Gdx.files.internal(path));
            tex.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
                          com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest);
            enemyTexList.add(tex);
            return buildStrip(tex, fw, fh, dur, mode);
        } catch (Exception e) {
            return null;
        }
    }

    void loadMap(int level) {
        if (tiledMap    != null) tiledMap.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        // Level 1: 16px tiles × scale 2. Levels 2-4: 32px tiles × scale 1.
        float scale = 1f;
        String[] mapFiles = { "level 1.tmx", "level 2.tmx", "level 3.....3.tmx" };
        tiledMap    = new TmxMapLoader().load(mapFiles[level - 1]);
        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap, scale);
        // Set Nearest filter on every tileset texture to prevent seam bleeding
        for (com.badlogic.gdx.maps.tiled.TiledMapTileSet ts : tiledMap.getTileSets()) {
            for (com.badlogic.gdx.maps.tiled.TiledMapTile tile : ts) {
                com.badlogic.gdx.graphics.g2d.TextureRegion region = tile.getTextureRegion();
                if (region != null && region.getTexture() != null) {
                    region.getTexture().setFilter(
                        Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                }
            }
        }
        if (mapCamera != null) {
            mapCamera.position.set(LEVEL_CAMERA_X[currentLevel - 1], LEVEL_CAMERA_Y[currentLevel - 1], 0);
            mapCamera.update();
        }
    }

    void nextLevel() {
        levelComplete = false;
        if (currentLevel >= TOTAL_LEVELS) {
            victory = true;
            return;
        }
        currentLevel++;
        wave = 0; lives = 3; money = 200;
        waveRunning = false; isDragging = false; wasTouched = false; platDragIdx = -1;
        zombies.clear(); bullets.clear(); floats.clear();
        slots = new Soldier[3][3];
        platformSwapTimer[0] = 0f; platformSwapTimer[1] = 0f;
        loadMap(currentLevel);
        addFloat("Level " + currentLevel + "!", W / 2f - 60, H / 2f, Color.CYAN);
    }

    void restartGame() {
        currentLevel = 1;
        lives = 3; money = 200; score = 0; wave = 0;
        waveRunning = false; gameOver = false; victory = false; levelComplete = false;
        isDragging = false; wasTouched = false; platDragIdx = -1;
        zombies.clear(); bullets.clear(); floats.clear();
        slots = new Soldier[3][3];
        platformSwapTimer[0] = 0f; platformSwapTimer[1] = 0f;
        loadMap(currentLevel);
    }

    // ── HUD icon builders (pixel art mirroring heart_coin_icons.html) ────────

    private void buildHudIcons() {
        iconHeart = buildHeartTex();
        iconCoin  = buildCoinTex();
    }

    /** Translates JS drawHeart() pixel-rect calls into a libGDX Texture. */
    private static Texture buildHeartTex() {
        final int S = 4;
        Pixmap p = new Pixmap(S * 20, S * 20, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        // shadow
        ic(p,"3a0808ff"); ir(p,S, 3,4,4,2); ir(p,S, 7,4,2,1); ir(p,S,12,4,4,2); ir(p,S,10,4,2,1);
        ir(p,S, 2,6,16,2); ir(p,S, 2,8,16,3); ir(p,S, 3,11,14,2); ir(p,S, 4,13,12,2);
        ir(p,S, 6,15,8,2); ir(p,S, 8,17,4,2); ir(p,S, 9,19,2,1);
        // dark outline
        ic(p,"880000ff"); ir(p,S, 2,3,4,2); ir(p,S, 6,3,2,1); ir(p,S,11,3,4,2); ir(p,S, 9,3,2,1);
        ir(p,S, 1,5,16,2); ir(p,S, 1,7,16,3); ir(p,S, 2,10,14,2); ir(p,S, 3,12,12,2);
        ir(p,S, 5,14,8,2); ir(p,S, 7,16,4,2); ir(p,S, 8,18,2,1);
        // main red
        ic(p,"dd2020ff"); ir(p,S, 3,3,3,2); ir(p,S, 6,2,2,2); ir(p,S,11,2,2,2); ir(p,S, 9,3,3,2);
        ir(p,S, 2,5,15,2); ir(p,S, 2,7,15,3); ir(p,S, 3,10,13,2); ir(p,S, 4,12,11,2);
        ir(p,S, 6,14,7,2); ir(p,S, 8,16,3,2); ir(p,S, 9,18,1,1);
        // highlights
        ic(p,"ff6060ff"); ir(p,S, 3,3,2,3); ir(p,S, 5,3,1,2); ir(p,S, 3,5,4,1); ir(p,S, 2,5,2,2);
        ir(p,S, 9,2,2,3); ir(p,S,11,3,1,2);
        // mid tone
        ic(p,"ee3030ff"); ir(p,S, 2,7,5,2); ir(p,S,11,7,4,2);
        // bottom-right shadow
        ic(p,"bb1818ff"); ir(p,S,13,9,3,3); ir(p,S,12,11,3,2); ir(p,S,10,13,3,2); ir(p,S, 8,15,3,2);
        // shine dots
        ic(p,"ffaaaaff"); ir(p,S, 4,4,1,1); ir(p,S,10,3,1,1);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return t;
    }

    /** Translates JS drawCoin() pixel-rect calls into a libGDX Texture. */
    private static Texture buildCoinTex() {
        final int S = 4;
        Pixmap p = new Pixmap(S * 20, S * 20, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        // shadow rim
        ic(p,"4a3000ff"); ir(p,S, 4,1,11,1); ir(p,S, 2,2,15,1); ir(p,S, 1,3,17,13);
        ir(p,S, 2,16,15,1); ir(p,S, 4,17,11,1);
        // outer gold ring
        ic(p,"7a5500ff"); ir(p,S, 4,1,11,1); ir(p,S, 2,2,15,1);
        ir(p,S, 1,3,2,13); ir(p,S,16,3,2,13); ir(p,S, 2,16,15,1); ir(p,S, 4,17,11,1);
        // coin face fill
        ic(p,"d4a030ff"); ir(p,S, 3,2,13,1); ir(p,S, 2,3,15,13); ir(p,S, 3,16,13,1);
        // left bright edge
        ic(p,"f0c040ff"); ir(p,S, 2,3,2,13); ir(p,S, 3,2,2,1); ir(p,S, 3,16,2,1);
        // top bright edge
        ic(p,"ffe060ff"); ir(p,S, 4,2,9,1);
        // bottom-right dark edge
        ic(p,"a07820ff"); ir(p,S,15,3,2,13); ir(p,S,13,16,2,1);
        // inner circle
        ic(p,"b88820ff"); ir(p,S, 5,4,9,1); ir(p,S, 4,5,1,9); ir(p,S,14,5,1,9); ir(p,S, 5,14,9,1);
        // $ symbol
        ic(p,"7a5500ff"); ir(p,S, 9,5,1,9); ir(p,S, 8,5,3,1); ir(p,S, 7,6,1,2); ir(p,S,10,6,1,2);
        ir(p,S, 7,8,5,1); ir(p,S, 7,10,1,2); ir(p,S,10,10,1,2); ir(p,S, 8,12,3,1);
        // shine spot
        ic(p,"ffe880ff"); ir(p,S, 5,4,3,2); ir(p,S, 4,5,2,3);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return t;
    }

    private static void ic(Pixmap p, String hex) { p.setColor(Color.valueOf(hex)); }
    private static void ir(Pixmap p, int S, int x, int y, int w, int h) {
        p.fillRectangle(x * S, y * S, w * S, h * S);
    }

    // ── Drawing ───────────────────────────────────────────────────────────────
    void draw() {
        // Per-level sky colour fills the black gaps between floor strips
        float[][] bgColors = {
            { 0.72f, 0.88f, 0.89f },   // Level 1: winter sky
            { 0.69f, 0.67f, 0.14f },   // Level 2: forest
            { 0.10f, 0.20f, 0.39f },   // Level 3: underwater
        };
        float[] bg = bgColors[currentLevel - 1];
        Gdx.gl.glClearColor(bg[0], bg[1], bg[2], 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // ── TMX map background ──
        // Snap camera to integer pixels to eliminate sub-pixel tile seams
        mapCamera.position.x = (float) Math.round(mapCamera.position.x);
        mapCamera.position.y = (float) Math.round(mapCamera.position.y);
        mapCamera.update();
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
        sr.end();

        // ── SpriteBatch pass (text + sprites) ──
        game.batch.begin();
        drawSlotSprites();
        drawSpriteSoldiers();
        drawZombieSprites();
        if (isDragging) drawSpriteDragGhost();
        drawHUD();
        drawSlotLabels();
        drawBottomPanelText();
        drawFloatTexts();
        if (gameOver)      drawGameOver();
        if (levelComplete) drawLevelComplete();
        if (victory)       drawVictory();
        game.batch.end();

        if (settingsOpen) drawSettingsModal();
    }

    void drawSpriteSoldiers() {
        for (int f = 0; f < 3; f++) {
            for (int s = 0; s < 3; s++) {
                Soldier sol = slots[f][s];
                if (sol == null) continue;
                // Position sprite so feet sit just on top of the visual TMX floor surface
                float heroRaise = currentLevel == 1 ? 8f : 0f;
                float drawY = LEVEL_ZOMBIE_FLOOR_Y[currentLevel - 1][sol.floor] - FLOOR_ZOMBIE_OFFSET - 8f + heroRaise;
                if (sol.type == Soldier.Type.SOLDIER && soldierIdleAnim != null) {
                    TextureRegion frame = soldierIdleAnim.getKeyFrame(soldierStateTime);
                    float dw = 64f, dh = 64f;
                    game.batch.setColor(1f, 1f, 1f, 1f);
                    game.batch.draw(frame, sol.x - dw / 2f, drawY, dw, dh);
                } else {
                    Animation<TextureRegion> anim = getHeroAnim(sol.type);
                    if (anim != null) {
                        TextureRegion frame = anim.getKeyFrame(soldierStateTime);
                        float dw = 48f, dh = 64f;
                        game.batch.setColor(1f, 1f, 1f, 1f);
                        game.batch.draw(frame, sol.x - dw / 2f, drawY, dw, dh);
                    }
                }
            }
        }
        game.batch.setColor(1f, 1f, 1f, 1f);
    }

    void drawSpriteDragGhost() {
        Animation<TextureRegion> anim = dragType == Soldier.Type.SOLDIER
            ? soldierIdleAnim : getHeroAnim(dragType);
        if (anim == null) return;
        TextureRegion frame = anim.getKeyFrame(soldierStateTime);
        float dw = 64f, dh = 64f;
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
        // Platform tiles are rendered by the TMX map renderer
    }

    /**
     * Draws opaque dark rectangles over the gap areas between floor strips (and above/below
     * the outer strips) using mapCamera projection — so the overlay coordinates match what
     * the TMX renderer produced.  Required for levels whose tilesets fill every row with
     * bright content (forest, underwater) unlike the winter map that has native dark gap tiles.
     */
    void drawMapGapOverlays() {
        float camY   = LEVEL_CAMERA_Y[currentLevel - 1];
        float camOff = camY - H / 2f;

        // Gap colour per level: dark earth tone that matches the tileset palette
        float gr, gg, gb;
        if (currentLevel == 3) {
            gr = 0.04f; gg = 0.08f; gb = 0.16f;   // Level 3: deep underwater dark blue
        } else {
            gr = 0.22f; gg = 0.16f; gb = 0.07f;   // Level 2: dark forest earth / bark
        }

        sr.setProjectionMatrix(mapCamera.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(gr, gg, gb, 1f);

        // Gap between floor 0 (top) and floor 1 (middle)
        sr.rect(0, FLOOR_Y[1] + FLOOR_H + camOff, W, FLOOR_Y[0] - FLOOR_Y[1] - FLOOR_H);
        // Gap between floor 1 (middle) and floor 2 (bottom)
        sr.rect(0, FLOOR_Y[2] + FLOOR_H + camOff, W, FLOOR_Y[1] - FLOOR_Y[2] - FLOOR_H);
        // Above floor 0 (sits behind HUD)
        float aboveY = FLOOR_Y[0] + FLOOR_H + camOff;
        sr.rect(0, aboveY, W, camY + H / 2f - aboveY);
        // Below floor 2 (sits behind hero panel)
        sr.rect(0, camY - H / 2f, W, FLOOR_Y[2]);

        sr.end();
        sr.setProjectionMatrix(game.batch.getProjectionMatrix());
    }

    void drawSlots() {
        // Per-level slot glow color: dungeon=magenta, winter=ice-blue, forest=green, underwater=teal
        float[][] levelColors = {
            { 0.85f, 0.30f, 1.00f },   // Level 1: purple
            { 0.25f, 0.65f, 1.00f },   // Level 2: ice blue
            { 0.30f, 1.00f, 0.40f },   // Level 3: forest green
            { 0.10f, 0.90f, 0.90f },   // Level 4: teal
        };
        float[] c = levelColors[currentLevel - 1];
        float cr = c[0], cg = c[1], cb = c[2];
        // Pulse 0..1 at ~1.5 Hz
        float pulse = (float)(Math.sin(soldierStateTime * 2.0 * Math.PI * 1.5) * 0.5 + 0.5);

        // Levels 1 and 3 use sprite indicators instead of ellipses
        if (currentLevel == 1 && slotWinterRegion    != null) return;
        if (currentLevel == 3 && slotUnderwaterRegion != null) return;

        for (int f = 0; f < 3; f++) {
            float surface = LEVEL_ZOMBIE_FLOOR_Y[currentLevel - 1][f] - FLOOR_ZOMBIE_OFFSET - 8f;
            for (int s = 0; s < 3; s++) {
                if (slots[f][s] != null) continue; // occupied – hero is visible, no marker needed

                float cx = SLOT_X[s] + SLOT_W / 2f;

                // Outer halo – very faint, wide
                sr.setColor(cr, cg, cb, 0.08f + 0.06f * pulse);
                sr.ellipse(cx - 36f, surface - 6f, 72f, 14f);
                // Mid ring
                sr.setColor(cr, cg, cb, 0.18f + 0.10f * pulse);
                sr.ellipse(cx - 24f, surface - 4f, 48f, 10f);
                // Bright core
                sr.setColor(cr + 0.15f * (1f - cr), cg + 0.05f * (1f - cg), cb + 0.05f * (1f - cb),
                            0.45f + 0.25f * pulse);
                sr.ellipse(cx - 12f, surface - 2.5f, 24f, 6f);
            }
        }
    }

    void drawSlotSprites() {
        TextureRegion region = null;
        if (currentLevel == 1) region = slotWinterRegion;
        if (currentLevel == 3) region = slotUnderwaterRegion;
        if (region == null) return;
        for (int f = 0; f < 3; f++) {
            float surface = LEVEL_ZOMBIE_FLOOR_Y[currentLevel - 1][f] - FLOOR_ZOMBIE_OFFSET - 8f;
            for (int s = 0; s < 3; s++) {
                if (slots[f][s] != null) continue;
                float cx = SLOT_X[s] + SLOT_W / 2f;
                float yOff = currentLevel == 3 ? -12f : 8f;
                game.batch.draw(region, cx - 16f, surface + yOff, 32f, 32f);
            }
        }
    }

    Texture tryLoadTexture(String path) {
        try { return new Texture(Gdx.files.internal(path)); } catch (Exception e) { return null; }
    }

    Animation<TextureRegion> getHeroAnim(Soldier.Type t) {
        switch (t) {
            case SNIPER: return sniperIdleAnim;
            case MG:     return mgIdleAnim;
            case ARCHER: return archerIdleAnim;
            case MAGE:   return mageIdleAnim;
            default:     return null;
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
            for (int s = 0; s < 3; s++) {
                Soldier sol = slots[f][s];
                if (sol == null) continue;
                // Skip shape rendering for types that have a sprite animation
                if (sol.type != Soldier.Type.SOLDIER && getHeroAnim(sol.type) != null) {
                    sol.drawHPBar(sr);
                } else {
                    sol.draw(sr);
                }
            }
    }

    void drawZombies() {
        for (Zombie z : zombies) {
            if (zombieWalkAnim != null) z.drawHPBar(sr);
            else z.draw(sr);
        }
    }

    void drawZombieSprites() {
        if (zombieWalkAnim == null) return;
        for (Zombie z : zombies) {
            game.batch.setColor(z.tintColor);
            z.drawSprite(game.batch);
        }
        game.batch.setColor(1f, 1f, 1f, 1f);
    }

    void drawBullets() {
        for (Bullet b : bullets) b.draw(sr);
    }

    void drawHUD() {
        // Heart icon + lives
        game.batch.setColor(1f, 1f, 1f, 1f);
        game.batch.draw(iconHeart, 4f, H - 34f, 24f, 24f);
        font.setColor(Color.CYAN);
        font.draw(game.batch, "" + lives, 32f, H - 8);

        // Coin icon + money
        game.batch.setColor(1f, 1f, 1f, 1f);
        game.batch.draw(iconCoin, 84f, H - 34f, 24f, 24f);
        font.setColor(Color.YELLOW);
        font.draw(game.batch, "" + money, 112f, H - 8);
        font.setColor(Color.GREEN);
        font.draw(game.batch, "Wave: " + wave + "/" + WAVES_PER_LEVEL, 185, H - 8);
        font.setColor(new Color(0.5f, 0.8f, 1f, 1f));
        font.setColor(Color.WHITE);
        font.draw(game.batch, "Score: " + score, 420, H - 8);

        // Wave / play button
        if (playBtnTex != null) {
            game.batch.setColor(waveRunning ? 0.45f : 1f, waveRunning ? 0.45f : 1f, waveRunning ? 0.45f : 1f, 1f);
            game.batch.draw(playBtnTex, btnWave.x, btnWave.y, btnWave.width, btnWave.height);
            game.batch.setColor(1f, 1f, 1f, 1f);
        } else {
            drawButton(btnWave, waveRunning ? "⌛" : "▶", false);
        }
        // Settings icon
        if (settingsIconTex != null) {
            game.batch.setColor(1f, 1f, 1f, 1f);
            game.batch.draw(settingsIconTex, btnSettings.x, btnSettings.y, btnSettings.width, btnSettings.height);
        }

        // Flash "swap!" text briefly after a swap fires
        for (int i = 0; i < 2; i++) {
            if (platformSwapTimer[i] > 0) {
                Rectangle r = platformRects[i];
                fontSmall.setColor(Color.CYAN);
                fontSmall.draw(game.batch, "⇅ swap!", r.x + 6, r.y + r.height - 3);
            }
        }

    }

    void drawButton(Rectangle r, String label, boolean selected) {
        // Drawn via ShapeRenderer would need end/begin — just use font color trick
        fontSmall.setColor(selected ? Color.WHITE : new Color(0.6f, 0.75f, 1f, 1f));
        fontSmall.draw(game.batch, "[" + label + "]", r.x + 4, r.y + r.height - 5);
    }

    void drawSlotLabels() {
        for (int f = 0; f < 3; f++) {
            for (int s = 0; s < 3; s++) {
                Soldier sol = slots[f][s];
                if (sol == null) continue;
                // Draw label above hero's head at actual position
                float heroBottomY = LEVEL_ZOMBIE_FLOOR_Y[currentLevel - 1][f] - FLOOR_ZOMBIE_OFFSET - 8f + (currentLevel == 1 ? 8f : 0f);
                fontSmall.setColor(getSoldierColor(sol.type));
                String lbl = getLevelHeroShort(sol.type);
                fontSmall.draw(game.batch, lbl, sol.x - 12f, heroBottomY + 72f);
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
            // Shape mini body only for non-SOLDIER types without sprites
            if (t != Soldier.Type.SOLDIER && getHeroAnim(t) == null) {
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
                float iw = 36f, ih = 36f;
                float cx = r.x + r.width / 2f;
                game.batch.setColor(1f, 1f, 1f, 1f);
                game.batch.draw(frame, cx - iw / 2f, r.y + 10f, iw, ih);
                game.batch.setColor(1f, 1f, 1f, 1f);
            }
            // Animated sprite icons for other hero types
            Animation<TextureRegion> heroAnim = getHeroAnim(t);
            if (heroAnim != null) {
                TextureRegion heroFrame = heroAnim.getKeyFrame(soldierStateTime);
                float iw = 36f, ih = 36f;
                float cx = r.x + r.width / 2f;
                game.batch.setColor(1f, 1f, 1f, 1f);
                game.batch.draw(heroFrame, cx - iw / 2f, r.y + 10f, iw, ih);
                game.batch.setColor(1f, 1f, 1f, 1f);
            }
            fontSmall.setColor(getSoldierColor(t));
            fontSmall.draw(game.batch, getLevelHeroName(t), r.x + 4, r.y + r.height - 3);
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
        if (gameOverTex != null) {
            game.batch.setColor(1f, 1f, 1f, 1f);
            game.batch.draw(gameOverTex, 0, 0, W, H);
        } else {
            fontBig.setColor(Color.RED);
            fontBig.draw(game.batch, "GAME OVER", W / 2f - 100, H / 2f + 30);
        }
        font.setColor(Color.YELLOW);
        font.draw(game.batch, "Tap to restart", W / 2f - 70, 60f);
    }

    void drawLevelComplete() {
        fontBig.setColor(Color.GREEN);
        fontBig.draw(game.batch, "LEVEL " + currentLevel + " CLEAR!", W / 2f - 140, H / 2f + 40);
        font.setColor(Color.WHITE);
        if (currentLevel < TOTAL_LEVELS) {
            font.draw(game.batch, "Next level: " + (currentLevel + 1) + " / " + TOTAL_LEVELS, W / 2f - 80, H / 2f);
            font.setColor(Color.YELLOW);
            font.draw(game.batch, "Tap to continue", W / 2f - 70, H / 2f - 36);
        } else {
            font.draw(game.batch, "Tap for final victory!", W / 2f - 90, H / 2f - 36);
        }
    }

    void drawVictory() {
        if (winTex != null) {
            game.batch.setColor(1f, 1f, 1f, 1f);
            game.batch.draw(winTex, 0, 0, W, H);
        } else {
            fontBig.setColor(Color.GOLD);
            fontBig.draw(game.batch, "VICTORY!", W / 2f - 80, H / 2f + 50);
        }
        font.setColor(Color.YELLOW);
        font.draw(game.batch, "Tap to restart", W / 2f - 70, 60f);
    }

    // ── Level-specific hero names ─────────────────────────────────────────────

    String getLevelHeroName(Soldier.Type t) {
        int i = t.ordinal();
        String[] names = currentLevel == 1
            ? new String[]{ "BLIZZARD", "VALKYRIE", "ICE LORD", "GLACIAL", "ETR.WNTR" }
            : new String[]{ "DIVER",    "TIDECALLR","SEA GOD",  "LEVIATHAN","OCEAN GOD" };
        return i < names.length ? names[i] : "???";
    }

    String getLevelHeroShort(Soldier.Type t) {
        int i = t.ordinal();
        String[] names = currentLevel == 1
            ? new String[]{ "BLZ", "VLK", "ICE", "GLC", "ETW" }
            : new String[]{ "DVR", "TDE", "SEA", "LVT", "OCG" };
        return i < names.length ? names[i] : "???";
    }

    // ── Settings modal ────────────────────────────────────────────────────────

    void handleSettingsInput(float mx, float my, boolean touched, boolean justPressed, boolean justReleased) {
        if (justPressed) {
            if (btnCloseSettings.contains(mx, my)) {
                settingsOpen = false;
                return;
            }
            if (btnBackToMenu.contains(mx, my)) {
                game.playMusic("menu.mp3");
                game.setScreen(new MenuScreen(game));
                return;
            }
            // Slider hit (extended vertical area for easier tapping)
            if (mx >= settingsSlider.x && mx <= settingsSlider.x + settingsSlider.width
                    && my >= settingsSlider.y - 12f && my <= settingsSlider.y + settingsSlider.height + 12f) {
                draggingSlider = true;
            }
            // Click outside modal closes it
            if (!settingsModal.contains(mx, my)) {
                settingsOpen = false;
                return;
            }
        }
        if (touched && draggingSlider) {
            musicVolume = Math.max(0f, Math.min(1f,
                    (mx - settingsSlider.x) / settingsSlider.width));
            if (game.currentMusic != null) game.currentMusic.setVolume(musicVolume);
        }
        if (justReleased) draggingSlider = false;
    }

    void drawSettingsModal() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Dark screen overlay + modal background
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.62f);
        sr.rect(0, 0, W, H);
        sr.setColor(0.04f, 0.04f, 0.14f, 0.97f);
        sr.rect(settingsModal.x, settingsModal.y, settingsModal.width, settingsModal.height);
        // Modal border
        sr.setColor(0.75f, 0.55f, 0.10f, 1f);
        sr.rect(settingsModal.x,                               settingsModal.y,                                settingsModal.width, 2);
        sr.rect(settingsModal.x,                               settingsModal.y + settingsModal.height - 2,     settingsModal.width, 2);
        sr.rect(settingsModal.x,                               settingsModal.y,                                2, settingsModal.height);
        sr.rect(settingsModal.x + settingsModal.width - 2,     settingsModal.y,                                2, settingsModal.height);
        // Slider track
        sr.setColor(0.25f, 0.25f, 0.25f, 1f);
        sr.rect(settingsSlider.x, settingsSlider.y, settingsSlider.width, settingsSlider.height);
        // Slider fill
        sr.setColor(0.18f, 0.55f, 1.00f, 1f);
        sr.rect(settingsSlider.x, settingsSlider.y, settingsSlider.width * musicVolume, settingsSlider.height);
        // Slider knob
        sr.setColor(Color.WHITE);
        float knobX = settingsSlider.x + settingsSlider.width * musicVolume;
        sr.circle(knobX, settingsSlider.y + settingsSlider.height / 2f, 9f);
        // Close button
        sr.setColor(0.55f, 0.08f, 0.08f, 1f);
        sr.rect(btnCloseSettings.x, btnCloseSettings.y, btnCloseSettings.width, btnCloseSettings.height);
        // Back-to-menu button
        sr.setColor(0.08f, 0.28f, 0.50f, 1f);
        sr.rect(btnBackToMenu.x, btnBackToMenu.y, btnBackToMenu.width, btnBackToMenu.height);
        sr.end();

        // Text
        game.batch.begin();
        float cx = settingsModal.x + settingsModal.width / 2f;
        font.setColor(Color.WHITE);
        layout.setText(font, "SETTINGS");
        font.draw(game.batch, "SETTINGS", cx - layout.width / 2f,
                  settingsModal.y + settingsModal.height - 16f);

        fontSmall.setColor(new Color(0.85f, 0.85f, 0.85f, 1f));
        fontSmall.draw(game.batch, "Music volume: " + (int)(musicVolume * 100) + "%",
                       settingsSlider.x, settingsSlider.y + 36f);

        fontSmall.setColor(new Color(0.55f, 0.70f, 0.90f, 1f));
        fontSmall.draw(game.batch, "Drag hero from panel | R+click = sell",
                       settingsSlider.x, settingsSlider.y - 20f);

        fontSmall.setColor(Color.WHITE);
        layout.setText(fontSmall, "CLOSE");
        fontSmall.draw(game.batch, "CLOSE",
                       btnCloseSettings.x + (btnCloseSettings.width - layout.width) / 2f,
                       btnCloseSettings.y + 20f);
        layout.setText(fontSmall, "MENU");
        fontSmall.draw(game.batch, "MENU",
                       btnBackToMenu.x + (btnBackToMenu.width - layout.width) / 2f,
                       btnBackToMenu.y + 20f);
        game.batch.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // ── Screen boilerplate ────────────────────────────────────────────────────
    @Override public void resize(int width, int height) {
        game.batch.getProjectionMatrix().setToOrtho2D(0, 0, W, H);
        sr.getProjectionMatrix().setToOrtho2D(0, 0, W, H);
        scaleX = (float) W / width;
        scaleY = (float) H / height;
        if (mapCamera != null) {
            mapCamera.viewportWidth  = W;
            mapCamera.viewportHeight = H;
            mapCamera.update();
        }
    }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}
    @Override public void dispose() {
        sr.dispose();
        font.dispose();
        fontBig.dispose();
        fontSmall.dispose();
        if (tiledMap    != null) { tiledMap.dispose();    tiledMap    = null; }
        if (mapRenderer != null) { mapRenderer.dispose(); mapRenderer = null; }
        if (soldierTex   != null) soldierTex.dispose();
        if (sniperTex  != null) sniperTex.dispose();
        if (mgTex      != null) mgTex.dispose();
        if (archerTex  != null) archerTex.dispose();
        if (mageTex    != null) mageTex.dispose();
        if (zombieIdleTex  != null) zombieIdleTex.dispose();
        if (zombieHurtTex  != null) zombieHurtTex.dispose();
        if (zombieDeathTex != null) zombieDeathTex.dispose();
        for (com.badlogic.gdx.graphics.Texture t : enemyTexList) t.dispose();
        enemyTexList.clear();
        if (iconHeart      != null) iconHeart.dispose();
        if (iconCoin       != null) iconCoin.dispose();
        if (slotTex        != null) slotTex.dispose();
        if (playBtnTex     != null) playBtnTex.dispose();
        if (settingsIconTex!= null) settingsIconTex.dispose();
        if (gameOverTex    != null) gameOverTex.dispose();
        if (winTex         != null) winTex.dispose();
    }
}
