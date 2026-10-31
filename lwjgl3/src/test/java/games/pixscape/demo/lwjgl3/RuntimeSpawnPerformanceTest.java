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
import games.pixscape.runtime.profiling.SystemProfilePhases;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import org.lwjgl.glfw.GLFW;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;

/** Opt-in measurements against the demo's exported assets and full GL/ECS/physics pipeline. */
public class RuntimeSpawnPerformanceTest {
    /** Desktop diagnostic only; correlates frames with JVM events without per-frame logging. */
    @Name("pixscape.ProbeFrame") @StackTrace(false) @Threshold("0 ns")
    static class ProbeFrame extends Event {
        public int sequence;
        public String scenario, stage, mode;
        public long engineNs, cpuNs, allocatedBytes, updateNs, profiledNs, physicsNs, submitNs;
        public long spriteSyncNs, finishNs, presentNs, totalNs;
        public long beforePhysicsNs, beforeGeometryNs, beforeSubmitNs, lightingPrepNs, tiledSyncNs;
        public int hierarchyRebuilds, chunkCompiles;
        public int worstPhase;
        public long worstPhaseNs;
    }

    @Test public void measureSpawnAndFollowingFrames() {
        Assume.assumeTrue(Boolean.getBoolean("runtime.performance") && !Boolean.getBoolean("runtime.normalLoop"));
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
            final games.pixscape.runtime.profiling.FrameSystemProfiler profiler =
                    (Boolean.getBoolean("runtime.removal.phases") || Boolean.getBoolean("runtime.frameTrace"))
                            ? new games.pixscape.runtime.profiling.FrameSystemProfiler() : null;
            final boolean trace = Boolean.getBoolean("runtime.frameTrace");
            final String frameMode = System.getProperty("runtime.frameMode", "submit");
            String scenario = "warmup";
            final long[] phaseGaps = new long[SystemProfilePhases.PHASE_COUNT];
            long lastPhaseEnd;
            int sequence;
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
                    if (profiler != null) {
                        profiler.setEnabled(trace);
                        engine.setSystemProfiler(!trace ? profiler : new games.pixscape.runtime.profiling.SystemProfiler() {
                            public boolean enabled() { return profiler.enabled(); }
                            public void beginFrame() {
                                profiler.beginFrame(); Arrays.fill(phaseGaps, 0); lastPhaseEnd = System.nanoTime();
                            }
                            public long begin(int phase) {
                                long start = profiler.begin(phase);
                                phaseGaps[phase] += start - lastPhaseEnd;
                                return start;
                            }
                            public void end(int phase, long start) {
                                profiler.end(phase, start); lastPhaseEnd = System.nanoTime();
                            }
                        });
                    }
                    if (!frameMode.equals("submit") && !frameMode.equals("finish") && !frameMode.equals("present")) {
                        throw new IllegalArgumentException("Unknown diagnostic frame mode: " + frameMode);
                    }
                    if (trace) {
                        allocations.setThreadCpuTimeEnabled(true);
                        System.out.println("TRACE mode=" + frameMode + " java=" + System.getProperty("java.runtime.version")
                                + " gpu=" + Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_RENDERER));
                    }
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
                    for (int i = 0; i < 128; i++) spawn(i + 128);
                    for (int i = 0; i < 5; i++) tick();
                    measure("members8-populated1024", 40, 1, true);
                    objectName = "snake";
                    particles(false);
                    particles(true);
                    jointRemoval();
                    Gdx.app.exit();
                } catch (Throwable ex) { fail(ex); }
            }
            GameObjectInstance spawn(int index) {
                return engine.api().gameObjects().spawn(objectName, -256 + index * 96, 3383);
            }
            void tick() { tick("setup"); }
            void tick(String stage) {
                if (!trace) { engine.update(1f / 60f); engine.render(); return; }
                ProbeFrame event = new ProbeFrame();
                event.sequence = sequence++;
                event.scenario = scenario; event.stage = stage; event.mode = frameMode;
                var hierarchy = engine.getWorld().getSystem(games.pixscape.runtime.system.GameObjectHierarchySystem.class);
                var tiled = engine.getWorld().getSystem(games.pixscape.runtime.system.RenderTiledSyncSystem.class);
                int rebuilds = hierarchy.rebuildCount(), compiles = tiled.persistentChunkCompilationCount();
                long allocated = bytes(), cpu = allocations.getCurrentThreadCpuTime();
                event.begin();
                long start = System.nanoTime();
                engine.update(1f / 60f);
                event.updateNs = System.nanoTime() - start;
                engine.render();
                long end = System.nanoTime();
                event.engineNs = end - start;
                event.cpuNs = allocations.getCurrentThreadCpuTime() - cpu;
                event.allocatedBytes = bytes() - allocated;
                event.hierarchyRebuilds = hierarchy.rebuildCount() - rebuilds;
                event.chunkCompiles = tiled.persistentChunkCompilationCount() - compiles;
                event.lightingPrepNs = engine.getFrameQueue().lighting.preparationCpuNs;
                event.tiledSyncNs = profiler.durationNs(SystemProfilePhases.RENDER_TILED_SYNC);
                event.profiledNs = profiler.totalNs();
                event.physicsNs = profiler.durationNs(SystemProfilePhases.BOX2D_SYNC);
                event.submitNs = profiler.durationNs(SystemProfilePhases.RENDER_SUBMIT);
                event.spriteSyncNs = profiler.durationNs(SystemProfilePhases.RENDER_SPRITE_SYNC);
                event.beforePhysicsNs = phaseGaps[SystemProfilePhases.BOX2D_SYNC];
                event.beforeGeometryNs = phaseGaps[SystemProfilePhases.UPDATE_WORLD_GEOMETRY];
                event.beforeSubmitNs = phaseGaps[SystemProfilePhases.RENDER_SUBMIT];
                event.worstPhase = profiler.worstPhaseId(); event.worstPhaseNs = profiler.worstNs();
                if (frameMode.equals("finish")) {
                    long t = System.nanoTime(); Gdx.gl.glFinish(); event.finishNs = System.nanoTime() - t;
                } else if (frameMode.equals("present")) {
                    long t = System.nanoTime();
                    GLFW.glfwSwapBuffers(((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle());
                    GLFW.glfwPollEvents();
                    event.presentNs = System.nanoTime() - t;
                }
                event.totalNs = System.nanoTime() - start;
                event.end(); event.commit();
            }
            long bytes() { return allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
            void measure(String label, int samples, int count, boolean remove) {
                scenario = label;
                long[] call = new long[samples], following = new long[samples], allocated = new long[samples];
                long[] nextAllocated = new long[samples];
                long[] removalCall = new long[samples], removalCallAllocated = new long[samples];
                long[] removal = new long[samples], removalAllocated = new long[samples];
                long[] afterRemoval = new long[samples], afterRemovalAllocated = new long[samples];
                GameObjectInstance[] instances = new GameObjectInstance[count];
                for (int n = 0; n < samples; n++) {
                    long b = bytes(), t = System.nanoTime();
                    for (int i = 0; i < count; i++) instances[i] = spawn(i);
                    call[n] = System.nanoTime() - t; allocated[n] = bytes() - b;
                    b = bytes();
                    for (int f = 0; f < 3; f++) {
                        t = System.nanoTime(); tick("spawn-next");
                        following[n] = Math.max(following[n], System.nanoTime() - t);
                    }
                    nextAllocated[n] = bytes() - b;
                    b = bytes(); t = System.nanoTime();
                    if (remove) for (GameObjectInstance instance : instances) instance.despawn();
                    removalCall[n] = System.nanoTime() - t; removalCallAllocated[n] = bytes() - b;
                    tick("remove");
                    removal[n] = System.nanoTime() - t; removalAllocated[n] = bytes() - b;
                    b = bytes();
                    for (int f = 0; f < 3; f++) {
                        t = System.nanoTime(); tick("remove-next");
                        afterRemoval[n] = Math.max(afterRemoval[n], System.nanoTime() - t);
                    }
                    afterRemovalAllocated[n] = bytes() - b;
                }
                report(label + ".call.ns", call); report(label + ".next3.peak.ns", following);
                report(label + ".call.bytes", allocated); report(label + ".next3.bytes", nextAllocated);
                report(label + ".remove-call.ns", removalCall); report(label + ".remove-call.bytes", removalCallAllocated);
                report(label + ".remove-frame.ns", removal); report(label + ".remove-frame.bytes", removalAllocated);
                report(label + ".remove-next3.peak.ns", afterRemoval);
                report(label + ".remove-next3.bytes", afterRemovalAllocated);
            }
            void particles(boolean lightingSetter) {
                scenario = "particles.setter-" + lightingSetter;
                long[] call = new long[40], next = new long[40], allocated = new long[40];
                long[] removalCall = new long[40], removalCallAllocated = new long[40];
                long[] removal = new long[40], removalAllocated = new long[40];
                long[] afterRemoval = new long[40], afterRemovalAllocated = new long[40];
                for (int n = 0; n < 40; n++) {
                    long b = bytes(), t = System.nanoTime();
                    ParticleRef particle = engine.api().particles().spawn("Flame", -95, 3543);
                    if (lightingSetter) particle.particles().setIgnoreAmbientLighting(true);
                    call[n] = System.nanoTime() - t;
                    for (int f = 0; f < 3; f++) {
                        t = System.nanoTime(); tick("spawn-next"); next[n] = Math.max(next[n], System.nanoTime() - t);
                    }
                    allocated[n] = bytes() - b;
                    b = bytes(); t = System.nanoTime();
                    particle.remove();
                    removalCall[n] = System.nanoTime() - t; removalCallAllocated[n] = bytes() - b;
                    tick("remove");
                    removal[n] = System.nanoTime() - t; removalAllocated[n] = bytes() - b;
                    b = bytes();
                    for (int f = 0; f < 3; f++) {
                        t = System.nanoTime(); tick("remove-next");
                        afterRemoval[n] = Math.max(afterRemoval[n], System.nanoTime() - t);
                    }
                    afterRemovalAllocated[n] = bytes() - b;
                }
                String label = "particles.setter-" + lightingSetter;
                report(label + ".call.ns", call); report(label + ".next3.peak.ns", next);
                report(label + ".total.bytes", allocated);
                report(label + ".remove-call.ns", removalCall); report(label + ".remove-call.bytes", removalCallAllocated);
                report(label + ".remove-frame.ns", removal); report(label + ".remove-frame.bytes", removalAllocated);
                report(label + ".remove-next3.peak.ns", afterRemoval);
                report(label + ".remove-next3.bytes", afterRemovalAllocated);
            }
            void jointRemoval() {
                scenario = "joints512";
                var world = engine.getWorld();
                var sync = world.getSystem(games.pixscape.runtime.system.Box2dSyncSystem.class);
                if (sync.getBox2d() == null) throw new AssertionError("Native physics required");
                var physics = new games.pixscape.runtime.service.PhysicsService(
                        world, sync.getBox2d(), engine.config().getSceneMeta("demo"));
                ProbeFrame setup = trace ? startOperation(sequence++, "setup-call", "submit") : null;
                int backgroundA = body(physics, 0), backgroundB = body(physics, 100);
                for (int i = 0; i < 512; i++) physics.createDistanceJoint(backgroundA, backgroundB);
                finishOperation(setup);
                for (int i = 0; i < 5; i++) tick();
                long[] call = new long[40], allocated = new long[40], frame = new long[40];
                long[] frameBytes = new long[40], next = new long[40], nextBytes = new long[40];
                if (profiler != null) profiler.setEnabled(true);
                long[][] phases = profiler != null
                        ? new long[games.pixscape.runtime.profiling.SystemProfilePhases.PHASE_COUNT][120] : null;
                for (int n = 0; n < 40; n++) {
                    setup = trace ? startOperation(sequence++, "setup-call", "submit") : null;
                    int a = body(physics, 400), b = body(physics, 500);
                    int c = body(physics, 600), d = body(physics, 700);
                    int source1 = physics.createRevoluteJoint(a, b, 450, -10000);
                    int source2 = physics.createPrismaticJoint(c, d, 650, -10000);
                    int gear = physics.createGearJoint(source1, source2, 2);
                    finishOperation(setup);
                    for (int i = 0; i < 3; i++) tick();
                    var ref = engine.api().entities().requireEntityId(a);
                    ProbeFrame removal = trace ? startOperation(sequence++, "remove-call", "submit") : null;
                    long bytesBefore = bytes(), time = System.nanoTime();
                    ref.remove();
                    finishOperation(removal);
                    call[n] = System.nanoTime() - time; allocated[n] = bytes() - bytesBefore;
                    tick("remove");
                    frame[n] = System.nanoTime() - time; frameBytes[n] = bytes() - bytesBefore;
                    bytesBefore = bytes();
                    for (int i = 0; i < 3; i++) {
                        time = System.nanoTime(); tick("remove-next");
                        next[n] = Math.max(next[n], System.nanoTime() - time);
                        if (profiler != null) for (int phase = 0; phase < phases.length; phase++) {
                            phases[phase][n * 3 + i] = profiler.durationNs(phase);
                        }
                    }
                    nextBytes[n] = bytes() - bytesBefore;
                    if (ref.exists() || world.getEntityManager().isActive(source1)
                            || world.getEntityManager().isActive(gear)
                            || !world.getEntityManager().isActive(source2)) throw new AssertionError("Joint closure");
                    engine.api().entities().requireEntityId(b).remove();
                    engine.api().entities().requireEntityId(c).remove();
                    engine.api().entities().requireEntityId(d).remove();
                    tick();
                }
                report("joints512.remove-call.ns", call); report("joints512.remove-call.bytes", allocated);
                report("joints512.remove-frame.ns", frame); report("joints512.remove-frame.bytes", frameBytes);
                report("joints512.remove-next3.peak.ns", next); report("joints512.remove-next3.bytes", nextBytes);
                if (profiler != null) for (int phase = 0; phase < phases.length; phase++) {
                    report("joints512.phase." + games.pixscape.runtime.profiling.SystemProfilePhases.name(phase) + ".ns", phases[phase]);
                }
            }
            int body(games.pixscape.runtime.service.PhysicsService physics, float x) {
                return createBody(engine, physics, x);
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

    /** Runs the real demo, with one Main.render() per native callback and normal buffer swaps. */
    @Test public void measureNormalDemoFrames() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("runtime.performance") && Boolean.getBoolean("runtime.normalLoop"));
        var engineField = games.pixscape.demo.Main.class.getDeclaredField("engine");
        var stageField = games.pixscape.demo.Main.class.getDeclaredField("loadingStage");
        var startField = games.pixscape.demo.Main.class.getDeclaredField("startRequested");
        engineField.setAccessible(true); stageField.setAccessible(true); startField.setAccessible(true);
        THREAD.setThreadCpuTimeEnabled(true);
        Throwable[] failure = {null};
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Runtime normal demo measurements"); config.setWindowedMode(960, 720);
        config.setInitialVisible(true); config.disableAudio(true); config.useVsync(false);
        config.setForegroundFPS(0);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        new Lwjgl3Application(new ApplicationAdapter() {
            final games.pixscape.demo.Main demo = new games.pixscape.demo.Main();
            final games.pixscape.runtime.profiling.FrameSystemProfiler profiler =
                    new games.pixscape.runtime.profiling.FrameSystemProfiler();
            PixscapeEngine engine;
            games.pixscape.runtime.service.PhysicsService physics;
            ProbeFrame pending;
            long previousStart;
            int frame, sequence, step = -5, trial;
            int a, b, c, d, source1, source2, gear;
            games.pixscape.runtime.api.EntityRef removed;
            @Override public void create() { demo.create(); profiler.setEnabled(true); }
            @Override public void resize(int width, int height) { demo.resize(width, height); }
            @Override public void render() {
                long callbackStart = System.nanoTime();
                try {
                    // Complete the previous interval only after the backend swapped/polled.
                    if (pending != null) {
                        pending.totalNs = callbackStart - previousStart;
                        pending.presentNs = pending.totalNs - pending.engineNs;
                        pending.end(); pending.commit(); pending = null;
                    }
                    if (trial == 40) { Gdx.app.exit(); return; }
                    String stage = String.valueOf(stageField.get(demo));
                    if (stage.equals("MENU") && engine == null) {
                        engine = (PixscapeEngine) engineField.get(demo);
                        engine.setSystemProfiler(profiler);
                        startField.setBoolean(demo, true);
                        System.out.println("TRACE normal java=" + System.getProperty("java.runtime.version")
                                + " gpu=" + Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_RENDERER));
                    }
                    if (!stage.equals("GAMEPLAY") && !stage.equals("READY")) { demo.render(); return; }
                    var hierarchy = engine.getWorld().getSystem(games.pixscape.runtime.system.GameObjectHierarchySystem.class);
                    var tiled = engine.getWorld().getSystem(games.pixscape.runtime.system.RenderTiledSyncSystem.class);
                    ProbeFrame event = new ProbeFrame();
                    event.sequence = sequence++; event.mode = "normal"; event.scenario = "joints512";
                    event.stage = frame < 40 ? "warmup" : step == 3 ? "remove" :
                            step >= 4 && step <= 6 ? "remove-next" : "setup";
                    int rebuilds = hierarchy.rebuildCount(), compiles = tiled.persistentChunkCompilationCount();
                    long allocated = THREAD.getThreadAllocatedBytes(Thread.currentThread().threadId());
                    long cpu = THREAD.getCurrentThreadCpuTime();
                    event.begin(); previousStart = callbackStart;
                    if (frame == 40) {
                        // Same authored workload for both Runtime versions; the actual demo keeps its gameplay systems.
                        for (int i = 0; i < 128; i++) engine.api().gameObjects().spawn("snake", -256 + i * 96, 3383);
                        var sync = engine.getWorld().getSystem(games.pixscape.runtime.system.Box2dSyncSystem.class);
                        physics = new games.pixscape.runtime.service.PhysicsService(
                                engine.getWorld(), sync.getBox2d(), engine.config().getSceneMeta("demo"));
                        ProbeFrame setup = startOperation(sequence++, "setup-call", "normal");
                        int backgroundA = createBody(engine, physics, 0), backgroundB = createBody(engine, physics, 100);
                        for (int i = 0; i < 512; i++) physics.createDistanceJoint(backgroundA, backgroundB);
                        finishOperation(setup);
                    }
                    if (frame >= 40 && step == 0) {
                        ProbeFrame setup = startOperation(sequence++, "setup-call", "normal");
                        a = createBody(engine, physics, 400); b = createBody(engine, physics, 500);
                        c = createBody(engine, physics, 600); d = createBody(engine, physics, 700);
                        source1 = physics.createRevoluteJoint(a, b, 450, -10000);
                        source2 = physics.createPrismaticJoint(c, d, 650, -10000);
                        gear = physics.createGearJoint(source1, source2, 2);
                        finishOperation(setup);
                    }
                    if (frame >= 40 && step == 3) {
                        removed = engine.api().entities().requireEntityId(a);
                        ProbeFrame removal = startOperation(sequence++, "remove-call", "normal");
                        removed.remove(); finishOperation(removal);
                    }
                    if (frame >= 40 && step == 7) {
                        var world = engine.getWorld();
                        if (removed.exists() || world.getEntityManager().isActive(source1)
                                || world.getEntityManager().isActive(gear)
                                || !world.getEntityManager().isActive(source2)) throw new AssertionError("Joint closure");
                        engine.api().entities().requireEntityId(b).remove();
                        engine.api().entities().requireEntityId(c).remove();
                        engine.api().entities().requireEntityId(d).remove();
                    }
                    demo.render();
                    event.engineNs = System.nanoTime() - callbackStart;
                    event.cpuNs = THREAD.getCurrentThreadCpuTime() - cpu;
                    event.allocatedBytes = THREAD.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
                    event.hierarchyRebuilds = hierarchy.rebuildCount() - rebuilds;
                    event.chunkCompiles = tiled.persistentChunkCompilationCount() - compiles;
                    event.profiledNs = profiler.totalNs();
                    event.physicsNs = profiler.durationNs(SystemProfilePhases.BOX2D_SYNC);
                    event.submitNs = profiler.durationNs(SystemProfilePhases.RENDER_SUBMIT);
                    event.tiledSyncNs = profiler.durationNs(SystemProfilePhases.RENDER_TILED_SYNC);
                    event.lightingPrepNs = engine.getFrameQueue().lighting.preparationCpuNs;
                    event.worstPhase = profiler.worstPhaseId(); event.worstPhaseNs = profiler.worstNs();
                    pending = event;
                    if (frame++ >= 40 && ++step == 8) { step = 0; trial++; }
                } catch (Throwable ex) { failure[0] = ex; Gdx.app.exit(); }
            }
            @Override public void dispose() { demo.dispose(); }
        }, config);
        if (failure[0] != null) throw new AssertionError("Normal demo measurement failed", failure[0]);
    }

    private static final com.sun.management.ThreadMXBean THREAD =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private static ProbeFrame startOperation(int sequence, String stage, String mode) {
        ProbeFrame event = new ProbeFrame();
        event.sequence = sequence; event.scenario = "joints512"; event.stage = stage; event.mode = mode;
        event.begin();
        event.allocatedBytes = -THREAD.getThreadAllocatedBytes(Thread.currentThread().threadId());
        event.cpuNs = -THREAD.getCurrentThreadCpuTime(); event.engineNs = -System.nanoTime();
        return event;
    }

    private static void finishOperation(ProbeFrame event) {
        if (event == null) return;
        event.engineNs += System.nanoTime(); event.cpuNs += THREAD.getCurrentThreadCpuTime();
        event.allocatedBytes += THREAD.getThreadAllocatedBytes(Thread.currentThread().threadId());
        event.totalNs = event.engineNs; event.end(); event.commit();
    }

    private static int createBody(PixscapeEngine engine,
            games.pixscape.runtime.service.PhysicsService physics, float x) {
        var world = engine.getWorld();
        int id = world.create();
        var transform = world.getMapper(games.pixscape.runtime.component.TransformComponent.class).create(id);
        transform.x = x; transform.y = -10000;
        physics.ensurePhysics(id);
        world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).get(id).type =
                games.pixscape.runtime.component.physics.PhysicsBodyComponent.DYNAMIC;
        var dirty = world.getSystem(games.pixscape.runtime.system.DirtyTrackerSystem.class);
        dirty.geometry(id, games.pixscape.runtime.render.GeometryDirty.ALL);
        dirty.physics(id, games.pixscape.runtime.render.PhysicsDirtyBits.ALL);
        if (Boolean.getBoolean("runtime.probe.publishBodies")) {
            // Diagnostic control: same complete detached body, using existing publication.
            // This is an internal preparation boundary, not a new public gameplay API.
            world.getSystem(games.pixscape.runtime.system.GameObjectHierarchySystem.class).registerPreparedSpawn(id);
        }
        return id;
    }
}
