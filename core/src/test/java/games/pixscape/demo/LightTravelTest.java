package games.pixscape.demo;

import games.pixscape.runtime.api.TransformFacade;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LightTravelTest {
    private final float[] position = {100f, 200f};
    private final TransformFacade transform = (TransformFacade) Proxy.newProxyInstance(
        TransformFacade.class.getClassLoader(), new Class<?>[]{TransformFacade.class},
        (proxy, method, args) -> {
            switch (method.getName()) {
                case "x": return position[0];
                case "y": return position[1];
                case "setPosition":
                    position[0] = (float) args[0];
                    position[1] = (float) args[1];
                    return proxy;
                default: throw new AssertionError("Unexpected light mutation: " + method.getName());
            }
        });

    @Test public void startsInPlaceReachesTreeAndReturnsEveryThirtySeconds() {
        LightTravel travel = new LightTravel(transform, -100f, 400f);
        travel.update(0);
        at(100f, 200f);
        travel.update(7.5);
        at(0f, 300f);
        travel.update(7.5);
        at(-100f, 400f);
        travel.update(15);
        at(100f, 200f);
        travel.update(45);
        at(-100f, 400f);
    }

    @Test public void irregularFramesAndLongFramesKeepTheSameTiming() {
        LightTravel travel = new LightTravel(transform, -100f, 400f);
        for (int i = 0; i < 100; i++) {
            travel.update(0.01);
            travel.update(0.04);
            travel.update(0.10);
        }
        at(-100f, 400f);
        travel.update(10);
        travel.update(5);
        at(100f, 200f);
    }

    private void at(float x, float y) {
        assertEquals(x, position[0], 0.001f);
        assertEquals(y, position[1], 0.001f);
    }
}
