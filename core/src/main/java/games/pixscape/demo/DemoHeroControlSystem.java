package games.pixscape.demo;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.Box2dSyncSystem;

final class DemoHeroControlSystem extends IteratingSystem {
    private static final String HERO_NAME = "hero";
    private static final float CAMERA_DT_MAX = 1f / 30f;
    private static final float MOVE_ACCELERATION_PIXELS = 450f;
    private static final float MAX_SPEED_PIXELS = 300f;
    private static final float IDLE_DAMPING_PER_SECOND = 8f;
    private static final float ANIMATION_PIXELS_PER_FRAME = 15f;
    private static final float ANIMATION_STOP_SPEED_PIXELS = 5f;
    private static final String[] RUN_CLIPS = {
            "run_000",
            "run_045",
            "run_090",
            "run_135",
            "run_180",
            "run_225",
            "run_270",
            "run_315"
    };
    private static final int[] DIRECTION_KEYS = {
            Input.Keys.NUM_1,
            Input.Keys.NUM_2,
            Input.Keys.NUM_3,
            Input.Keys.NUM_4,
            Input.Keys.NUM_6,
            Input.Keys.NUM_7,
            Input.Keys.NUM_8,
            Input.Keys.NUM_9,
            Input.Keys.NUMPAD_1,
            Input.Keys.NUMPAD_2,
            Input.Keys.NUMPAD_3,
            Input.Keys.NUMPAD_4,
            Input.Keys.NUMPAD_6,
            Input.Keys.NUMPAD_7,
            Input.Keys.NUMPAD_8,
            Input.Keys.NUMPAD_9
    };

    private final OrthographicCamera camera;

    private ComponentMapper<PixscapeIdentityComponent> identities;
    private ComponentMapper<PhysicsRuntimeBodyComponent> runtimeBodies;
    private ComponentMapper<AnimationComponent> animations;
    private Box2dSyncSystem box2dSync;

    DemoHeroControlSystem(OrthographicCamera camera) {
        super(Aspect.all(
                PixscapeIdentityComponent.class,
                PhysicsRuntimeBodyComponent.class,
                AnimationComponent.class
        ));
        this.camera = camera;
    }

    static void catchDirectionKeys() {
        for (int key : DIRECTION_KEYS) {
            Gdx.input.setCatchKey(key, true);
        }
    }

