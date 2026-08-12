package games.pixscape.demo.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/** GestureDetector policy for Android hero movement, attack, and camera zoom. */
public final class TouchHeroInput extends GestureDetector.GestureAdapter implements HeroInput {
    private static final float TOUCH_MOVE_DEAD_ZONE_DP = 12f;
    private static final float TOUCH_MOVE_RADIUS_DP = 72f;
    private static final float CAMERA_ZOOM_MIN = 0.2f;
    private static final float CAMERA_ZOOM_MAX = 10f;

    private float density = 1f;
    private float originX;
    private float originY;
    private float movementX;
    private float movementY;
    private float userZoom = 1f;
    private float pinchStartUserZoom = 1f;
    private boolean pinching;
    private boolean attackPending;

    public TouchHeroInput() {
    }

    @Override
    public float moveX() {
        return movementX;
    }

    @Override
    public float moveY() {
        return movementY;
    }

    @Override
    public boolean attackJustPressed() {
        boolean pending = attackPending;
        attackPending = false;
        return pending;
    }

    public float userZoom() {
        return userZoom;
    }

    @Override
    public boolean touchDown(float x, float y, int pointer, int button) {
        if (pointer == 0) {
            density = currentDensity();
            originX = x;
            originY = y;
            resetMovement();
        } else {
            pinching = true;
            pinchStartUserZoom = userZoom;
            resetMovement();
        }
        return true;
    }

    @Override
    public boolean tap(float x, float y, int count, int button) {
        if (count != 2) return false;
        attackPending = true;
        return true;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        if (pinching) {
            resetMovement();
            return false;
        }

        updateMovement(x - originX, originY - y);
        return true;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, int button) {
        resetMovement();
        return true;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        pinching = true;
        resetMovement();
        if (initialDistance <= 0f || distance <= 0f) return false;

        userZoom = MathUtils.clamp(
            pinchStartUserZoom * initialDistance / distance,
            CAMERA_ZOOM_MIN,
            CAMERA_ZOOM_MAX
        );
        return true;
    }

    @Override
    public boolean pinch(
        Vector2 initialPointer1,
        Vector2 initialPointer2,
        Vector2 pointer1,
        Vector2 pointer2
    ) {
        pinching = true;
        resetMovement();
        return false;
    }

    @Override
    public void pinchStop() {
        pinching = false;
        resetMovement();
    }

    private void updateMovement(float dx, float dy) {
        float distance2 = dx * dx + dy * dy;
        float deadZone = TOUCH_MOVE_DEAD_ZONE_DP * density;
        if (distance2 <= deadZone * deadZone) {
            resetMovement();
            return;
        }

        float distance = (float) Math.sqrt(distance2);
        float radius = Math.max(deadZone + 1f, TOUCH_MOVE_RADIUS_DP * density);
        float strength = MathUtils.clamp((distance - deadZone) / (radius - deadZone), 0f, 1f);
        movementX = dx / distance * strength;
        movementY = dy / distance * strength;
    }

    private void resetMovement() {
        movementX = 0f;
        movementY = 0f;
    }

    private static float currentDensity() {
        return Gdx.graphics != null && Gdx.graphics.getDensity() > 0f
            ? Gdx.graphics.getDensity()
            : 1f;
    }
}
