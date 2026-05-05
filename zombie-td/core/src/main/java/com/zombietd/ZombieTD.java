package com.zombietd;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class ZombieTD extends Game {
    public SpriteBatch batch;
    Music currentMusic;

    @Override
    public void create() {
        batch = new SpriteBatch();
        setScreen(new MenuScreen(this));
    }

    public void playMusic(String filename) {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.dispose();
        }
        currentMusic = Gdx.audio.newMusic(Gdx.files.internal(filename));
        currentMusic.setLooping(true);
        currentMusic.play();
    }

    @Override
    public void dispose() {
        batch.dispose();
        if (currentMusic != null) currentMusic.dispose();
    }
}
