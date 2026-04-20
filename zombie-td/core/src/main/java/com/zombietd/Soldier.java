package com.zombietd;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class Soldier {
    public enum Type { SOLDIER, SNIPER, MG, ARCHER, MAGE }

    public float x, y;
    public int floor, slot;
    public Type type;
    public float hp, maxHp;
    public float damage;
    public float range;
    public float fireRate;      // shots per second
    public float cooldown = 0f;
    public boolean dead = false;
    public int sellValue;

    public Soldier(Type type, int floor, int slot, float x, float y) {
        this.type = type;
        this.floor = floor;
        this.slot = slot;
        this.x = x;
        this.y = y;

        switch (type) {
            case SOLDIER:
                maxHp = 100; damage = 12; range = 750; fireRate = 1.0f; sellValue = 25;
                break;
            case SNIPER:
                maxHp = 70;  damage = 30; range = 900; fireRate = 0.4f; sellValue = 40;
                break;
            case MG:
                maxHp = 130; damage = 7;  range = 700; fireRate = 3.5f; sellValue = 60;
                break;
            case ARCHER:
                maxHp = 85;  damage = 55; range = 850; fireRate = 0.7f; sellValue = 80;
                break;
            case MAGE:
                maxHp = 60;  damage = 95; range = 780; fireRate = 0.25f; sellValue = 110;
                break;
        }
        hp = maxHp;
        cooldown = 0f; // shoot immediately when a zombie enters range
    }

    public void update(float delta) {
        if (cooldown > 0) cooldown -= delta;
    }

    public boolean canShoot() {
        return cooldown <= 0;
    }

    public void shoot() {
        cooldown = 1f / fireRate;
    }

    public void draw(ShapeRenderer sr) {
        if (type == Type.SOLDIER) return; // drawn as animated sprite in GameScreen
        sr.setColor(getBodyColor());
        sr.rect(x - 9, y, 18, 22);

        // Head
        sr.setColor(new Color(0.78f, 0.66f, 0.51f, 1f));
        sr.circle(x, y + 30, 9);

        // Helmet
        sr.setColor(getHelmetColor());
        sr.rect(x - 9, y + 25, 18, 10);

        // Eyes
        sr.setColor(Color.BLACK);
        sr.circle(x - 3, y + 29, 2);
        sr.circle(x + 3, y + 29, 2);

        // Gun (pointing right)
        sr.setColor(Color.DARK_GRAY);
        sr.rect(x + 9, y + 12, 18, 4);

        // HP bar
        float hpPct = hp / maxHp;
        sr.setColor(Color.DARK_GRAY);
        sr.rect(x - 12, y - 6, 24, 3);
        sr.setColor(hpPct > 0.5f ? Color.GREEN : Color.YELLOW);
        sr.rect(x - 12, y - 6, 24 * hpPct, 3);
    }

    private Color getBodyColor() {
        switch (type) {
            case SOLDIER: return new Color(0.23f, 0.36f, 0.55f, 1f);
            case SNIPER:  return new Color(0.42f, 0.23f, 0.55f, 1f);
            case MG:      return new Color(0.55f, 0.42f, 0.23f, 1f);
            case ARCHER:  return new Color(0.20f, 0.55f, 0.25f, 1f);
            case MAGE:    return new Color(0.70f, 0.20f, 0.70f, 1f);
            default: return Color.BLUE;
        }
    }

    private Color getHelmetColor() {
        switch (type) {
            case SOLDIER: return new Color(0.17f, 0.30f, 0.17f, 1f);
            case SNIPER:  return new Color(0.25f, 0.15f, 0.35f, 1f);
            case MG:      return new Color(0.35f, 0.25f, 0.10f, 1f);
            case ARCHER:  return new Color(0.15f, 0.40f, 0.15f, 1f);
            case MAGE:    return new Color(0.50f, 0.10f, 0.50f, 1f);
            default: return Color.DARK_GRAY;
        }
    }

    public static int getCost(Type type) {
        switch (type) {
            case SOLDIER: return 50;
            case SNIPER:  return 80;
            case MG:      return 120;
            case ARCHER:  return 150;
            case MAGE:    return 200;
            default: return 50;
        }
    }

    public static String getName(Type type) {
        switch (type) {
            case SOLDIER: return "Soldier";
            case SNIPER:  return "Sniper";
            case MG:      return "MachineGun";
            case ARCHER:  return "Archer";
            case MAGE:    return "Mage";
            default: return "";
        }
    }

    public static String getLabel(Type type) {
        switch (type) {
            case SOLDIER: return "Солдат $50";
            case SNIPER:  return "Снайпер $80";
            case MG:      return "Пулемёт $120";
            case ARCHER:  return "Лучник $150";
            case MAGE:    return "Маг $200";
            default: return "";
        }
    }
}
