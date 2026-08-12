package games.pixscape.demo.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/** Keyboard keys consumed by the demo rather than the hosting platform. */
public final class DemoInputKeys {
    private static final int[] HANDLED_KEYS = {
            Input.Keys.LEFT,
            Input.Keys.RIGHT,
            Input.Keys.UP,
            Input.Keys.DOWN
    };

    private DemoInputKeys() {
    }

    public static void catchHandledKeys() {
        for (int key : HANDLED_KEYS) {
            Gdx.input.setCatchKey(key, true);
        }
    }

    public static boolean isHandled(int keycode) {
        for (int key : HANDLED_KEYS) {
            if (keycode == key) {
                return true;
            }
        }
        return false;
    }
}
