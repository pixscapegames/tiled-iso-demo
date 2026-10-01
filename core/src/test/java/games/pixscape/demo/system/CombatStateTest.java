package games.pixscape.demo.system;

import games.pixscape.demo.util.CombatState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
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

    @Test
    public void countsConsumedHitsAndLocksCombatAfterDeathUntilReset() {
        CombatState state = new CombatState();
        for (int hit = 1; hit <= 7; hit++) {
            assertTrue(state.requestHeroHit());
            assertTrue(state.consumeHeroHitRequest());
            assertEquals(hit, state.heroHitCount());
        }

        state.markHeroDead();
        assertTrue(state.consumeHeroDeath());
        assertFalse(state.consumeHeroDeath());
        assertTrue(state.isHeroDead());
        assertFalse(state.requestHeroHit());
        state.recordHeroAttackImpact();
        assertFalse(state.consumeHeroAttackImpact());

        state.reset();
        assertEquals(0, state.heroHitCount());
        assertFalse(state.isHeroDead());
        assertTrue(state.requestHeroHit());
    }
}
