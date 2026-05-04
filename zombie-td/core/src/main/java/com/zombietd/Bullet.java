package com.zombietd;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class Bullet {
    public float x, y;
    public float speed = 320f;
    public float damage;
    public Zombie target;
    public boolean dead = false;
    public Soldier.Type type;

    // Rotation/age for animated projectiles
    private float age = 0f;

    public Bullet(float x, float y, float damage, Zombie target, Soldier.Type type) {
        this.x = x;
        this.y = y;
        this.damage = damage;
        this.target = target;
        this.type = type;
    }

    public void update(float delta) {
        age += delta;
        if (target == null || target.dead) { dead = true; return; }

        float tx = target.x;
        float ty = target.y + 15;
        float dx = tx - x;
        float dy = ty - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist < 8f) {
            target.takeDamage(damage);
            dead = true;
            return;
        }

        x += (dx / dist) * speed * delta;
        y += (dy / dist) * speed * delta;
    }

    public void draw(ShapeRenderer sr) {
        if (type == null) { drawDefault(sr); return; }
        switch (type) {
            case SNIPER:  drawArrow(sr);  break;  // ra → arrow
            case ARCHER:  drawArrow(sr);  break;  // sc → arrow
            case MG:      drawIce(sr);    break;  // kn → sword beam (use holy as star)
            case MAGE:    drawHoly(sr);   break;  // bm → ice → use holy orb for mage
            case SOLDIER: drawDefault(sr); break;
            default:      drawDefault(sr); break;
        }
    }

    // ── yellow/orange orb (soldier default) ───────────────────────────────────
    private void drawDefault(ShapeRenderer sr) {
        sr.setColor(new Color(1f, 0.85f, 0.15f, 1f));
        sr.circle(x, y, 3.5f);
        sr.setColor(new Color(1f, 0.5f, 0.1f, 0.5f));
        drawTrail(sr, 10);
    }

    // ── arrow: brown shaft + green tip (sniper / archer) ─────────────────────
    private void drawArrow(ShapeRenderer sr) {
        float dx = 1f, dy = 0f;
        if (target != null && !target.dead) {
            float tdx = target.x - x, tdy = (target.y + 15) - y;
            float d = (float) Math.sqrt(tdx * tdx + tdy * tdy);
            if (d > 0) { dx = tdx / d; dy = tdy / d; }
        }
        // Shaft
        sr.setColor(new Color(0.53f, 0.33f, 0.13f, 1f));
        sr.rectLine(x, y, x - dx * 10, y - dy * 10, 1.5f);
        // Tip
        float tipSize = (type == Soldier.Type.SNIPER) ? 4f : 3f;
        Color tipCol = (type == Soldier.Type.SNIPER)
            ? new Color(0.67f, 0.87f, 0.27f, 1f)   // green (ranger)
            : new Color(0.67f, 0.87f, 0.27f, 1f);   // green (scout)
        sr.setColor(tipCol);
        sr.triangle(
            x + dx * tipSize, y + dy * tipSize,
            x - dy * tipSize * 0.7f, y + dx * tipSize * 0.7f,
            x + dy * tipSize * 0.7f, y - dx * tipSize * 0.7f
        );
        // Feather trail
        sr.setColor(new Color(0.8f, 0.8f, 0.35f, 0.5f));
        drawTrail(sr, 6);
    }

    // ── ice shards: rotating blue spikes (MG ← knight sword beam) ────────────
    private void drawIce(ShapeRenderer sr) {
        float rot = age * 3f;
        float r = 4f;
        Color col = new Color(0.27f, 0.67f, 1f, 1f);
        sr.setColor(col);
        for (int i = 0; i < 4; i++) {
            float angle = rot + (float)(i * Math.PI / 2);
            float cx2 = x + (float) Math.cos(angle) * r;
            float cy2 = y + (float) Math.sin(angle) * r;
            sr.rectLine(x, y, cx2, cy2, 1.5f);
        }
        // Core
        sr.setColor(new Color(0.55f, 0.87f, 1f, 1f));
        sr.circle(x, y, 2f);
        // Trail
        sr.setColor(new Color(0.27f, 0.67f, 1f, 0.4f));
        drawTrail(sr, 7);
    }

    // ── holy star burst: gold rays (mage ← blizzard ice / mage) ─────────────
    private void drawHoly(ShapeRenderer sr) {
        float rot = age * 2.5f;
        float r = 5f;
        Color col  = new Color(0.85f, 0.20f, 0.85f, 1f); // purple for mage
        Color col2 = new Color(1f, 0.7f, 1f, 1f);
        // Rays
        sr.setColor(col);
        for (int i = 0; i < 4; i++) {
            float angle = rot + (float)(i * Math.PI / 4);
            float cx2 = x + (float) Math.cos(angle) * r;
            float cy2 = y + (float) Math.sin(angle) * r;
            sr.rectLine(x, y, cx2, cy2, 1f);
        }
        // Core
        sr.setColor(col2);
        sr.circle(x, y, 2.5f);
        // Glow trail
        sr.setColor(new Color(0.85f, 0.20f, 0.85f, 0.4f));
        drawTrail(sr, 8);
    }

    private void drawTrail(ShapeRenderer sr, float len) {
        if (target == null || target.dead) return;
        float tdx = target.x - x, tdy = (target.y + 15) - y;
        float d = (float) Math.sqrt(tdx * tdx + tdy * tdy);
        if (d > 0) {
            float trailX = x - (tdx / d) * len;
            float trailY = y - (tdy / d) * len;
            sr.rectLine(x, y, trailX, trailY, 1.5f);
        }
    }
}
