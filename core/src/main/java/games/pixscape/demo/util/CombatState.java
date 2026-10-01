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
    private int heroHitCount;
    private boolean heroDead;
    private boolean heroDeathPending;

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
        if (heroHitPending || heroDead) return false;

        heroHitPending = true;
        heroAttackImpactPending = false;
        return true;
    }

    public boolean consumeHeroHitRequest() {
        boolean pending = heroHitPending;
        heroHitPending = false;
        if (pending) heroHitCount++;
        return pending;
    }

    public int heroHitCount() {
        return heroHitCount;
    }

    public void markHeroDead() {
        if (heroDead) return;
        heroDead = true;
        heroDeathPending = true;
        heroHitPending = false;
        heroHitActive = false;
        heroAttackImpactPending = false;
    }

    public boolean consumeHeroDeath() {
        boolean pending = heroDeathPending;
        heroDeathPending = false;
        return pending;
    }

    public boolean isHeroDead() {
        return heroDead;
    }

    public void setHeroHitActive(boolean active) {
        heroHitActive = active;
    }

    public boolean isHeroHitOrPending() {
        return heroHitActive || heroHitPending || heroDead;
    }

    public void reset() {
        heroAttackImpactPending = false;
        heroHitPending = false;
        heroHitActive = false;
        heroHitCount = 0;
        heroDead = false;
        heroDeathPending = false;
    }
}
