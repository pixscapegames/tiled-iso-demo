package games.pixscape.demo.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Array;
import games.pixscape.demo.camera.CameraEffects;
import games.pixscape.demo.input.HeroInput;
import games.pixscape.demo.system.CameraFollowSystem;
import games.pixscape.demo.system.EnemySystem;
import games.pixscape.demo.system.HeroControlSystem;
import games.pixscape.demo.util.CombatState;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.GameObjectInstance;
import games.pixscape.runtime.api.GameObjectsAPI;
import games.pixscape.runtime.api.ParticleRef;
import games.pixscape.runtime.api.ParticlesAPI;
import games.pixscape.runtime.api.PixscapeAPI;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneLoadHandle;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Real exported assets, real contacts and hero inputs; no fabricated combat signals. */
public class SnakeRuntimeGlTest {
    @Test
    public void preparedSnakeSpawnsOnceAndDiesAfterFiveHeroHits() {
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Snake Runtime integration check");
        config.setWindowedMode(960, 720);
        config.setInitialVisible(false);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        config.disableAudio(true);
        config.useVsync(false);
        new Lwjgl3Application(new ApplicationAdapter() {
            final OrthographicCamera camera = new OrthographicCamera();
            final CameraEffects effects = new CameraEffects();
            final CombatState combat = new CombatState();
            final Input input = new Input();
            PixscapeEngine engine;
            PixscapeAPI api;
            SceneLoadHandle load;
            EntityRef hero;
            EntityRef snake;
            GameObjectInstance instance;
            Body heroBody;
            int spawnCount;
            int flameCount;
            int score;
            int deathNotifications;
            int frame;
            int deathFrame = -1;
            int previousAsset = -1;
            int previousHeroHits;
            boolean sawIdle;
            boolean sawAttack;
            boolean sawHit;
            boolean sawDeath;
            long deadline;

            @Override public void create() {
                try {
                    deadline = System.nanoTime() + 90_000_000_000L;
                    engine = new PixscapeEngine().setWorldCamera(camera);
                    api = engine.api();
                    GameObjectsAPI objects = (name, x, y) -> {
                        Assert.assertTrue("Gameplay spawn before READY", load.isReady());
                        spawnCount++;
                        instance = api.gameObjects().spawn(name, x, y);
                        snake = instance.requireMember("snake");
                        return instance;
                    };
                    ParticlesAPI particles = new ParticlesAPI() {
                        @Override public ParticleRef spawn(String name, float x, float y) {
                            if (name.equals("Flame")) flameCount++;
                            return api.particles().spawn(name, x, y);
                        }
                        @Override public ParticleRef oneshot(String name, float x, float y) {
                            return api.particles().oneshot(name, x, y);
                        }
                    };
                    // Observe public calls while keeping real Runtime behavior underneath.
                    PixscapeAPI observed = (PixscapeAPI) Proxy.newProxyInstance(
                        PixscapeAPI.class.getClassLoader(), new Class<?>[]{PixscapeAPI.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("gameObjects")) return objects;
                            if (method.getName().equals("particles")) return particles;
                            try { return method.invoke(api, args); }
                            catch (InvocationTargetException ex) { throw ex.getCause(); }
                        });
                    engine.setPreRenderSystemCustomizer(builder -> builder.with(
                        new CameraFollowSystem(camera, api)));
                    engine.setPostRenderSystemCustomizer(builder -> builder.with(
                        new HeroControlSystem(observed, combat, input),
                        new EnemySystem(observed, combat, effects, (points, x, y) -> {
                            score += points;
                            Assert.assertEquals(-95.93151f, x, 0.01f);
                            Assert.assertEquals(3543.4182f, y, 0.01f);
                        }, () -> deathNotifications++)));
                    engine.loadProject(Gdx.files.internal(""));
                    engine.resize(960, 720);
                    load = engine.beginLoadScene("demo");
                } catch (Throwable ex) { fail(ex); }
            }

            @Override public void render() {
                if (failure[0] != null) return;
                try {
                    Assert.assertTrue("Combat test timed out", System.nanoTime() < deadline);
                    if (!load.isReady()) {
                        load.update();
                        if (load.isFailed()) throw new AssertionError("Scene load failed", load.failure());
                        if (!load.isReady()) return;
                        hero = api.entities().requireName("hero");
                        heroBody = api.physics().body(hero);
                        verifyReadyResources();
                    }
                    // Test positioning uses the borrowed Box2D body, never authored transforms.
                    // Real physics steps below produce begin/end contacts against exported sensors.
                    if (frame == 10 || frame == 100) placeHero(-448.735f, 3518.4704f);
                    if (frame == 70) placeHero(-520.1351f, 3302.552f);
                    if (frame == 140) placeHero(-170f, 3540f);
                    input.x = frame == 140 ? 1f : 0f;
                    input.attack = frame >= 140 && deathFrame < 0;
                    effects.update(1f / 60f);
                    camera.zoom = effects.zoomMultiplier();
                    engine.update(1f / 60f);
                    engine.render();
                    if (frame < 10) Assert.assertEquals(0, spawnCount);
                    if (frame == 60) {
                        Assert.assertEquals(1, spawnCount);
                        Assert.assertEquals(2, flameCount);
                        Assert.assertEquals(0.6f, effects.zoomMultiplier(), 0.001f);
                        Assert.assertFalse(instance.root().animation().exists());
                        Assert.assertNull(api.physics().body(instance.root()));
                        Assert.assertEquals(-255.9315f, instance.root().transform().x(), 0.001f);
                        Assert.assertEquals(3383.4182f, instance.root().transform().y(), 0.001f);
                        Assert.assertEquals(hero.renderOrder().layerIndex(), instance.root().renderOrder().layerIndex());
                        Assert.assertEquals(4, instance.root().renderOrder().zIndex());
                        Assert.assertEquals(160f, snake.transform().x(), 0.001f);
                        Body body = api.physics().body(snake);
                        Assert.assertNotNull(body);
                        Assert.assertEquals(-95.93151f, body.getPosition().x * 100f, 0.01f);
                        Assert.assertEquals(3543.4182f, body.getPosition().y * 100f, 0.01f);
                        capture("idle");
                    }
                    if (frame >= 100) Assert.assertEquals("Appearance must be unique", 1, spawnCount);
                    observeCombat();
                    Assert.assertFalse("Hero died before winning the combat", combat.isHeroDead());
                    if (deathFrame >= 0 && frame == deathFrame + 150) {
                        Assert.assertEquals(50, score);
                        Assert.assertEquals(1, deathNotifications);
                        Assert.assertTrue(sawIdle && sawAttack && sawHit && sawDeath);
                        Assert.assertTrue("Snake must also damage the hero", combat.heroHitCount() > 0);
                        Assert.assertEquals(1f, effects.zoomMultiplier(), 0.001f);
                        Assert.assertFalse(api.physics().body(snake).isActive());
                        capture("after-death");
                        // Re-entering the appearance sensor after death must not respawn it.
                        placeHero(-448.735f, 3518.4704f);
                    }
                    if (deathFrame >= 0 && frame > deathFrame + 180) {
                        Assert.assertEquals(1, spawnCount);
                        Assert.assertEquals(2, flameCount);
                        Assert.assertEquals(50, score);
                        Assert.assertEquals(1, deathNotifications);
                        Gdx.app.exit();
                    }
                    Assert.assertTrue("Five hits were not reached in 30 seconds; score=" + score
                        + ", hero hits=" + combat.heroHitCount(), frame++ < 1800);
                } catch (Throwable ex) { fail(ex); }
            }

            private void verifyReadyResources() {
                // Before any gameplay or render step: all owned animations must already bind.
                GameObjectInstance probe = api.gameObjects().spawn("gameobjects/snake.gameobject", -5000, -5000);
                probe.root().renderOrder().set(hero.renderOrder().layerIndex(), 4);
                EntityRef actor = probe.requireMember("snake");
                for (String name : new String[]{"snake_idle", "snake_attack", "snake_hit", "snake_death"}) {
                    actor.animation().play(name, name.equals("snake_attack") ? "attack_000" : "default");
                    Assert.assertEquals(api.animations().definition(name).assetId(), actor.sprite().assetId());
                }
                // Keep this preparation probe off-scene until engine disposal. Only calls
                // made by EnemySystem are counted as gameplay spawns by the observed API.
                for (String name : new String[]{"Blood.p", "Flame.p", "stars.p"}) {
                    ParticleRef effect = api.particles().spawn(name, 0, 0);
                    Assert.assertTrue(effect.entity().exists());
                    effect.remove();
                }
                // Tiled physics may be split across several native bodies.
                Array<Body> bodies = new Array<>();
                api.physics().box2dWorld().getBodies(bodies);
                int sensors = 0;
                for (Body body : bodies) for (Fixture fixture : body.getFixtureList()) {
                    if (!fixture.isSensor()) continue;
                    int category = fixture.getFilterData().categoryBits;
                    if (category == 4 || category == 8) {
                        Assert.assertEquals(2, fixture.getFilterData().maskBits);
                        sensors++;
                    }
                }
                Assert.assertEquals("Both exported snake sensors are required", 2, sensors);
                for (Fixture fixture : heroBody.getFixtureList()) {
                    if (fixture.isSensor()) continue;
                    Assert.assertEquals("Hero category must be authored in Studio", 2,
                        fixture.getFilterData().categoryBits);
                    Assert.assertEquals(-1, fixture.getFilterData().maskBits);
                }
            }

            private void observeCombat() {
                if (snake == null) return;
                int asset = snake.sprite().assetId();
                sawIdle |= asset == api.animations().definition("snake_idle").assetId();
                sawAttack |= asset == api.animations().definition("snake_attack").assetId();
                sawHit |= asset == api.animations().definition("snake_hit").assetId();
                if (asset != previousAsset) {
                    Assert.assertTrue("Snake must retain its authored Spatial rectangles", snake.spatial().enabled());
                    Assert.assertTrue(snake.spatial().rectangleWidth() > 0f);
                    Assert.assertTrue(snake.spatial().height() > 0f);
                    System.out.println("frame=" + frame + " snakeAsset=" + asset
                        + " score=" + score + " heroHits=" + combat.heroHitCount());
                    if (frame > 140) capture("combat-" + frame);
                    previousAsset = asset;
                }
                if (combat.heroHitCount() != previousHeroHits) {
                    Assert.assertTrue(hero.animation().clip().startsWith("hit_"));
                    capture("hero-hit-" + combat.heroHitCount());
                    previousHeroHits = combat.heroHitCount();
                }
                if (asset == api.animations().definition("snake_death").assetId()) {
                    sawDeath = true;
                    Assert.assertEquals("Death must start on exactly the fifth hit", 50, score);
                    Assert.assertFalse(api.physics().body(snake).isActive());
                    if (deathFrame < 0) deathFrame = frame;
                }
            }

            private void placeHero(float x, float y) {
                Assert.assertFalse(api.physics().box2dWorld().isLocked());
                heroBody.setTransform(x / api.physics().pixelsPerMeter(), y / api.physics().pixelsPerMeter(), 0);
                heroBody.setLinearVelocity(0, 0);
                heroBody.setAwake(true);
            }
            private void capture(String name) {
                Pixmap shot = ScreenUtils.getFrameBufferPixmap(0, 0, 960, 720);
                try { PixmapIO.writePNG(Gdx.files.local("../build/reports/snake-runtime/" + name + ".png"), shot, -1, true); }
                finally { shot.dispose(); }
            }
            private void fail(Throwable ex) { failure[0] = ex; Gdx.app.exit(); }
            @Override public void dispose() { if (engine != null) engine.dispose(); }
        }, config);
        if (failure[0] != null) throw new AssertionError("Snake Runtime check failed", failure[0]);
    }

    private static final class Input implements HeroInput {
        float x;
        boolean attack;
        @Override public float moveX() { return x; }
        @Override public float moveY() { return 0; }
        @Override public boolean attackJustPressed() { return attack; }
    }
}
