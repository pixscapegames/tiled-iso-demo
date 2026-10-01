package games.pixscape.demo.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.physics.box2d.Body;
import games.pixscape.demo.camera.CameraEffects;
import games.pixscape.demo.util.CombatState;
import games.pixscape.demo.util.SnakeTriggers;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.GameObjectInstance;
import games.pixscape.runtime.api.GameObjectsAPI;
import games.pixscape.runtime.api.ParticleRef;
import games.pixscape.runtime.api.PhysicsAPI;
import games.pixscape.runtime.api.PixscapeAPI;

/** Shows the demo snake once, then drives its small combat state machine. */
public final class EnemySystem extends BaseSystem {

    @FunctionalInterface
    public interface ScoreAward {
        void award(int points, float worldX, float worldY);
    }

    private enum SnakeState {
        HIDDEN,
        IDLE,
        ATTACKING,
        HIT,
        DEAD
    }

    private static final String HERO_NAME = "hero";
    private static final String SNAKE_GAME_OBJECT = "gameobjects/snake.gameobject";
    private static final String SNAKE_ACTOR_NAME = "snake_attack";
    private static final String SNAKE_IDLE_ANIMATION = "snake_idle";
    private static final String SNAKE_ATTACK_ANIMATION = "snake_attack";
    private static final String SNAKE_HIT_ANIMATION = "snake_hit";
    private static final String SNAKE_DEATH_ANIMATION = "snake_death";
    private static final String DEFAULT_CLIP = "default";
    private static final String SNAKE_ATTACK_CLIP = "attack_000";
    private static final String FLAME_EFFECT = "Flame";

    private static final int SNAKE_ATTACK_IMPACT_FRAME = 3;
    private static final int SNAKE_HITS_BEFORE_DEATH = 5;
    private static final float SNAKE_DEATH_FADE_SECONDS = 0.5f;
    private static final int SPATIAL_LAYER_INDEX = 4;
    private static final int SNAKE_Z_INDEX = 4;
    // The former snake prefab's actor was near (-96, 3543). The new
    // Game Object actor is local (160, 160) to its zero-positioned root.
    private static final float SNAKE_SPAWN_X = -256f;
    private static final float SNAKE_SPAWN_Y = 3383f;

    private static final float HIT_TINT_RED = 1f;
    private static final float HIT_TINT_GREEN = 0f;
    private static final float HIT_TINT_BLUE = 0f;
    private static final float OPAQUE = 1f;

    private final PixscapeAPI api;
    private final CombatState combatState;
    private final CameraEffects cameraEffects;
    private final ScoreAward scoreAwarded;
    private final Runnable snakeDied;
    private final SnakeTriggers triggers = new SnakeTriggers();

    private com.badlogic.gdx.physics.box2d.World listenerWorld;
    private GameObjectInstance snakeInstance;
    private EntityRef snake;
    private boolean snakeJustSpawned;
    private SnakeState state = SnakeState.HIDDEN;
    private int snakeHitCount;
    private int snakeAttackFrameCount;
    private boolean snakeAttackImpactHandled;
    private float deathFadeRemaining = -1f;


    public EnemySystem(PixscapeAPI api, CombatState combatState) {
        this(api, combatState, new CameraEffects());
    }

    public EnemySystem(
        PixscapeAPI api,
        CombatState combatState,
        CameraEffects cameraEffects
    ) {
        this(api, combatState, cameraEffects, (points, worldX, worldY) -> {});
    }

    public EnemySystem(
        PixscapeAPI api,
        CombatState combatState,
        CameraEffects cameraEffects,
        ScoreAward scoreAwarded
    ) {
        this(api, combatState, cameraEffects, scoreAwarded, () -> {});
    }

    public EnemySystem(
        PixscapeAPI api,
        CombatState combatState,
        CameraEffects cameraEffects,
        ScoreAward scoreAwarded,
        Runnable snakeDied
    ) {
        this.api = api;
        this.combatState = combatState;
        this.cameraEffects = cameraEffects;
        this.scoreAwarded = scoreAwarded;
        this.snakeDied = snakeDied;
    }

