package com.zombietd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

public class MenuScreen implements Screen {

    static final int W = 960, H = 600;

    // ── Color palette (matching heroes-path-menu HTML) ────────────────────────
    private static final Color ACCENT    = new Color(0xe8a040FF);
    private static final Color SUBTITLE  = new Color(0xf0d090FF);
    private static final Color BTN_BG    = new Color(0.031f, 0.016f, 0.078f, 0.88f);
    private static final Color BTN_BDR   = new Color(0.784f, 0.439f, 0.188f, 0.5f);
    private static final Color BTN_HOV   = new Color(0xf0a030FF);
    private static final Color BTN_TEXT  = new Color(0.941f, 0.784f, 0.549f, 0.9f);
    private static final Color SCAN      = new Color(0f, 0f, 0f, 0.07f);

    // Sparkle colours (blue/cyan matching HTML)
    private static final float[][] SPARK_RGBA = {
        {0.627f, 0.941f, 1.00f},
        {0.392f, 0.784f, 1.00f},
        {0.784f, 0.941f, 1.00f},
        {0.314f, 0.706f, 0.941f},
        {0.863f, 0.980f, 1.00f},
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private final ZombieTD game;

    private ShapeRenderer sr;
    private SpriteBatch   batch;
    private BitmapFont    fontTitle, fontSub, fontBtn, fontSmall, fontTeam;
    private GlyphLayout   layout;
    private Texture       bgTex;

    private float time = 0f;
    private float scaleX = 1f, scaleY = 1f;

    // Sparkles
    private static final int SPARK_COUNT = 90;
    private float[] spX, spY, spSize, spFreq, spPhase, spDrift;
    private int[]   spColor;
    private boolean[] spCross;

    // Buttons
    private final Rectangle btnPlay = new Rectangle();
    private final Rectangle btnQuit = new Rectangle();
    private boolean hoverPlay, hoverQuit;

    // ── Constructor ───────────────────────────────────────────────────────────
    public MenuScreen(ZombieTD game) {
        this.game = game;
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────
    @Override
    public void show() {
        sr    = new ShapeRenderer();
        batch = new SpriteBatch();
        layout = new GlyphLayout();

        game.playMusic("menu.mp3");

        // Background texture (extracted from heroes-path-menu HTML)
        bgTex = new Texture(Gdx.files.internal("menu_bg.jpg"));
        bgTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        // Press Start 2P — identical font to the HTML menu
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(
            Gdx.files.internal("PressStart2P.ttf"));

        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.magFilter = Texture.TextureFilter.Nearest;
        p.minFilter = Texture.TextureFilter.Nearest;

        p.size = 44;
        p.color = Color.WHITE;
        fontTitle = gen.generateFont(p);

        p.size = 10;
        p.color = new Color(0xf0d090FF);
        fontSub = gen.generateFont(p);

        p.size = 8;
        p.color = new Color(0xe8a040FF);
        fontTeam = gen.generateFont(p);

        p.size = 10;
        p.color = new Color(0xf0c88cFF);
        fontBtn = gen.generateFont(p);

        p.size = 7;
        p.color = new Color(0xc89660FF);
        fontSmall = gen.generateFont(p);

        gen.dispose(); // TTF data no longer needed after generation

        // Init sparkles
        spX     = new float[SPARK_COUNT];
        spY     = new float[SPARK_COUNT];
        spSize  = new float[SPARK_COUNT];
        spFreq  = new float[SPARK_COUNT];
        spPhase = new float[SPARK_COUNT];
        spDrift = new float[SPARK_COUNT];
        spColor = new int[SPARK_COUNT];
        spCross = new boolean[SPARK_COUNT];
        for (int i = 0; i < SPARK_COUNT; i++) {
            spX[i]     = MathUtils.random();
            spY[i]     = MathUtils.random();
            spSize[i]  = 0.5f + MathUtils.random(2.2f);
            spFreq[i]  = 0.02f + MathUtils.random(0.04f);
            spPhase[i] = MathUtils.random(MathUtils.PI2);
            spDrift[i] = (MathUtils.random() - 0.5f) * 0.00015f;
            spColor[i] = MathUtils.random(SPARK_RGBA.length - 1);
            spCross[i] = MathUtils.random() < 0.3f;
        }

        // Button layout — centred
        float btnW = 270f, btnH = 52f, cx = (W - btnW) / 2f;
        btnPlay.set(cx, 248f, btnW, btnH);
        btnQuit.set(cx, 178f, btnW, btnH);

        Gdx.input.setInputProcessor(null);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void render(float delta) {
        time += delta;

        float mx = Gdx.input.getX() * scaleX;
        float my = H - Gdx.input.getY() * scaleY;

        hoverPlay = btnPlay.contains(mx, my);
        hoverQuit = btnQuit.contains(mx, my);

        // Input
        if (Gdx.input.justTouched()) {
            if (hoverPlay) { launchGame(); return; }
            if (hoverQuit) { Gdx.app.exit(); return; }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) ||
            Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            launchGame();
            return;
        }

        Gdx.gl.glClearColor(0.02f, 0.008f, 0.059f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        drawBackground();
        drawScanlines();
        drawSparkles();
        drawUI();
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void drawBackground() {
        // 1. Real background photo (fills entire screen)
        batch.begin();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(bgTex, 0, 0, W, H);
        batch.end(); // SpriteBatch.end() disables GL blend — re-enable for overlay

        // 2. Dark overlay — rgba(8,4,18, 0.52) matching HTML .overlay
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.031f, 0.016f, 0.071f, 0.54f);
        sr.rect(0, 0, W, H);
        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawScanlines() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(SCAN);
        for (int y = 0; y < H; y += 4) {
            sr.rect(0, y, W, 1);
        }
        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawSparkles() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        for (int i = 0; i < SPARK_COUNT; i++) {
            spX[i] += spDrift[i];
            if (spX[i] < 0) spX[i] = 1f;
            if (spX[i] > 1f) spX[i] = 0f;

            float phase = spPhase[i] + time * spFreq[i] * 60f;
            float alpha = 0.15f + 0.7f  * Math.abs(MathUtils.sin(phase));
            float sz    = spSize[i] * (0.7f + 0.6f * Math.abs(MathUtils.sin(phase * 0.7f)));

            float px = spX[i] * W;
            float py = spY[i] * H;
            float[] c = SPARK_RGBA[spColor[i]];

            if (spCross[i]) {
                sr.setColor(c[0], c[1], c[2], alpha);
                sr.rect(px - sz * 2f, py - sz * 0.3f, sz * 4f, sz * 0.6f);
                sr.rect(px - sz * 0.3f, py - sz * 2f, sz * 0.6f, sz * 4f);
            } else {
                sr.setColor(c[0], c[1], c[2], alpha * 0.65f);
                // draw as small filled circle approximation (8-segment poly)
                float r = sz * 2.5f;
                for (int seg = 0; seg < 8; seg++) {
                    float a1 = seg * MathUtils.PI2 / 8f;
                    float a2 = (seg + 1) * MathUtils.PI2 / 8f;
                    sr.triangle(px, py,
                        px + r * MathUtils.cos(a1), py + r * MathUtils.sin(a1),
                        px + r * MathUtils.cos(a2), py + r * MathUtils.sin(a2));
                }
            }
        }

        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawUI() {
        float cx = W / 2f;

        // ── Buttons (background + border) ────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        drawButton(btnPlay, hoverPlay);
        drawButton(btnQuit, hoverQuit);

        // Divider line (gradient, fades at edges) — matches HTML .divider
        float divY = 318f;
        for (int x = 0; x < 300; x++) {
            float t = x / 300f;
            float a = t < 0.25f ? t / 0.25f : t > 0.75f ? (1f - t) / 0.25f : 1f;
            sr.setColor(0.784f, 0.439f, 0.251f, a * 0.95f);
            sr.rect(cx - 150f + x, divY, 1, 2);
        }

        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── Text ─────────────────────────────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();

        // Team label — "✦ PIXELS STUDIO ✦" with pulse (HTML: .team class)
        float teamAlpha = 0.35f + 0.35f * MathUtils.sin(time * 2.6f);
        fontTeam.setColor(0.910f, 0.627f, 0.376f, teamAlpha);
        drawCentered(fontTeam, "PIXELS STUDIO", cx, 510f);

        // Subtitle "HERO'S PATH" (HTML: .gtitle-sub)
        fontSub.setColor(0.941f, 0.816f, 0.565f, 0.9f);
        drawCentered(fontSub, "HERO'S PATH", cx, 485f);

        // Main title "DEFENSE" with drop-shadow glow (HTML: .gtitle)
        layout.setText(fontTitle, "DEFENSE");
        float tx = cx - layout.width / 2f;
        float ty = 450f;
        // Shadow layers (mimics: text-shadow 3px 3px 0 #7a2060, 6px 6px 0 #3d1030)
        fontTitle.setColor(0.478f, 0.125f, 0.376f, 0.7f);
        fontTitle.draw(batch, "DEFENSE", tx + 3, ty - 3);
        fontTitle.setColor(0.239f, 0.063f, 0.188f, 0.45f);
        fontTitle.draw(batch, "DEFENSE", tx + 6, ty - 6);
        // Outer glow
        fontTitle.setColor(1f, 0.706f, 0.471f, 0.18f);
        fontTitle.draw(batch, "DEFENSE", tx - 2, ty + 2);
        fontTitle.draw(batch, "DEFENSE", tx + 2, ty + 2);
        // Main text
        fontTitle.setColor(Color.WHITE);
        fontTitle.draw(batch, "DEFENSE", tx, ty);

        // Button labels
        drawBtnLabel("LEVELS", btnPlay, hoverPlay);
        drawBtnLabel("QUIT",     btnQuit, hoverQuit);

        // Hint — pulsing (HTML: .hint)
        float hintA = 0.28f + 0.22f * MathUtils.sin(time * 3f);
        fontSmall.setColor(0.784f, 0.588f, 0.353f, hintA);
        drawCentered(fontSmall, "PRESS ENTER OR CLICK TO START", cx, 145f);

        // Version
        fontSmall.setColor(1f, 1f, 1f, 0.15f);
        fontSmall.draw(batch, "v0.1", W - 60f, 22f);

        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawButton(Rectangle btn, boolean hover) {
        // fill
        sr.setColor(BTN_BG.r, BTN_BG.g, BTN_BG.b, 0.88f);
        sr.rect(btn.x, btn.y, btn.width, btn.height);
        // border
        Color bdr = hover ? BTN_HOV : BTN_BDR;
        sr.setColor(bdr);
        sr.rect(btn.x,                     btn.y,                      btn.width, 2);
        sr.rect(btn.x,                     btn.y + btn.height - 2,     btn.width, 2);
        sr.rect(btn.x,                     btn.y,                      2, btn.height);
        sr.rect(btn.x + btn.width - 2,     btn.y,                      2, btn.height);
        // hover arrow
        if (hover) {
            sr.setColor(BTN_HOV);
            float ax = btn.x + 16f, ay = btn.y + btn.height / 2f;
            sr.triangle(ax, ay + 6f, ax, ay - 6f, ax + 9f, ay);
        }
    }

    private void drawBtnLabel(String text, Rectangle btn, boolean hover) {
        fontBtn.setColor(hover ? BTN_HOV : BTN_TEXT);
        layout.setText(fontBtn, text);
        float tx = btn.x + (hover ? 32f : 22f);
        float ty = btn.y + btn.height * 0.67f;
        fontBtn.draw(batch, text, tx, ty);
    }

    private void drawCentered(BitmapFont font, String text, float cx, float y) {
        layout.setText(font, text);
        font.draw(batch, text, cx - layout.width / 2f, y);
    }

    private void launchGame() {
        game.setScreen(new LevelSelectScreen(game));
    }

    // ── Screen interface ──────────────────────────────────────────────────────
    @Override
    public void resize(int width, int height) {
        scaleX = (float) W / width;
        scaleY = (float) H / height;
        sr.getProjectionMatrix().setToOrtho2D(0, 0, W, H);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, W, H);
    }

    @Override public void pause()  {}
    @Override public void resume() {}

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        sr.dispose();
        batch.dispose();
        bgTex.dispose();
        fontTitle.dispose();
        fontSub.dispose();
        fontTeam.dispose();
        fontBtn.dispose();
        fontSmall.dispose();
    }
}
