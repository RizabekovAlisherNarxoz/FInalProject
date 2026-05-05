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

public class LevelSelectScreen implements Screen {

    static final int W = 960, H = 600;

    private static final Color BTN_BG   = new Color(0.031f, 0.016f, 0.078f, 0.88f);
    private static final Color BTN_BDR  = new Color(0.784f, 0.439f, 0.188f, 0.5f);
    private static final Color BTN_HOV  = new Color(0xf0a030FF);
    private static final Color BTN_TEXT = new Color(0.941f, 0.784f, 0.549f, 0.9f);
    private static final Color SCAN     = new Color(0f, 0f, 0f, 0.07f);

    private static final float[][] SPARK_RGBA = {
        {0.627f, 0.941f, 1.00f},
        {0.392f, 0.784f, 1.00f},
        {0.784f, 0.941f, 1.00f},
        {0.314f, 0.706f, 0.941f},
        {0.863f, 0.980f, 1.00f},
    };

    private static final String[] LEVEL_NAMES = {
        "LEVEL 1", "LEVEL 2"
    };

    // Maps button index → actual level number passed to GameScreen
    private static final int[] LEVEL_IDS = { 1, 3 };

    private static final int NUM_LEVELS  = 2;
    private static final int SPARK_COUNT = 90;

    private final ZombieTD game;

    private ShapeRenderer sr;
    private SpriteBatch   batch;
    private BitmapFont    fontTitle, fontBtn, fontSmall;
    private GlyphLayout   layout;
    private Texture       bgTex;

    private float   time   = 0f;
    private float   scaleX = 1f, scaleY = 1f;

    // Sparkles
    private float[]   spX, spY, spSize, spFreq, spPhase, spDrift;
    private int[]     spColor;
    private boolean[] spCross;

    // Buttons
    private final Rectangle[] btnLevel = new Rectangle[NUM_LEVELS];
    private final boolean[]   hoverLvl = new boolean[NUM_LEVELS];
    private final Rectangle   btnBack  = new Rectangle();
    private boolean hoverBack;

