package games.pixscape.demo.system;

import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.GameObjectInstance;
import games.pixscape.runtime.api.GameObjectsAPI;
import games.pixscape.runtime.api.RenderOrderFacade;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;

public class EnemySystemGameObjectTest {

    @Test
    public void spawnsAvailableSnakeWithoutAnyScenePlacement() {
        String[] requestedAsset = new String[1];
        float[] requestedPosition = new float[2];
        int[] requestedOrder = new int[2];
        int[] spawnCount = new int[1];

        RenderOrderFacade order = (RenderOrderFacade) Proxy.newProxyInstance(
            RenderOrderFacade.class.getClassLoader(),
            new Class<?>[]{RenderOrderFacade.class},
            (proxy, method, args) -> {
                if ("set".equals(method.getName())) {
                    requestedOrder[0] = (Integer) args[0];
                    requestedOrder[1] = (Integer) args[1];
                    return proxy;
                }
                throw new AssertionError("Unexpected render-order call: " + method.getName());
            });
        EntityRef root = (EntityRef) Proxy.newProxyInstance(
            EntityRef.class.getClassLoader(),
            new Class<?>[]{EntityRef.class},
            (proxy, method, args) -> {
                if ("renderOrder".equals(method.getName())) return order;
                throw new AssertionError("Unexpected root call: " + method.getName());
            });
        GameObjectInstance instance = new GameObjectInstance() {
            @Override public EntityRef root() { return root; }
            @Override public EntityRef[] members() { return new EntityRef[0]; }
            @Override public EntityRef requireMember(String name) {
                throw new AssertionError("Member resolution is deferred until the next frame");
            }
            @Override public boolean exists() { return true; }
            @Override public void despawn() { }
        };
        GameObjectsAPI gameObjects = new GameObjectsAPI() {
            @Override
            public GameObjectInstance spawn(String name, float x, float y) {
                spawnCount[0]++;
                requestedAsset[0] = name;
                requestedPosition[0] = x;
                requestedPosition[1] = y;
                return instance;
            }

        };

        Assert.assertSame(instance, EnemySystem.spawnSnake(gameObjects));
        Assert.assertEquals(1, spawnCount[0]);
        Assert.assertEquals("gameobjects/snake.gameobject", requestedAsset[0]);
        Assert.assertEquals(-256f, requestedPosition[0], 0f);
        Assert.assertEquals(3383f, requestedPosition[1], 0f);
        Assert.assertArrayEquals(new int[]{4, 4}, requestedOrder);
    }
}
