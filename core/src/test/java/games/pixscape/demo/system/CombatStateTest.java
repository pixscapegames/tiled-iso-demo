package games.pixscape.demo.system;

import games.pixscape.demo.util.CombatState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatStateTest {

    @Test
    public void snakeHitTakesPriorityOverAPendingHeroAttack() {
        CombatState state = new CombatState();

        state.recordHeroAttackImpact();
        assertTrue(state.requestHeroHit());

        assertFalse(state.consumeHeroAttackImpact());
        assertTrue(state.consumeHeroHitRequest());
    }

    @Test
    public void eachSnakeAttackCycleCanRestartTheHeroHitAnimation() {
        CombatState state = new CombatState();

        assertTrue(state.requestHeroHit());
        assertFalse(state.requestHeroHit());
        assertTrue(state.consumeHeroHitRequest());

        state.setHeroHitActive(true);
        assertTrue(state.requestHeroHit());
        assertFalse(state.requestHeroHit());
        assertTrue(state.consumeHeroHitRequest());
        assertTrue(state.isHeroHitOrPending());
    }
}