    public LevelSelectScreen(ZombieTD game) {
        this.game = game;
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────

    @Override
    public void show() {
        sr     = new ShapeRenderer();
        batch  = new SpriteBatch();
        layout = new GlyphLayout();

        bgTex = new Texture(Gdx.files.internal("menu_bg.jpg"));
        bgTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(
            Gdx.files.internal("PressStart2P.ttf"));
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.magFilter = Texture.TextureFilter.Nearest;
        p.minFilter = Texture.TextureFilter.Nearest;

        p.size = 22; p.color = Color.WHITE;
        fontTitle = gen.generateFont(p);

        p.size = 10; p.color = new Color(0xf0c88cFF);
        fontBtn = gen.generateFont(p);

        p.size = 7; p.color = new Color(0xc89660FF);
        fontSmall = gen.generateFont(p);

        gen.dispose();

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

        // Button layout — centred column
        float btnW = 300f, btnH = 50f;
        float cx   = (W - btnW) / 2f;
        for (int i = 0; i < NUM_LEVELS; i++) {
            btnLevel[i] = new Rectangle(cx, 400f - i * 65f, btnW, btnH);
        }
        btnBack.set(cx, 400f - NUM_LEVELS * 65f - 8f, btnW, btnH);

        Gdx.input.setInputProcessor(null);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void render(float delta) {
        time += delta;

        float mx = Gdx.input.getX() * scaleX;
        float my = H - Gdx.input.getY() * scaleY;

        for (int i = 0; i < NUM_LEVELS; i++) hoverLvl[i]  = btnLevel[i].contains(mx, my);
        hoverBack = btnBack.contains(mx, my);

        if (Gdx.input.justTouched()) {
            for (int i = 0; i < NUM_LEVELS; i++) {
                if (hoverLvl[i]) { startLevel(LEVEL_IDS[i]); return; }
            }
            if (hoverBack) { goBack(); return; }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { goBack(); return; }

        Gdx.gl.glClearColor(0.02f, 0.008f, 0.059f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        drawBackground();
        drawScanlines();
        drawSparkles();
        drawUI();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private void drawBackground() {
        batch.begin();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(bgTex, 0, 0, W, H);
        batch.end();

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
        for (int y = 0; y < H; y += 4) sr.rect(0, y, W, 1);
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
            float alpha = 0.15f + 0.7f * Math.abs(MathUtils.sin(phase));
            float sz    = spSize[i] * (0.7f + 0.6f * Math.abs(MathUtils.sin(phase * 0.7f)));
            float px    = spX[i] * W, py = spY[i] * H;
            float[] c   = SPARK_RGBA[spColor[i]];
            if (spCross[i]) {
                sr.setColor(c[0], c[1], c[2], alpha);
                sr.rect(px - sz * 2f, py - sz * 0.3f, sz * 4f, sz * 0.6f);
                sr.rect(px - sz * 0.3f, py - sz * 2f, sz * 0.6f, sz * 4f);
            } else {
                sr.setColor(c[0], c[1], c[2], alpha * 0.65f);
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

        // Button backgrounds (ShapeRenderer)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < NUM_LEVELS; i++) drawBtn(btnLevel[i], hoverLvl[i]);
        drawBtn(btnBack, hoverBack);
        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Text (SpriteBatch)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        batch.setColor(1f, 1f, 1f, 1f);

        // Title
        layout.setText(fontTitle, "SELECT LEVEL");
        float tx = cx - layout.width / 2f;
        // shadow
        fontTitle.setColor(0.478f, 0.125f, 0.376f, 0.7f);
        fontTitle.draw(batch, "SELECT LEVEL", tx + 2, 548f - 2);
        // main
        fontTitle.setColor(Color.WHITE);
        fontTitle.draw(batch, "SELECT LEVEL", tx, 548f);

        // Sub line
        fontSmall.setColor(0.910f, 0.627f, 0.376f, 0.35f + 0.35f * MathUtils.sin(time * 2.6f));
        drawCentered(fontSmall, "CHOOSE YOUR MISSION", cx, 516f);

        // Level button labels
        for (int i = 0; i < NUM_LEVELS; i++) drawBtnLabel(LEVEL_NAMES[i], btnLevel[i], hoverLvl[i]);

        // Back button label
        drawBtnLabel("< BACK TO MENU", btnBack, hoverBack);

        // ESC hint
        float hintA = 0.28f + 0.22f * MathUtils.sin(time * 3f);
        fontSmall.setColor(0.784f, 0.588f, 0.353f, hintA);
        drawCentered(fontSmall, "PRESS ESC TO RETURN", cx, 30f);

        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawBtn(Rectangle btn, boolean hover) {
        sr.setColor(BTN_BG.r, BTN_BG.g, BTN_BG.b, 0.88f);
        sr.rect(btn.x, btn.y, btn.width, btn.height);
        Color bdr = hover ? BTN_HOV : BTN_BDR;
        sr.setColor(bdr);
        sr.rect(btn.x,                   btn.y,                   btn.width, 2);
        sr.rect(btn.x,                   btn.y + btn.height - 2,  btn.width, 2);
        sr.rect(btn.x,                   btn.y,                   2, btn.height);
        sr.rect(btn.x + btn.width - 2,   btn.y,                   2, btn.height);
        if (hover) {
            sr.setColor(BTN_HOV);
            float ax = btn.x + 16f, ay = btn.y + btn.height / 2f;
            sr.triangle(ax, ay + 6f, ax, ay - 6f, ax + 9f, ay);
        }
    }

    private void drawBtnLabel(String text, Rectangle btn, boolean hover) {
        fontBtn.setColor(hover ? BTN_HOV : BTN_TEXT);
        float tx = btn.x + (hover ? 32f : 22f);
        float ty = btn.y + btn.height * 0.67f;
        fontBtn.draw(batch, text, tx, ty);
    }

    private void drawCentered(BitmapFont font, String text, float cx, float y) {
        layout.setText(font, text);
        font.draw(batch, text, cx - layout.width / 2f, y);
    }

    private void startLevel(int level) {
        game.setScreen(new GameScreen(game, level));
    }

    private void goBack() {
        game.setScreen(new MenuScreen(game));
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
    @Override public void hide()   { dispose(); }

    @Override
    public void dispose() {
        sr.dispose();
        batch.dispose();
        bgTex.dispose();
        fontTitle.dispose();
        fontBtn.dispose();
        fontSmall.dispose();
    }
}
