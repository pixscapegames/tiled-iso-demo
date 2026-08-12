package games.pixscape.demo.camera;

import com.badlogic.gdx.math.Interpolation;

/** Small collection of temporary cinematic camera effects used by the demo. */
public final class CameraEffects {
    private static final float NORMAL_ZOOM = 1f;
    private static final float COMBAT_ZOOM = 0.60f;
    private static final float FOCUS_DURATION = 0.50f;
    private static final float RELEASE_DURATION = 0.90f;

    private float zoom = NORMAL_ZOOM;
    private float startZoom = NORMAL_ZOOM;
    private float targetZoom = NORMAL_ZOOM;
    private float transitionTime;
    private float transitionDuration;

    public void focusCombat() {
        transitionTo(COMBAT_ZOOM, FOCUS_DURATION);
    }

    public void releaseCombat() {
        transitionTo(NORMAL_ZOOM, RELEASE_DURATION);
    }

    public void update(float dt) {
        if (zoom == targetZoom) return;

        transitionTime = Math.min(
            transitionTime + Math.max(dt, 0f),
            transitionDuration
        );
        float progress = transitionDuration > 0f
            ? transitionTime / transitionDuration
            : 1f;

        zoom = Interpolation.smooth.apply(startZoom, targetZoom, progress);
        if (progress >= 1f) zoom = targetZoom;
    }

    public float zoomMultiplier() {
        return zoom;
    }

    public void reset() {
        zoom = NORMAL_ZOOM;
        startZoom = NORMAL_ZOOM;
        targetZoom = NORMAL_ZOOM;
        transitionTime = 0f;
        transitionDuration = 0f;
    }

    private void transitionTo(float target, float duration) {
        startZoom = zoom;
        targetZoom = target;
        transitionTime = 0f;
        transitionDuration = Math.max(duration, 0f);
        if (transitionDuration == 0f) zoom = targetZoom;
    }
}
