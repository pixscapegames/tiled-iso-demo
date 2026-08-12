package games.pixscape.demo.input;

import com.badlogic.gdx.Input;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DemoInputKeysTest {
    @Test
    public void reportsDemoKeysAsHandled() {
        assertFalse(DemoInputKeys.isHandled(Input.Keys.SPACE));
        assertTrue(DemoInputKeys.isHandled(Input.Keys.LEFT));
        assertFalse(DemoInputKeys.isHandled(Input.Keys.PLUS));
        assertFalse(DemoInputKeys.isHandled(Input.Keys.NUM_7));
        assertFalse(DemoInputKeys.isHandled(Input.Keys.NUMPAD_3));
    }

    @Test
    public void reportsOtherKeysAsUnhandled() {
        assertFalse(DemoInputKeys.isHandled(Input.Keys.A));
        assertFalse(DemoInputKeys.isHandled(Input.Keys.ESCAPE));
    }
}