    @Override
    protected void processSystem() {
        bindToCurrentWorld();

        if (triggers.consumeAppearance() && !snakeExists()
            && (snakeInstance == null || !snakeInstance.exists())) {
            showActors();
        }

        if (snakeInstance != null && snake == null && snakeInstance.exists()) {
            if (snakeJustSpawned) {
                snakeJustSpawned = false;
            } else {
                snake = snakeInstance.requireMember(SNAKE_ACTOR_NAME);
                showSnake();
            }
        }

        if (!snakeExists()) {
            combatState.consumeHeroAttackImpact();
            return;
        }
        if (state == SnakeState.DEAD) {
            combatState.consumeHeroAttackImpact();
            updateDeathFade();
            return;
        }

        if (!triggers.isHeroInAttackRange()) {
            combatState.consumeHeroAttackImpact();
            if (state == SnakeState.ATTACKING || state == SnakeState.HIT) {
                idle();
            }
            return;
        }

        updateCombat();
    }

    private void updateCombat() {
        if (state == SnakeState.HIT) {
            // A hit reaction is exclusive: another hero impact is ignored until it ends.
            combatState.consumeHeroAttackImpact();
            if (snake.animation().isFinished()) {
                attack();
            }
            return;
        }

        if (state == SnakeState.IDLE) {
            attack();
        }

        if (state == SnakeState.ATTACKING) {
            updateAttackImpact();
        }

        if (combatState.consumeHeroAttackImpact()
            && !combatState.isHeroHitOrPending()) {
            hit();
        }
    }

    private void attack() {
        if (!snakeExists() || state == SnakeState.DEAD) return;

        restoreSnakeTint();
        snake.animation()
            .play(SNAKE_ATTACK_ANIMATION, SNAKE_ATTACK_CLIP)
            .setLoop(true);

        state = SnakeState.ATTACKING;
        snakeAttackImpactHandled = false;
    }

    private void updateAttackImpact() {
        if (snakeAttackFrameCount <= 0) {
            snakeAttackFrameCount = api.animations()
                .definition(SNAKE_ATTACK_ANIMATION)
                .frameCount();
        }

        boolean impactFrame = isAttackImpactFrame(
            snake.animation().frame(),
            snakeAttackFrameCount
        );

        if (impactFrame && !snakeAttackImpactHandled) {
            combatState.requestHeroHit();
        }

        snakeAttackImpactHandled = impactFrame;
    }

    private void hit() {
        if (!snakeExists() || state == SnakeState.DEAD) return;

        snakeHitCount++;
        Body hitBody = api.physics().body(snake);
        float worldX;
        float worldY;
        if (hitBody != null) {
            float pixelsPerMeter = api.physics().pixelsPerMeter();
            worldX = hitBody.getPosition().x * pixelsPerMeter;
            worldY = hitBody.getPosition().y * pixelsPerMeter;
        } else {
            worldX = snakeInstance.root().transform().x() + snake.transform().x();
            worldY = snakeInstance.root().transform().y() + snake.transform().y();
        }
        scoreAwarded.award(10, worldX, worldY);
        if (isFatalSnakeHit(snakeHitCount)) {
            die();
            return;
        }

        snake.animation()
            .play(SNAKE_HIT_ANIMATION, DEFAULT_CLIP)
            .setLoop(false);
        snake.sprite().setTint(
            HIT_TINT_RED,
            HIT_TINT_GREEN,
            HIT_TINT_BLUE,
            OPAQUE
        );

        state = SnakeState.HIT;
        snakeAttackImpactHandled = false;
    }

    private void idle() {
        if (!snakeExists() || state == SnakeState.DEAD) return;

        restoreSnakeTint();
        snake.animation()
            .play(SNAKE_IDLE_ANIMATION, DEFAULT_CLIP)
            .setLoop(true);

        state = SnakeState.IDLE;
        snakeAttackImpactHandled = false;
    }

