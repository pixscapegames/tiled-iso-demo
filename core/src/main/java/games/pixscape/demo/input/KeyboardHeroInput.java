package games.pixscape.demo.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/** Desktop and HTML keyboard controls. */
public final class KeyboardHeroInput implements HeroInput {
    @Override
    public float moveX() {
        float value = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) value -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) value += 1f;
        return value;
    }

    @Override
    public float moveY() {
        float value = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) value += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) value -= 1f;
        return value;
    }

    @Override
    public boolean attackJustPressed() {
        return Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
    }
}
