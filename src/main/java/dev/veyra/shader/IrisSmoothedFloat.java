package dev.veyra.shader;

/**
 * Iris' SmoothedFloat / SmoothFloat: basic exponential smoothing whose half-lives are given in tenths
 * of a second. The first value is taken unsmoothed, and a zero half-life follows the value immediately.
 */
public final class IrisSmoothedFloat {
    private final double halfLifeUp;
    private final double halfLifeDown;
    private boolean initialized;
    private double value;

    public IrisSmoothedFloat(double halfLifeUp, double halfLifeDown) {
        this.halfLifeUp = halfLifeUp;
        this.halfLifeDown = halfLifeDown;
    }

    /** Smooths {@code target} over {@code frameTimeSeconds} with the configured half-lives. */
    public double update(double target, double frameTimeSeconds) {
        return update(target, frameTimeSeconds, halfLifeUp, halfLifeDown);
    }

    /** Smooths with half-lives supplied per call, as custom-uniform smooth() evaluates its arguments. */
    public double update(double target, double frameTimeSeconds, double halfLifeUp, double halfLifeDown) {
        if (!initialized) {
            initialized = true;
            value = target;
            return value;
        }
        double halfLifeSeconds = (target > value ? halfLifeUp : halfLifeDown) * 0.1;
        if (halfLifeSeconds <= 0.0) {
            value = target;
        } else if (frameTimeSeconds > 0.0) {
            double decay = Math.log(2.0) / halfLifeSeconds;
            value += (target - value) * (1.0 - Math.exp(-decay * frameTimeSeconds));
        }
        return value;
    }

    public double value() {
        return value;
    }

    public void reset() {
        initialized = false;
        value = 0.0;
    }
}
