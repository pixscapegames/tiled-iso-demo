package games.pixscape.demo.util;

/**
 * Small mailbox for combat signals exchanged by the hero and enemy systems.
 *
 * <p>The systems stay independent: they publish and consume gameplay events
 * instead of calling each other directly.</p>
 */
public final class CombatState {
    private boolean heroAttackImpactPending;
    private boolean heroHitPending;
    private boolean heroHitActive;

    public void recordHeroAttackImpact() {
        if (isHeroHitOrPending()) return;
        heroAttackImpactPending = true;
    }

    public boolean consumeHeroAttackImpact() {
        boolean pending = heroAttackImpactPending;
        heroAttackImpactPending = false;
        return pending;
    }

    public boolean requestHeroHit() {
        if (heroHitPending) return false;

        heroHitPending = true;
        heroAttackImpactPending = false;
        return true;
    }

    public boolean consumeHeroHitRequest() {
        boolean pending = heroHitPending;
        heroHitPending = false;
        return pending;
    }

    public void setHeroHitActive(boolean active) {
        heroHitActive = active;
    }

    public boolean isHeroHitOrPending() {
        return heroHitActive || heroHitPending;
    }

    public void reset() {
        heroAttackImpactPending = false;
        heroHitPending = false;
        heroHitActive = false;
    }
}
