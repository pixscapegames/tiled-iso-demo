package games.pixscape.demo;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MainInputRoutingTest {
    @Test
    public void desktopUsesTheHudProcessorDirectly() {
        InputProcessor hud = new InputAdapter();
        assertSame(hud, Main.routeInput(hud, null));
    }

    @Test
    public void hudGesturesStayWithHudAndOtherGesturesReachTheWorld() {
        int[] hudDown = {0};
        int[] hudUp = {0};
        int[] worldDown = {0};
        int[] worldUp = {0};
        boolean[] hudOwnsPointer = {false};
        InputProcessor hud = new InputAdapter() {
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                if (x >= 100) return false;
                hudDown[0]++;
                hudOwnsPointer[0] = true;
                return true;
            }

            @Override public boolean touchDragged(int x, int y, int pointer) {
                return hudOwnsPointer[0];
            }

            @Override public boolean touchUp(int x, int y, int pointer, int button) {
                if (!hudOwnsPointer[0]) return false;
                hudOwnsPointer[0] = false;
                hudUp[0]++;
                return true;
            }
        };
        InputProcessor world = new InputAdapter() {
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                worldDown[0]++;
                return true;
            }

            @Override public boolean touchUp(int x, int y, int pointer, int button) {
                worldUp[0]++;
                return true;
            }
        };
        InputProcessor routed = Main.routeInput(hud, world);

        assertTrue(routed.touchDown(20, 20, 0, 0));
        assertTrue(routed.touchDragged(200, 200, 0));
        assertTrue(routed.touchUp(200, 200, 0, 0));
        assertEquals(1, hudDown[0]);
        assertEquals(1, hudUp[0]);
        assertEquals(0, worldDown[0]);
        assertEquals(0, worldUp[0]);

        assertTrue(routed.touchDown(200, 200, 0, 0));
        assertTrue(routed.touchUp(20, 20, 0, 0));
        assertEquals(1, worldDown[0]);
        assertEquals(1, worldUp[0]);
        assertFalse(hudOwnsPointer[0]);
    }

}
