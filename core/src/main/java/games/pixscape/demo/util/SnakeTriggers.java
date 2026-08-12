package games.pixscape.demo.util;

import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;

/** Tracks the demo snake sensors and hides the Box2D contact-filtering details. */
public final class SnakeTriggers implements ContactListener {

    private static final short HERO_FOOTPRINT_CATEGORY = 1 << 1;     // 2
    private static final short SNAKE_APPEARANCE_CATEGORY = 1 << 2;  // 4
    private static final short SNAKE_ATTACK_CATEGORY = 1 << 3;      // 8
    private static final short SNAKE_SENSOR_MASK = HERO_FOOTPRINT_CATEGORY;

    private int heroEntityId = -1;
    private int attackSensorContacts;
    private boolean appearanceTriggered;
    private boolean appearancePending;

    public void bindHero(int heroEntityId) {
        this.heroEntityId = heroEntityId;
    }

    @Override
    public void beginContact(Contact contact) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        if (!appearanceTriggered
            && isSensorContact(fixtureA, fixtureB, SNAKE_APPEARANCE_CATEGORY)) {
            appearanceTriggered = true;
            appearancePending = true;
        }

        if (isSensorContact(fixtureA, fixtureB, SNAKE_ATTACK_CATEGORY)) {
            attackSensorContacts++;
        }
    }

    @Override
    public void endContact(Contact contact) {
        if (attackSensorContacts <= 0) return;

        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        if (isSensorContact(fixtureA, fixtureB, SNAKE_ATTACK_CATEGORY)) {
            attackSensorContacts--;
        }
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
    }

    public boolean consumeAppearance() {
        if (!appearancePending) return false;

        appearancePending = false;
        return true;
    }

    public boolean isHeroInAttackRange() {
        return attackSensorContacts > 0;
    }

    public void reset() {
        heroEntityId = -1;
        attackSensorContacts = 0;
        appearanceTriggered = false;
        appearancePending = false;
    }

    private boolean isSensorContact(
        Fixture fixtureA,
        Fixture fixtureB,
        short sensorCategory
    ) {
        return isSensorHeroPair(fixtureA, fixtureB, sensorCategory)
            || isSensorHeroPair(fixtureB, fixtureA, sensorCategory);
    }

    private boolean isSensorHeroPair(
        Fixture sensorFixture,
        Fixture heroFixture,
        short sensorCategory
    ) {
        return isTrigger(sensorFixture, sensorCategory)
            && isHeroFootprint(heroFixture, sensorCategory);
    }

    private static boolean isTrigger(Fixture fixture, short expectedCategory) {
        return fixture.isSensor()
            && fixture.getFilterData().categoryBits == expectedCategory
            && fixture.getFilterData().maskBits == SNAKE_SENSOR_MASK;
    }

    private boolean isHeroFootprint(Fixture fixture, short sensorCategory) {
        Object userData = fixture.getBody().getUserData();
        boolean hero = userData instanceof Integer && (Integer) userData == heroEntityId;

        return hero
            && fixture.getFilterData().categoryBits == HERO_FOOTPRINT_CATEGORY
            && (fixture.getFilterData().maskBits & sensorCategory) != 0;
    }
}
