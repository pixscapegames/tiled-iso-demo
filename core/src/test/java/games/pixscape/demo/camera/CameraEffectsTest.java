package games.pixscape.demo.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CameraEffectsTest {
    @Test
    public void startsAtNormalZoom() {
        assertEquals(1f, new CameraEffects().zoomMultiplier(), 0f);
    }

    @Test
    public void focusMovesSmoothlyToCombatZoomWithoutOvershoot() {
        CameraEffects effects = new CameraEffects();
        effects.focusCombat();

        effects.update(0.25f);
        assertTrue(effects.zoomMultiplier() < 1f);
        assertTrue(effects.zoomMultiplier() >= 0.60f);

        effects.update(1f);
        assertEquals(0.60f, effects.zoomMultiplier(), 0.0001f);
    }

    @Test
    public void releaseReturnsSmoothlyToNormalZoom() {
        CameraEffects effects = new CameraEffects();
        effects.focusCombat();
        effects.update(1f);
        effects.releaseCombat();

        effects.update(0.45f);
        assertTrue(effects.zoomMultiplier() > 0.60f);
        assertTrue(effects.zoomMultiplier() <= 1f);

        effects.update(1f);
        assertEquals(1f, effects.zoomMultiplier(), 0.0001f);
    }

    @Test
    public void resetImmediatelyRestoresNormalZoom() {
        CameraEffects effects = new CameraEffects();
        effects.focusCombat();
        effects.update(0.25f);

        effects.reset();

        assertEquals(1f, effects.zoomMultiplier(), 0f);
    }
}