    private void die() {
        if (!snakeExists() || state == SnakeState.DEAD) return;

        restoreSnakeTint();
        String deathClip = api.animations()
            .definition(SNAKE_DEATH_ANIMATION)
            .currentClip();
        snake.animation()
            .play(SNAKE_DEATH_ANIMATION, deathClip)
            .setLoop(false);

        state = SnakeState.DEAD;
        deathFadeRemaining = -1f;
        snakeAttackImpactHandled = false;
        cameraEffects.releaseCombat();

        Body body = api.physics().body(snake);
        if (body != null) {
            body.setLinearVelocity(0f, 0f);
            body.setAngularVelocity(0f);
            body.setActive(false);
        }
    }

    private void updateDeathFade() {
        if (deathFadeRemaining < 0f) {
            if (!snake.animation().isFinished()) return;
            deathFadeRemaining = SNAKE_DEATH_FADE_SECONDS;
            snakeDied.run();
            return;
        }
        if (deathFadeRemaining == 0f) return;

        deathFadeRemaining = Math.max(0f, deathFadeRemaining - world.getDelta());
        snake.sprite().setAlpha(deathFadeRemaining / SNAKE_DEATH_FADE_SECONDS);
        if (deathFadeRemaining == 0f) snake.sprite().setVisible(false);
    }

    private void showActors() {
        spawnFlame(-19f, 3500f);
        spawnFlame(-224f, 3630f);

        snakeInstance = spawnSnake(api.gameObjects());
        snakeJustSpawned = true;
        cameraEffects.focusCombat();
    }

    static GameObjectInstance spawnSnake(GameObjectsAPI gameObjects) {
        GameObjectInstance instance = gameObjects.spawn(
            SNAKE_GAME_OBJECT, SNAKE_SPAWN_X, SNAKE_SPAWN_Y);
        instance.root().renderOrder().set(SPATIAL_LAYER_INDEX, SNAKE_Z_INDEX);
        return instance;
    }

    private ParticleRef spawnFlame(float x, float y) {
        ParticleRef flame = api.particles().spawn(FLAME_EFFECT, x, y);
        flame.entity()
            .renderOrder()
            .layerIndex(SPATIAL_LAYER_INDEX);
        flame.loop(false);
        return flame;
    }

    private void showSnake() {
        snakeHitCount = 0;
        deathFadeRemaining = -1f;
        snake.sprite().setVisible(true);
        restoreSnakeTint();
        idle();

        Body body = api.physics().body(snake);
        if (body != null) {
            body.setActive(true);
        }
    }

    private void bindToCurrentWorld() {
        PhysicsAPI physics = api.physics();
        com.badlogic.gdx.physics.box2d.World activeWorld = physics.isRunning()
            ? physics.box2dWorld()
            : null;

        if (activeWorld == listenerWorld) return;

        listenerWorld = activeWorld;
        resetRuntimeState();

        if (listenerWorld != null) {
            int heroEntityId = api.entities()
                .requireName(HERO_NAME)
                .entityId();

            triggers.bindHero(heroEntityId);
            listenerWorld.setContactListener(triggers);
        }
    }

    private void resetRuntimeState() {
        snake = null;
        snakeInstance = null;
        snakeJustSpawned = false;
        state = SnakeState.HIDDEN;
        snakeHitCount = 0;
        snakeAttackFrameCount = 0;
        snakeAttackImpactHandled = false;
        deathFadeRemaining = -1f;
        cameraEffects.reset();
        combatState.reset();
        triggers.reset();
    }

    private boolean snakeExists() {
        return snake != null && snake.exists();
    }

    private void restoreSnakeTint() {
        if (!snakeExists()) return;
        snake.sprite().setTint(1f, 1f, 1f, OPAQUE);
    }

    static boolean isAttackImpactFrame(int frame, int frameCount) {
        return frameCount > SNAKE_ATTACK_IMPACT_FRAME
            && frame == SNAKE_ATTACK_IMPACT_FRAME;
    }

    static boolean isFatalSnakeHit(int receivedHitCount) {
        return receivedHitCount >= SNAKE_HITS_BEFORE_DEATH;
    }

}
