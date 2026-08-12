package games.pixscape.demo.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.PhysicsAPI;
import games.pixscape.runtime.api.PixscapeAPI;

/** Keeps the world camera centered on the named hero while follow is enabled. */
public final class CameraFollowSystem extends BaseSystem {
    private static final String HERO_NAME = "hero";

    private final OrthographicCamera camera;
    private final PixscapeAPI api;
    private final FollowState followState;
    private EntityRef hero;

    public CameraFollowSystem(OrthographicCamera camera) {
        this(camera, null, new FollowState());
    }

    public CameraFollowSystem(OrthographicCamera camera, PixscapeAPI api) {
        this(camera, api, new FollowState());
    }

    public CameraFollowSystem(
            OrthographicCamera camera, PixscapeAPI api, FollowState followState) {
        this.camera = camera;
        this.api = api;
        this.followState = followState;
    }

    public void suspendFollow() {
        followState.suspendFollow();
    }

    public void resumeFollow() {
        followState.resumeFollow();
    }

    public boolean isFollowing() {
        return followState.isFollowing();
    }

    @Override
    protected void processSystem() {
        if (api == null) return;

        PhysicsAPI physics = api.physics();
        if (!physics.isRunning()) return;

        if (hero == null) {
            hero = api.entities().requireName(HERO_NAME);
        }
        Body body = physics.body(hero);
        if (body == null) return;

        followBody(HERO_NAME, body.getPosition(), physics.pixelsPerMeter());
    }

    void followBody(String entityName, Vector2 bodyPosition, float pixelsPerMeter) {
        if (!followState.isFollowing() || !HERO_NAME.equals(entityName) || bodyPosition == null) return;

        camera.position.set(
                bodyPosition.x * pixelsPerMeter,
                bodyPosition.y * pixelsPerMeter,
                camera.position.z
        );
        camera.update();
    }

    /** Camera-follow state shared across Runtime World replacements. */
    public static final class FollowState {
        private boolean following = true;

        public void suspendFollow() {
            following = false;
        }

        public void resumeFollow() {
            following = true;
        }

        public boolean isFollowing() {
            return following;
        }
    }
}
