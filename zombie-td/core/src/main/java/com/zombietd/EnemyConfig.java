package com.zombietd;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class EnemyConfig {
    // ── JSON fields ──────────────────────────────────────────────────────────
    public String id     = "";
    public String name   = "ZOMBIE";
    public float  hp     = 60f;
    public float  damage = 5f;
    public float  speed  = 1.5f;
    public int    level  = 1;

    // ── Runtime-only: loaded from assets/enemies/{id}_*.png ─────────────────
    public Animation<TextureRegion> walkAnim;
    public Animation<TextureRegion> hurtAnim;
    public Animation<TextureRegion> deathAnim;

    /** No-arg constructor required by libGDX Json parser. */
    public EnemyConfig() {}
}

