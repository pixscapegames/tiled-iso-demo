package games.pixscape.demo;

import com.badlogic.gdx.utils.LongArray;
import games.pixscape.runtime.profiling.FrameSystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilePhases;

import java.util.Arrays;

final class RuntimeProfilerReporter {
    private static final int SYSTEM_REPORT_LIMIT = 5;

    private final FrameSystemProfiler profiler;
    private final RuntimeProfilerConfig config;
    private final WindowSamples samples = new WindowSamples();

    private float warmupRemainingSeconds;
    private float windowElapsedSeconds;

    RuntimeProfilerReporter(FrameSystemProfiler profiler, RuntimeProfilerConfig config) {
        this.profiler = profiler;
        this.config = config;
        this.warmupRemainingSeconds = config.warmupSeconds;
    }

    void afterFrame(float deltaSeconds, long appFrameNs, long engineUpdateNs, long engineRenderNs) {
        if (profiler == null || !profiler.enabled()) {
            return;
        }

        float safeDelta = Math.max(0f, deltaSeconds);
        if (warmupRemainingSeconds > 0f) {
            warmupRemainingSeconds -= safeDelta;
            profiler.beginFrame();
            return;
        }

        samples.add(appFrameNs, engineUpdateNs, engineRenderNs, profiler);
        windowElapsedSeconds += safeDelta;

        if (windowElapsedSeconds >= config.windowSeconds && samples.frames > 0) {
            System.out.println(samples.report(windowElapsedSeconds));
            samples.clear();
            windowElapsedSeconds = 0f;
        }
    }

    private static final class WindowSamples {
        private final LongArray appFrameNs = new LongArray(false, 512);
        private final LongArray engineUpdateNs = new LongArray(false, 512);
        private final LongArray engineRenderNs = new LongArray(false, 512);
        private final LongArray profiledSystemsTotalNs = new LongArray(false, 512);
        private final LongArray unprofiledRemainderNs = new LongArray(false, 512);
        private final LongArray[] systemNs = new LongArray[SystemProfilePhases.PHASE_COUNT];
        private int frames;

        WindowSamples() {
            for (int i = 0; i < systemNs.length; i++) {
                systemNs[i] = new LongArray(false, 512);
            }
        }

        void add(long appFrame, long engineUpdate, long engineRender, FrameSystemProfiler profiler) {
            frames++;
            appFrameNs.add(Math.max(0L, appFrame));
            engineUpdateNs.add(Math.max(0L, engineUpdate));
            engineRenderNs.add(Math.max(0L, engineRender));
            profiledSystemsTotalNs.add(profiler.totalNs());
            unprofiledRemainderNs.add(profiler.unprofiledRemainderNs(engineRender));
            for (int i = 0; i < systemNs.length; i++) {
                systemNs[i].add(profiler.durationNs(i));
            }
        }

        String report(float elapsedSeconds) {
            StringBuilder out = new StringBuilder(1024);
            out.append("[RuntimeProfiler] window ")
                    .append(oneDecimal(elapsedSeconds))
                    .append("s frames=")
                    .append(frames)
                    .append('\n');
            appendStats(out, "app.frame", appFrameNs);
            appendStats(out, "engine.update", engineUpdateNs);
            appendStats(out, "engine.render", engineRenderNs);
            appendStats(out, "profiledSystemsTotal", profiledSystemsTotalNs);
            appendStats(out, "unprofiledRemainder", unprofiledRemainderNs);
            appendSystems(out, "worst avg systems:", true);
            appendSystems(out, "worst max systems:", false);
            return out.toString();
        }

        void clear() {
            frames = 0;
            appFrameNs.clear();
            engineUpdateNs.clear();
            engineRenderNs.clear();
            profiledSystemsTotalNs.clear();
            unprofiledRemainderNs.clear();
            for (LongArray system : systemNs) {
                system.clear();
            }
        }

        private void appendSystems(StringBuilder out, String heading, boolean byAverage) {
            int[] top = topSystems(byAverage);
            out.append("  ").append(heading).append('\n');
            boolean any = false;
            for (int phaseId : top) {
                if (phaseId < 0) continue;
                any = true;
                out.append("    ").append(SystemProfilePhases.name(phaseId));
                appendInlineStats(out, systemNs[phaseId]);
                out.append('\n');
            }
            if (!any) {
                out.append("    none\n");
            }
        }

        private int[] topSystems(boolean byAverage) {
            int[] top = new int[SYSTEM_REPORT_LIMIT];
            long[] scores = new long[SYSTEM_REPORT_LIMIT];
            Arrays.fill(top, -1);

            for (int phaseId = 0; phaseId < systemNs.length; phaseId++) {
                LongArray values = systemNs[phaseId];
                long score = byAverage ? average(values) : max(values);
                if (score <= 0L) continue;

                for (int slot = 0; slot < top.length; slot++) {
                    if (score <= scores[slot]) continue;

                    for (int move = top.length - 1; move > slot; move--) {
                        top[move] = top[move - 1];
                        scores[move] = scores[move - 1];
                    }
                    top[slot] = phaseId;
                    scores[slot] = score;
                    break;
                }
            }
            return top;
        }

        private static void appendStats(StringBuilder out, String label, LongArray values) {
            out.append("  ").append(label);
            appendInlineStats(out, values);
            out.append('\n');
        }

        private static void appendInlineStats(StringBuilder out, LongArray values) {
            out.append(" avg=").append(FrameSystemProfiler.formatMs(average(values))).append("ms")
                    .append(" p95=").append(FrameSystemProfiler.formatMs(percentile(values, 0.95f))).append("ms")
                    .append(" p99=").append(FrameSystemProfiler.formatMs(percentile(values, 0.99f))).append("ms")
                    .append(" max=").append(FrameSystemProfiler.formatMs(max(values))).append("ms");
        }

        private static long average(LongArray values) {
            if (values.size == 0) return 0L;
            long total = 0L;
            for (int i = 0; i < values.size; i++) {
                total += values.get(i);
            }
            return total / values.size;
        }

        private static long max(LongArray values) {
            long max = 0L;
            for (int i = 0; i < values.size; i++) {
                max = Math.max(max, values.get(i));
            }
            return max;
        }

        private static long percentile(LongArray values, float percentile) {
            if (values.size == 0) return 0L;
            long[] sorted = new long[values.size];
            for (int i = 0; i < values.size; i++) {
                sorted[i] = values.get(i);
            }
            Arrays.sort(sorted);
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            index = Math.max(0, Math.min(sorted.length - 1, index));
            return sorted[index];
        }

        private static String oneDecimal(float value) {
            int tenths = Math.round(value * 10f);
            return (tenths / 10) + "." + Math.abs(tenths % 10);
        }
    }
}
