package games.pixscape.demo.input;

import com.badlogic.gdx.math.Vector2;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TouchHeroInputTest {
    @Test
    public void deadZoneSuppressesSmallPanAndConvertsScreenY() {
        TouchHeroInput input = new TouchHeroInput();
        input.touchDown(100f, 100f, 0, 0);

        input.pan(108f, 106f, 8f, 6f);
        assertEquals(0f, input.moveX(), 0f);
        assertEquals(0f, input.moveY(), 0f);

        input.pan(140f, 70f, 32f, -36f);
        assertTrue(input.moveX() > 0f);
        assertTrue(input.moveY() > 0f);
        assertTrue(input.moveX() <= 1f);
        assertTrue(input.moveY() <= 1f);
    }

    @Test
    public void panStopImmediatelyResetsMovement() {
        TouchHeroInput input = new TouchHeroInput();
        input.touchDown(0f, 0f, 0, 0);
        input.pan(80f, 0f, 80f, 0f);
        assertTrue(input.moveX() > 0f);

        input.panStop(80f, 0f, 0, 0);
        assertEquals(0f, input.moveX(), 0f);
        assertEquals(0f, input.moveY(), 0f);
    }

    @Test
    public void onlyGestureDetectorDoubleTapRequestsAttack() {
        TouchHeroInput input = new TouchHeroInput();

        assertFalse(input.tap(10f, 10f, 1, 0));
        assertFalse(input.attackJustPressed());
        assertTrue(input.tap(10f, 10f, 2, 0));
        assertTrue(input.attackJustPressed());
        assertFalse(input.attackJustPressed());
    }

    @Test
    public void panCallbackNeverCreatesAnAttackRequest() {
        TouchHeroInput input = new TouchHeroInput();
        input.touchDown(0f, 0f, 0, 0);
        input.pan(80f, 0f, 80f, 0f);
        input.panStop(80f, 0f, 0, 0);

        assertFalse(input.attackJustPressed());
    }

    @Test
    public void pinchCancelsHeroMovement() {
        TouchHeroInput input = new TouchHeroInput();
        input.touchDown(0f, 0f, 0, 0);
        input.pan(80f, 0f, 80f, 0f);
        assertTrue(input.moveX() > 0f);

        input.touchDown(100f, 0f, 1, 0);
        input.pinch(
            new Vector2(0f, 0f),
            new Vector2(100f, 0f),
            new Vector2(0f, 0f),
            new Vector2(200f, 0f)
        );

        assertEquals(0f, input.moveX(), 0f);
        assertEquals(0f, input.moveY(), 0f);
        input.pinchStop();
    }

    @Test
    public void zoomCallbackUpdatesUserZoomWithinLimits() {
        TouchHeroInput input = new TouchHeroInput();

        input.touchDown(0f, 0f, 0, 0);
        input.touchDown(100f, 0f, 1, 0);
        assertTrue(input.zoom(100f, 200f));
        assertEquals(0.5f, input.userZoom(), 0.001f);

        input.zoom(100f, 10000f);
        assertEquals(0.2f, input.userZoom(), 0f);
        input.pinchStop();
    }
}