    static boolean isDirectionKey(int keycode) {
        for (int directionKey : DIRECTION_KEYS) {
            if (keycode == directionKey) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void initialize() {
        box2dSync = world.getSystem(Box2dSyncSystem.class);
    }

    @Override
    protected void process(int entityId) {
        PixscapeIdentityComponent identity = identities.get(entityId);
        if (identity == null || !HERO_NAME.equals(identity.name)) return;

        Box2dWorldService box2d = currentBox2d();
        if (box2d == null) return;

        PhysicsRuntimeBodyComponent runtimeBody = runtimeBodies.get(entityId);
        if (runtimeBody == null || runtimeBody.body == null) return;

        handleControls(runtimeBody, animations.get(entityId), box2d, world.getDelta());
        followCamera(runtimeBody, box2d);
    }

    private Box2dWorldService currentBox2d() {
        if (box2dSync == null || !box2dSync.isEnabled()) return null;
        Box2dWorldService box2d = box2dSync.getBox2d();
        if (box2d == null || box2d.world == null || box2d.isDisposed()) return null;
        return box2d;
    }

    private void handleControls(PhysicsRuntimeBodyComponent runtimeBody,
                                AnimationComponent animation,
                                Box2dWorldService box2d,
                                float dt) {
        if (runtimeBody.body.getType() != BodyDef.BodyType.DynamicBody) return;

        float dx = inputX();
        float dy = inputY();
        boolean hasInput = dx != 0f || dy != 0f;

        if (hasInput) {
            float invLen = 1f / (float) Math.sqrt(dx * dx + dy * dy);
            dx *= invLen;
            dy *= invLen;
            setAnimationClip(animation, runClipForDirection(dx, dy));
        } else {
            dampVelocity(runtimeBody, dt);
        }

        updateAnimationSpeed(runtimeBody, animation, box2d, dt);
        if (!hasInput) return;

        clampVelocity(runtimeBody, box2d);

        float force = runtimeBody.body.getMass() * box2d.pxToM(MOVE_ACCELERATION_PIXELS);
        runtimeBody.body.applyForceToCenter(dx * force, dy * force, true);
    }

    private static float inputX() {
        float dx = 0f;
        if (isAnyKeyPressed(Input.Keys.NUM_7, Input.Keys.NUMPAD_7)
                || isAnyKeyPressed(Input.Keys.NUM_4, Input.Keys.NUMPAD_4)
                || isAnyKeyPressed(Input.Keys.NUM_1, Input.Keys.NUMPAD_1)) {
            dx -= 1f;
        }
        if (isAnyKeyPressed(Input.Keys.NUM_9, Input.Keys.NUMPAD_9)
                || isAnyKeyPressed(Input.Keys.NUM_6, Input.Keys.NUMPAD_6)
                || isAnyKeyPressed(Input.Keys.NUM_3, Input.Keys.NUMPAD_3)) {
            dx += 1f;
        }
        return dx;
    }

    private static float inputY() {
        float dy = 0f;
        if (isAnyKeyPressed(Input.Keys.NUM_7, Input.Keys.NUMPAD_7)
                || isAnyKeyPressed(Input.Keys.NUM_8, Input.Keys.NUMPAD_8)
                || isAnyKeyPressed(Input.Keys.NUM_9, Input.Keys.NUMPAD_9)) {
            dy += 1f;
        }
        if (isAnyKeyPressed(Input.Keys.NUM_1, Input.Keys.NUMPAD_1)
                || isAnyKeyPressed(Input.Keys.NUM_2, Input.Keys.NUMPAD_2)
                || isAnyKeyPressed(Input.Keys.NUM_3, Input.Keys.NUMPAD_3)) {
            dy -= 1f;
        }
        return dy;
    }

    private static boolean isAnyKeyPressed(int keycode, int numpadKeycode) {
        return Gdx.input.isKeyPressed(keycode) || Gdx.input.isKeyPressed(numpadKeycode);
    }

    private static void dampVelocity(PhysicsRuntimeBodyComponent runtimeBody, float dt) {
        float safeDt = Math.min(dt, CAMERA_DT_MAX);
        Vector2 velocity = runtimeBody.body.getLinearVelocity();
        float damping = (float) Math.exp(-IDLE_DAMPING_PER_SECOND * safeDt);
        velocity.scl(damping);

        if (velocity.len2() < 0.0001f) {
            velocity.setZero();
        }

        runtimeBody.body.setLinearVelocity(velocity);
    }

    private static void clampVelocity(PhysicsRuntimeBodyComponent runtimeBody, Box2dWorldService box2d) {
        Vector2 velocity = runtimeBody.body.getLinearVelocity();
        float maxSpeed = box2d.pxToM(MAX_SPEED_PIXELS);
        if (velocity.len2() > maxSpeed * maxSpeed) {
            runtimeBody.body.setLinearVelocity(velocity.nor().scl(maxSpeed));
        }
    }

    private static void updateAnimationSpeed(PhysicsRuntimeBodyComponent runtimeBody,
                                             AnimationComponent animation,
                                             Box2dWorldService box2d,
                                             float dt) {
        if (animation == null) return;

        Vector2 velocity = runtimeBody.body.getLinearVelocity();
        float speedPixels = box2d.mToPx(velocity.len());
        if (speedPixels < ANIMATION_STOP_SPEED_PIXELS) {
            animation.fps = 0f;
            return;
        }

        float safeDt = Math.min(dt, CAMERA_DT_MAX);
        animation.fps = 1f;
        animation.stateTime += speedPixels * safeDt / ANIMATION_PIXELS_PER_FRAME - safeDt;
    }

    private static void setAnimationClip(AnimationComponent animation, String clipName) {
        if (animation == null || animation.clips == null || !animation.clips.containsKey(clipName)) return;
        if (clipName.equals(animation.currentClip)) return;

        animation.currentClip = clipName;
        animation.frame = -1;
    }

    private static String runClipForDirection(float dx, float dy) {
        float degrees = (float) Math.toDegrees(Math.atan2(dx, dy));
        if (degrees < 0f) {
            degrees += 360f;
        }
        int sector = (int) Math.floor((degrees + 22.5f) / 45f) % RUN_CLIPS.length;
        return RUN_CLIPS[sector];
    }

    private void followCamera(PhysicsRuntimeBodyComponent runtimeBody, Box2dWorldService box2d) {
        if (camera == null || runtimeBody.body == null) return;

        Vector2 heroPosition = runtimeBody.body.getPosition();
        camera.position.set(box2d.mToPx(heroPosition.x), box2d.mToPx(heroPosition.y), camera.position.z);
        camera.update();
    }
}
