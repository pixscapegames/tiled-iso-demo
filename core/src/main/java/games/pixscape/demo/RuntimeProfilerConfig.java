package games.pixscape.demo;

public final class RuntimeProfilerConfig {
    public static final RuntimeProfilerConfig DISABLED = new RuntimeProfilerConfig(false, 5f, 3f);

    public final boolean enabled;
    public final float windowSeconds;
    public final float warmupSeconds;

    public RuntimeProfilerConfig(boolean enabled, float windowSeconds, float warmupSeconds) {
        this.enabled = enabled;
        this.windowSeconds = positiveOrDefault(windowSeconds, 5f);
        this.warmupSeconds = nonNegativeOrDefault(warmupSeconds, 3f);
    }

    private static float positiveOrDefault(float value, float fallback) {
        return value > 0f && !Float.isNaN(value) && !Float.isInfinite(value) ? value : fallback;
    }

    private static float nonNegativeOrDefault(float value, float fallback) {
        return value >= 0f && !Float.isNaN(value) && !Float.isInfinite(value) ? value : fallback;
    }
}
