package games.pixscape.demo.system;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraFollowSystemTest {
    private OrthographicCamera camera;
    private CameraFollowSystem followSystem;

    @Before
    public void setUp() {
        camera = new TestCamera();
        followSystem = new CameraFollowSystem(camera);
    }

    @Test
    public void followsHeroAndIgnoresOtherEntities() {
        camera.position.set(7f, 8f, 0f);
        followSystem.followBody("crate", new Vector2(2f, 3f), 100f);
        assertCameraPosition(7f, 8f);

        followSystem.followBody("hero", new Vector2(4f, 5f), 100f);
        assertCameraPosition(400f, 500f);
    }

    @Test
    public void suspendingAndResumingChangesFollowDeterministically() {
        followSystem.followBody("hero", new Vector2(1f, 2f), 100f);
        assertTrue(followSystem.isFollowing());
        assertCameraPosition(100f, 200f);

        followSystem.suspendFollow();
        followSystem.followBody("hero", new Vector2(3f, 4f), 100f);
        assertFalse(followSystem.isFollowing());
        assertCameraPosition(100f, 200f);

        followSystem.resumeFollow();
        followSystem.followBody("hero", new Vector2(3f, 4f), 100f);
        assertTrue(followSystem.isFollowing());
        assertCameraPosition(300f, 400f);
    }

    private void assertCameraPosition(float x, float y) {
        assertEquals(x, camera.position.x, 0.001f);
        assertEquals(y, camera.position.y, 0.001f);
    }

    private static final class TestCamera extends OrthographicCamera {
        @Override
        public void update() {
            // Matrix projection is irrelevant to camera-follow policy and requires native LibGDX code.
        }
    }
}
