package com.zombietd;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class FloatText {
    public float x, y;
    public String text;
    public Color color;
    public float life = 1.2f;
    public boolean dead = false;

    public FloatText(String text, float x, float y, Color color) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color.cpy();
    }

    public void update(float delta) {
        y += 35f * delta;
        life -= delta;
        if (life <= 0) dead = true;
    }

    public void draw(SpriteBatch batch, BitmapFont font) {
        font.setColor(color.r, color.g, color.b, Math.min(life, 0.8f));
        font.draw(batch, text, x, y);
        font.setColor(Color.WHITE);
    }
}
