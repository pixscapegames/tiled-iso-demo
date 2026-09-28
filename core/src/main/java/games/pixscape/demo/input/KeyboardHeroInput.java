package games.pixscape.demo.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import java.util.function.BooleanSupplier;

/** Desktop and HTML keyboard controls. */
public final class KeyboardHeroInput implements HeroInput {
    private final BooleanSupplier hudHasKeyboardFocus;

    public KeyboardHeroInput() {
        this(() -> false);
    }

    public KeyboardHeroInput(BooleanSupplier hudHasKeyboardFocus) {
        if (hudHasKeyboardFocus == null) {
            throw new IllegalArgumentException("HUD focus supplier is required.");
        }
        this.hudHasKeyboardFocus = hudHasKeyboardFocus;
    }

    @Override
    public float moveX() {
        if (hudHasKeyboardFocus.getAsBoolean()) return 0f;
        float value = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) value -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) value += 1f;
        return value;
    }

    @Override
    public float moveY() {
        if (hudHasKeyboardFocus.getAsBoolean()) return 0f;
        float value = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) value += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) value -= 1f;
        return value;
    }

    @Override
    public boolean attackJustPressed() {
        if (hudHasKeyboardFocus.getAsBoolean()) return false;
        return Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
    }
}
