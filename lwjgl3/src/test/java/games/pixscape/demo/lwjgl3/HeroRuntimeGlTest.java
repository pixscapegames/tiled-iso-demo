package games.pixscape.demo.lwjgl3;

import com.artemis.BaseSystem;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.utils.ScreenUtils;
import games.pixscape.demo.input.HeroInput;
import games.pixscape.demo.system.CameraFollowSystem;
import games.pixscape.demo.system.HeroControlSystem;
import games.pixscape.demo.util.CombatState;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.ParticleRef;
import games.pixscape.runtime.api.PixscapeAPI;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneLoadHandle;
import org.junit.Assert;
import org.junit.Test;

/** Exercises the current export through the public Runtime API in a real GL30 context. */
public class HeroRuntimeGlTest {
    @Test
    public void heroWorksWithoutSnakeAndEffectsArePreparedAtReady() {
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Hero Runtime integration check");
        config.setWindowedMode(640, 480);
        config.setInitialVisible(false);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        config.disableAudio(true);
        config.useVsync(false);
        new Lwjgl3Application(new ApplicationAdapter() {
            final OrthographicCamera camera = new OrthographicCamera();
            final CombatState combat = new CombatState();
            final ScriptedInput input = new ScriptedInput();
            PixscapeEngine engine;
            PixscapeAPI api;
            CameraFollowSystem follow;
            SceneLoadHandle load;
            EntityRef hero;
            EntityRef tree;
            Body body;
            Vector2 start;
            int frame;
            boolean moved;
            boolean collided;
            boolean animated;
            int previousAnimationFrame = -1;
            int renderedAnimationFrame = -1;
            long deadline;

            @Override public void create() {
                try {
                    deadline = System.nanoTime() + 90_000_000_000L;
                    engine = new PixscapeEngine().setWorldCamera(camera);
                    api = engine.api();
                    follow = new CameraFollowSystem(camera, api);
                    engine.setPreRenderSystemCustomizer(builder -> builder.with(
                        follow,
                        new BaseSystem() {
                            @Override protected void processSystem() {
                                // Observe the resolved frame before hero control adjusts playback time.
                                if (hero != null) renderedAnimationFrame = hero.animation().frame();
                            }
                        }));
                    // Keep this locomotion regression independent of combat. SnakeRuntimeGlTest
                    // exercises both gameplay systems together through the exported sensors.
                    engine.setPostRenderSystemCustomizer(builder -> builder.with(
                        new HeroControlSystem(api, combat, input)));
                    engine.loadProject(Gdx.files.internal(""));
                    engine.resize(640, 480);
                    load = engine.beginLoadScene("demo");
                } catch (Throwable ex) { fail(ex); }
            }

            @Override public void render() {
                if (failure[0] != null) return;
                try {
                    Assert.assertTrue("Integration check timed out", System.nanoTime() < deadline);
                    if (!load.isReady()) {
                        load.update();
                        if (load.isFailed()) throw new AssertionError("Scene load failed", load.failure());
                        if (!load.isReady()) return;
                        hero = api.entities().requireName("hero");
                        tree = api.entities().requireName("willow2");
                        Assert.assertEquals("texture-array-default", tree.shader().shader());
                        body = api.physics().body(hero);
                        Assert.assertNotNull(body);
                        Assert.assertEquals(BodyDef.BodyType.DynamicBody, body.getType());
                        Assert.assertFalse(body.getFixtureList().isEmpty());
                        start = body.getPosition().cpy();
                        // No render or gameplay step has occurred: these must be ready already.
                        for (String name : new String[]{"Blood.p", "Flame.p", "stars.p"}) {
                            ParticleRef effect = api.particles().spawn(name,
                                start.x * api.physics().pixelsPerMeter(),
                                start.y * api.physics().pixelsPerMeter());
                            effect.entity().renderOrder().layerIndex(hero.renderOrder().layerIndex());
                            effect.restart();
                            Assert.assertTrue(name, effect.entity().exists());
                            effect.remove();
                        }
                        Assert.assertThrows(IllegalStateException.class,
                            () -> api.entities().requireName("snake"));
                    }

                    int direction = frame / 150;
                    input.x = direction == 0 ? 1 : direction == 2 ? -1 : 0;
                    input.y = direction == 1 ? 1 : direction == 3 ? -1 : 0;
                    if (frame >= 600) input.x = input.y = 0;
                    input.attack = frame == 660;
                    if (frame == 750) combat.requestHeroHit();
                    engine.update(1f / 60f);
                    engine.render();

                    moved |= body.getPosition().dst2(start) > 0.25f;
                    if (frame < 600) {
                        animated |= previousAnimationFrame >= 0
                            && previousAnimationFrame != renderedAnimationFrame;
                        previousAnimationFrame = renderedAnimationFrame;
                    }
                    for (Contact contact : api.physics().box2dWorld().getContactList()) {
                        if (contact.isTouching() && !contact.getFixtureA().isSensor()
                                && !contact.getFixtureB().isSensor()
                                && (contact.getFixtureA().getBody() == body
                                    || contact.getFixtureB().getBody() == body)) collided = true;
                    }
                    float ppm = api.physics().pixelsPerMeter();
                    Assert.assertEquals(body.getPosition().x * ppm, camera.position.x, 0.1f);
                    Assert.assertEquals(body.getPosition().y * ppm, camera.position.y, 0.1f);
                    if (frame == 640 || frame == 740 || frame == 830) {
                        Assert.assertTrue("Hero must return to idle", hero.animation().clip().startsWith("idle_"));
                    }
                    if (frame == 665) Assert.assertTrue(hero.animation().clip().startsWith("attack_"));
                    if (frame == 755) Assert.assertTrue(hero.animation().clip().startsWith("hit_"));
                    if (frame == 0 || frame == 75 || frame == 225 || frame == 375
                            || frame == 525 || frame == 665 || frame == 755) capture(frame);
                    if (++frame > 840) {
                        Assert.assertTrue("Hero did not move", moved);
                        Assert.assertTrue("Locomotion animation did not advance", animated);
                        Assert.assertTrue("Route did not exercise a solid hero collision", collided);
                        Assert.assertEquals("texture-array-default", tree.shader().shader());
                        Assert.assertThrows(IllegalStateException.class,
                            () -> api.entities().requireName("snake"));
                        // Capture the authored tree material without changing its shader.
                        follow.suspendFollow();
                        camera.position.set(tree.transform().x(), tree.transform().y(), 0f);
                        camera.update();
                        engine.update(0f);
                        engine.render();
                        engine.render();
                        capture(840);
                        Gdx.app.exit();
                    }
                } catch (Throwable ex) { fail(ex); }
            }

            private void capture(int number) {
                Pixmap shot = ScreenUtils.getFrameBufferPixmap(0, 0, 640, 480);
                try {
                    PixmapIO.writePNG(Gdx.files.local("../build/reports/hero-runtime/" + number + ".png"),
                        shot, -1, true);
                } finally { shot.dispose(); }
            }

            private void fail(Throwable ex) { failure[0] = ex; Gdx.app.exit(); }
            @Override public void dispose() { if (engine != null) engine.dispose(); }
        }, config);
        if (failure[0] != null) throw new AssertionError("Hero Runtime check failed", failure[0]);
    }

    private static final class ScriptedInput implements HeroInput {
        float x;
        float y;
        boolean attack;
        @Override public float moveX() { return x; }
        @Override public float moveY() { return y; }
        @Override public boolean attackJustPressed() { return attack; }
    }
}
