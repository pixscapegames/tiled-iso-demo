package games.pixscape.demo;

import games.pixscape.runtime.api.TransformFacade;

/** Moves an existing light's ground projection before World processing. */
final class LightTravel {
    private static final double PERIOD_SECONDS = 30.0;
    private final TransformFacade transform;
    private final float startX, startY, endX, endY;
    private double elapsed;

    LightTravel(TransformFacade transform, float endX, float endY) {
        this.transform = transform;
        this.startX = transform.x();
        this.startY = transform.y();
        this.endX = endX;
        this.endY = endY;
    }

    void update(double deltaSeconds) {
        elapsed = (elapsed + deltaSeconds) % PERIOD_SECONDS;
        double t = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * elapsed / PERIOD_SECONDS));
        transform.setPosition((float) (startX + t * (endX - startX)),
            (float) (startY + t * (endY - startY)));
    }
}
