package games.pixscape.demo.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeyboardHeroInputTest {
    @Test
    public void focusedHudSuppressesPolledMovementAndAttack() {
        Input previous = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(
                Input.class.getClassLoader(), new Class<?>[] {Input.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isKeyPressed")) {
                        int key = (Integer) args[0];
                        return key == Input.Keys.LEFT || key == Input.Keys.UP;
                    }
                    if (method.getName().equals("isKeyJustPressed")) return true;
                    throw new UnsupportedOperationException(method.getName());
                });
        try {
            boolean[] focused = {false};
            KeyboardHeroInput hero = new KeyboardHeroInput(() -> focused[0]);
            assertEquals(-1f, hero.moveX(), 0f);
            assertEquals(1f, hero.moveY(), 0f);
            assertTrue(hero.attackJustPressed());

            focused[0] = true;
            assertEquals(0f, hero.moveX(), 0f);
            assertEquals(0f, hero.moveY(), 0f);
            assertFalse(hero.attackJustPressed());
        } finally {
            Gdx.input = previous;
        }
    }
}
