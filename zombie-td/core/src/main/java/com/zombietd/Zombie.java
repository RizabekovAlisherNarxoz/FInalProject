package com.zombietd;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class Zombie {
    public float x, y;
    public float hp, maxHp;
    public float speed;
    public int floor;           // 0=top, 1=mid, 2=bottom
    public boolean dead = false;
    public boolean rewardGiven = false;
    public int reward = 15;

    // Death animation timing
    public float deathTimer = 0f;
    public static final float DEATH_DURATION = 1.2f;

    // Hurt flash state
    private float hurtTimer = 0f;
    private boolean hurtPlaying = false;
    private static final float HURT_DURATION = 0.4f;

    // Per-state animation clocks
    private float walkTime  = 0f;
    private float deathTime = 0f;

    // Sprite dimensions in world units
    public static final float SPRITE_W = 44f;
    public static final float SPRITE_H = 44f;

    // Injected from GameScreen — null = fallback to shape rendering
    Animation<TextureRegion> walkAnim;
    Animation<TextureRegion> hurtAnim;
    Animation<TextureRegion> dieAnim;

    public Zombie(int floor, float startX, float floorY, int wave,
                  Animation<TextureRegion> walk,
                  Animation<TextureRegion> hurt,
                  Animation<TextureRegion> die) {
        this.floor  = floor;
        this.x      = startX;
        this.y      = floorY;
        this.maxHp  = 60 + wave * 25;
        this.hp     = maxHp;
        this.speed  = 55 + wave * 6;
        this.reward = 15 + wave * 3;
        this.walkAnim = walk;
        this.hurtAnim = hurt;
        this.dieAnim  = die;
    }

    public void update(float delta) {
        if (dead) {
            deathTimer += delta;
            deathTime  += delta;
            return;
        }
        walkTime += delta;
        if (hurtPlaying) {
            hurtTimer += delta;
            if (hurtTimer >= HURT_DURATION) hurtPlaying = false;
        }
        x -= speed * delta;
    }

    public void takeDamage(float dmg) {
        hp -= dmg;
        if (hp <= 0 && !dead) {
            dead      = true;
            deathTime = 0f;
        } else if (hp > 0) {
            hurtPlaying = true;
            hurtTimer   = 0f;
        }
    }

    /** True once the death animation has fully played and can be removed. */
    public boolean isDone() {
        return dead && deathTimer >= DEATH_DURATION;
    }

    // ── Sprite draw ── call inside SpriteBatch.begin/end ─────────────────────
    public void drawSprite(SpriteBatch batch) {
        if (walkAnim == null) return;
        TextureRegion frame;
        if (dead) {
            frame = dieAnim != null
                ? dieAnim.getKeyFrame(deathTime, false)
                : walkAnim.getKeyFrame(walkTime, true);
        } else if (hurtPlaying && hurtAnim != null) {
            frame = hurtAnim.getKeyFrame(hurtTimer, false);
        } else {
            frame = walkAnim.getKeyFrame(walkTime, true);
        }
        batch.draw(frame, x - SPRITE_W / 2f, y - SPRITE_H / 2f, SPRITE_W, SPRITE_H);
    }

    // ── HP bar ── ShapeRenderer.Filled must be active ─────────────────────────
    public void drawHPBar(ShapeRenderer sr) {
        if (dead) return;
        float hpPct  = hp / maxHp;
        float barTop = (walkAnim != null) ? y + SPRITE_H / 2f + 4f : y + 42f;
        sr.setColor(Color.DARK_GRAY);
        sr.rect(x - 14, barTop, 28, 4);
        sr.setColor(hpPct > 0.5f ? Color.GREEN : hpPct > 0.25f ? Color.YELLOW : Color.RED);
        sr.rect(x - 14, barTop, 28 * hpPct, 4);
    }

    // ── Shape fallback ── used when textures are not loaded ───────────────────
    public void draw(ShapeRenderer sr) {
        if (dead) return;
        float wobble   = (float) Math.sin(walkTime * 8f) * 2f;
        float drawY    = y + wobble;
        float hpPct    = hp / maxHp;
        Color bodyColor = hpPct > 0.5f ? new Color(0.24f, 0.42f, 0.24f, 1f)
                        : hpPct > 0.25f ? new Color(0.5f, 0.35f, 0.1f, 1f)
                        : new Color(0.5f, 0.15f, 0.15f, 1f);

        sr.setColor(bodyColor);
        sr.rect(x - 8, drawY, 16, 20);
        sr.setColor(hpPct > 0.5f ? new Color(0.3f, 0.55f, 0.3f, 1f) : new Color(0.55f, 0.3f, 0.3f, 1f));
        sr.circle(x, drawY + 26, 9);
        sr.setColor(Color.RED);
        sr.circle(x - 3, drawY + 28, 2.5f);
        sr.circle(x + 4, drawY + 28, 2.5f);
        sr.setColor(bodyColor);
        sr.rectLine(x - 8, drawY + 14, x - 20, drawY + 10, 3);
        sr.rectLine(x + 8, drawY + 12, x + 16, drawY + 18, 3);

        sr.setColor(Color.DARK_GRAY);
        sr.rect(x - 14, drawY + 38, 28, 4);
        sr.setColor(hpPct > 0.5f ? Color.GREEN : hpPct > 0.25f ? Color.YELLOW : Color.RED);
        sr.rect(x - 14, drawY + 38, 28 * hpPct, 4);
    }
}
