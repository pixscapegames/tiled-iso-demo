package games.pixscape.demo.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.api.GameObjectInstance;
import games.pixscape.runtime.api.ParticleRef;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneLoadHandle;
import org.junit.Assume;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Opt-in measurements against the demo's exported assets and full GL/ECS/physics pipeline. */
public class RuntimeSpawnPerformanceTest {
    @Test public void measureSpawnAndFollowingFrames() {
        Assume.assumeTrue(Boolean.getBoolean("runtime.performance"));
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Runtime spawn measurements");
        config.setWindowedMode(960, 720);
        config.setInitialVisible(false);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        config.disableAudio(true);
        config.useVsync(false);
        new Lwjgl3Application(new ApplicationAdapter() {
            PixscapeEngine engine;
            SceneLoadHandle load;
            int frame;
            String objectName = "snake";
            com.badlogic.gdx.files.FileHandle multiFile;
            final com.sun.management.ThreadMXBean allocations =
                    (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

            @Override public void create() {
                try {
                    OrthographicCamera camera = new OrthographicCamera(960, 720);
                    camera.position.set(-95, 3543, 0);
                    camera.update();
                    engine = new PixscapeEngine().setWorldCamera(camera);
                    engine.loadProject(Gdx.files.internal(""));
                    // Derive a multi-member case from the same exported actor, without changing authored assets.
                    var candidate = Gdx.files.local("pixscape-project/gameobjects/runtime-probe-multi.gameobject");
                    if (candidate.exists()) throw new IllegalStateException("Probe file already exists: " + candidate);
                    multiFile = candidate;
                    var loader = new games.pixscape.runtime.gameobject.GameObjectAssetLoader();
                    var sourceFile = Gdx.files.internal("pixscape-project/gameobjects/snake.gameobject");
                    var multi = loader.load(sourceFile);
                    for (int i = 0; i < 7; i++) {
                        var member = loader.load(sourceFile).entities.get(1);
                        member.sourceEntityId = 3 + i;
                        member.transform.x += (i + 1) * 96;
                        member.identity.name = "member" + i;
                        multi.entities.add(member);
                    }
                    loader.save(multiFile, multi);
                    engine.config().getSceneMeta("demo").runtimeGameObjectIds.add("runtime-probe-multi");
                    engine.resize(960, 720);
                    load = engine.beginLoadScene("demo");
                } catch (Throwable ex) { fail(ex); }
            }
            @Override public void render() {
                try {
                    if (!load.isReady()) {
                        load.update();
                        if (load.isFailed()) throw new AssertionError("Scene loading failed");
                        return;
                    }
                    tick();
                    if (++frame != 40) return;
                    measure("first", 1, 1, true);
                    measure("repeated", 40, 1, true);
                    measure("burst16", 10, 16, true);
                    for (int i = 0; i < 128; i++) spawn(i);
                    for (int i = 0; i < 5; i++) tick();
                    measure("populated128", 40, 1, true);
                    objectName = "runtime-probe-multi";
                    measure("members8", 40, 1, true);
                    objectName = "snake";
                    particles(false);
                    particles(true);
                    Gdx.app.exit();
                } catch (Throwable ex) { fail(ex); }
            }
            GameObjectInstance spawn(int index) {
                return engine.api().gameObjects().spawn(objectName, -256 + index * 96, 3383);
            }
            void tick() { engine.update(1f / 60f); engine.render(); }
            long bytes() { return allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
            void measure(String label, int samples, int count, boolean remove) {
                long[] call = new long[samples], following = new long[samples], allocated = new long[samples];
                long[] nextAllocated = new long[samples];
                long[] removal = new long[samples], removalAllocated = new long[samples];
                long[] afterRemoval = new long[samples], afterRemovalAllocated = new long[samples];
                GameObjectInstance[] instances = new GameObjectInstance[count];
                for (int n = 0; n < samples; n++) {
                    long b = bytes(), t = System.nanoTime();
                    for (int i = 0; i < count; i++) instances[i] = spawn(i);
                    call[n] = System.nanoTime() - t; allocated[n] = bytes() - b;
                    b = bytes();
                    for (int f = 0; f < 3; f++) {
                        t = System.nanoTime(); tick();
                        following[n] = Math.max(following[n], System.nanoTime() - t);
                    }
                    nextAllocated[n] = bytes() - b;
                    b = bytes(); t = System.nanoTime();
                    if (remove) for (GameObjectInstance instance : instances) instance.despawn();
                    tick();
                    removal[n] = System.nanoTime() - t; removalAllocated[n] = bytes() - b;
                    b = bytes();
                    for (int f = 0; f < 3; f++) {
                        t = System.nanoTime(); tick();
                        afterRemoval[n] = Math.max(afterRemoval[n], System.nanoTime() - t);
                    }
                    afterRemovalAllocated[n] = bytes() - b;
                }
                report(label + ".call.ns", call); report(label + ".next3.peak.ns", following);
                report(label + ".call.bytes", allocated); report(label + ".next3.bytes", nextAllocated);
                report(label + ".remove-frame.ns", removal); report(label + ".remove-frame.bytes", removalAllocated);
                report(label + ".remove-next3.peak.ns", afterRemoval);
                report(label + ".remove-next3.bytes", afterRemovalAllocated);
            }
            void particles(boolean lightingSetter) {
                long[] call = new long[40], next = new long[40], allocated = new long[40];
                long[] removal = new long[40], removalAllocated = new long[40];
                long[] afterRemoval = new long[40], afterRemovalAllocated = new long[40];
                for (int n = 0; n < 40; n++) {
                    long b = bytes(), t = System.nanoTime();
                    ParticleRef particle = engine.api().particles().spawn("Flame", -95, 3543);
                    if (lightingSetter) particle.particles().setIgnoreAmbientLighting(true);
                    call[n] = System.nanoTime() - t;
                    for (int f = 0; f < 3; f++) {
                        t = System.nanoTime(); tick(); next[n] = Math.max(next[n], System.nanoTime() - t);
                    }
                    allocated[n] = bytes() - b;
                    b = bytes(); t = System.nanoTime();
                    particle.remove(); tick();
                    removal[n] = System.nanoTime() - t; removalAllocated[n] = bytes() - b;
                    b = bytes();
                    for (int f = 0; f < 3; f++) {
                        t = System.nanoTime(); tick();
                        afterRemoval[n] = Math.max(afterRemoval[n], System.nanoTime() - t);
                    }
                    afterRemovalAllocated[n] = bytes() - b;
                }
                String label = "particles.setter-" + lightingSetter;
                report(label + ".call.ns", call); report(label + ".next3.peak.ns", next);
                report(label + ".total.bytes", allocated);
                report(label + ".remove-frame.ns", removal); report(label + ".remove-frame.bytes", removalAllocated);
                report(label + ".remove-next3.peak.ns", afterRemoval);
                report(label + ".remove-next3.bytes", afterRemovalAllocated);
            }
            void report(String label, long[] values) {
                Arrays.sort(values);
                System.out.println("PERF " + label + " n=" + values.length + " p50=" + values[values.length / 2]
                        + " p95=" + values[(int) Math.ceil(values.length * .95) - 1] + " max=" + values[values.length - 1]);
            }
            void fail(Throwable ex) { failure[0] = ex; Gdx.app.exit(); }
            @Override public void dispose() {
                if (engine != null) engine.dispose();
                if (multiFile != null) multiFile.delete();
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Runtime measurement failed", failure[0]);
    }
}
