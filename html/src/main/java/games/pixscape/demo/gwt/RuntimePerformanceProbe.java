package games.pixscape.demo.gwt;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.PreElement;
import games.pixscape.runtime.api.GameObjectInstance;
import games.pixscape.runtime.api.ParticleRef;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneLoadHandle;

import java.util.Arrays;

/** Opt-in browser probe: ?runtimeProbe=1; uses real exported resources and actual browser frames. */
final class RuntimePerformanceProbe extends ApplicationAdapter {
    private PixscapeEngine engine;
    private SceneLoadHandle load;
    private final PreElement output = Document.get().createPreElement();
    private final long[] calls = new long[40], peaks = new long[40], gaps = new long[40];
    private final GameObjectInstance[] objects = new GameObjectInstance[16];
    private ParticleRef particle;
    private int warmup, phase, sample, follow, population;
    private long previousFrame;
    private boolean done;
    private double heapStart;

    @Override public void create() {
        output.setId("runtime-probe-results");
        output.getStyle().setProperty("position", "absolute");
        output.getStyle().setProperty("zIndex", "10000");
        output.getStyle().setProperty("background", "white");
        output.getStyle().setProperty("color", "black");
        Document.get().getBody().appendChild(output);
        try {
            OrthographicCamera camera = new OrthographicCamera(960, 720);
            camera.position.set(-95, 3543, 0); camera.update();
            engine = new PixscapeEngine().setWorldCamera(camera);
            engine.loadProject(Gdx.files.internal(""));
            engine.resize(960, 720);
            load = engine.beginLoadScene("demo");
            line("Loading Runtime probe...");
        } catch (Throwable failure) { fail(failure); }
    }

    @Override public void render() {
        if (done) return;
        try {
            if (!load.isReady()) {
                load.update();
                if (load.isFailed()) throw new IllegalStateException("Scene loading failed");
                return;
            }
            long now = System.nanoTime();
            long gap = previousFrame == 0 ? 0 : now - previousFrame;
            previousFrame = now;
            if (warmup++ < 40) { tick(); return; }
            if (phase == 3 && population < 128) {
                for (int i = 0; i < 8; i++) spawn(population++);
                tick(); return;
            }
            if (follow == 0) {
                if (sample == 0) heapStart = heapBytes();
                long start = System.nanoTime();
                if (phase < 4) {
                    int count = phase == 2 ? 16 : 1;
                    for (int i = 0; i < count; i++) objects[i] = spawn(i);
                } else {
                    particle = engine.api().particles().spawn("Flame", -95, 3543);
                    if (phase == 5) particle.particles().setIgnoreAmbientLighting(true);
                }
                calls[sample] = System.nanoTime() - start;
            }
            long start = System.nanoTime();
            tick();
            peaks[sample] = Math.max(peaks[sample], System.nanoTime() - start);
            gaps[sample] = Math.max(gaps[sample], gap);
            if (++follow <= 3) return;
            if (phase < 4) {
                for (int i = 0; i < (phase == 2 ? 16 : 1); i++) objects[i].despawn();
            } else particle.remove();
            follow = 0;
            if (++sample < (phase == 0 ? 1 : 40)) return;
            String label = new String[]{"first", "repeated", "burst16", "populated128", "particles.no-setter", "particles.setter"}[phase];
            report(label + ".call.ns", calls);
            report(label + ".next4.peak.ns", peaks);
            report(label + ".raf-gap.ns", gaps);
            line(label + ".heap.delta=" + (heapBytes() - heapStart) + " (heap trend, not allocated bytes)");
            Arrays.fill(calls, 0); Arrays.fill(peaks, 0); Arrays.fill(gaps, 0);
            sample = 0;
            if (++phase == 6) { line("DONE"); done = true; }
        } catch (Throwable failure) { fail(failure); }
    }

    private GameObjectInstance spawn(int index) {
        return engine.api().gameObjects().spawn("snake", -256 + index * 96, 3383);
    }
    private void tick() { engine.update(1f / 60f); engine.render(); }
    private void report(String label, long[] values) {
        Arrays.sort(values, 0, sample);
        line(label + " n=" + sample + " p50=" + values[sample / 2]
                + " p95=" + values[(int) Math.ceil(sample * .95) - 1] + " max=" + values[sample - 1]);
    }
    private void line(String text) { output.setInnerText(output.getInnerText() + text + "\n"); }
    private void fail(Throwable failure) { line("FAILED " + failure); done = true; }
    private static native double heapBytes() /*-{
        return $wnd.performance && $wnd.performance.memory ? $wnd.performance.memory.usedJSHeapSize : -1;
    }-*/;
    @Override public void dispose() { if (engine != null) engine.dispose(); }
}
