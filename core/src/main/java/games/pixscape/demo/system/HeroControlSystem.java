package games.pixscape.demo.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import games.pixscape.demo.input.HeroInput;
import games.pixscape.demo.input.KeyboardHeroInput;
import games.pixscape.demo.util.CombatState;
import games.pixscape.runtime.api.AnimationDefinition;
import games.pixscape.runtime.api.AnimationFacade;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.ParticleRef;
import games.pixscape.runtime.api.PhysicsAPI;
import games.pixscape.runtime.api.PixscapeAPI;

/** Handles the small amount of hero gameplay needed by the demo. */
public final class HeroControlSystem extends BaseSystem {

    private enum HeroState {
        FREE,
        ATTACKING,
        HIT
    }

    private static final String HERO_NAME = "hero";
    private static final String HERO_RUN_ANIMATION = "hero_run";
    private static final String HERO_IDLE_ANIMATION = "hero_idle";
    private static final String HERO_ATTACK_ANIMATION = "hero_attack";
    private static final String HERO_HIT_ANIMATION = "hero_hit";
    private static final String EFFECTIVE_ATTACK_CLIP = "attack_090";
    private static final String BLOOD_EFFECT = "Blood";

    private static final float CAMERA_DT_MAX = 1f / 30f;
    private static final float MOVE_ACCELERATION_PIXELS = 450f;
    private static final float MAX_SPEED_PIXELS = 300f;
    private static final float IDLE_DAMPING_PER_SECOND = 8f;
    private static final float ANIMATION_PIXELS_PER_FRAME = 15f;
    private static final float ANIMATION_STOP_SPEED_PIXELS = 5f;

    private static final int HERO_ATTACK_IMPACT_FRAME = 9;
    private static final int PHYSICS_LAYER_INDEX = 5;

    private static final String[] RUN_CLIPS = {
        "run_000",
        "run_090",
        "run_180",
        "run_270"
    };

    private final PixscapeAPI api;
    private final CombatState combatState;
    private final HeroInput input;

    private EntityRef hero;
    private ParticleRef bloodEffect;
    private HeroState state = HeroState.FREE;
    private boolean attackImpactTriggered;

    public HeroControlSystem() {
        this(null, new CombatState(), new KeyboardHeroInput());
    }

    public HeroControlSystem(PixscapeAPI api, CombatState combatState) {
        this(api, combatState, new KeyboardHeroInput());
    }

    public HeroControlSystem(
        PixscapeAPI api,
        CombatState combatState,
        HeroInput input
    ) {
        this.api = api;
        this.combatState = combatState;
        this.input = input;
    }

    @Override
    protected void processSystem() {
        if (api == null) return;

        PhysicsAPI physics = api.physics();
        if (!physics.isRunning()) return;

        if (hero == null || !hero.exists()) {
            hero = api.entities().requireName(HERO_NAME);
            bloodEffect = null;
            state = HeroState.FREE;
            attackImpactTriggered = false;
            combatState.setHeroHitActive(false);
        }

        Body body = physics.body(hero);
        if (body == null) return;

        float pixelsPerMeter = physics.pixelsPerMeter();
        ensureBloodEffect(body, pixelsPerMeter);
        updateHero(body, hero.animation(), pixelsPerMeter, world.getDelta());
    }

    private void updateHero(
        Body body,
        AnimationFacade animation,
        float pixelsPerMeter,
        float dt
    ) {
        if (body.getType() != BodyDef.BodyType.DynamicBody) return;

        float dx = input.moveX();
        float dy = input.moveY();
        boolean attackPressed = input.attackJustPressed();
        boolean hasInput = dx != 0f || dy != 0f;

        if (hasInput) {
            float invLen = 1f / (float) Math.sqrt(dx * dx + dy * dy);
            dx *= invLen;
            dy *= invLen;
        } else {
            dampVelocity(body, dt);
        }

        if (combatState.consumeHeroHitRequest()) {
            enterHit(body, animation, pixelsPerMeter);
        }

        boolean recoveredFromHit = updateFinishedAnimations(animation);
        publishHeroAttackImpact(animation);

        boolean hitCanBeCancelled = state == HeroState.HIT
            && canCancelHitForAttack(
                animation.stateTime(),
                animation.fps(),
                heroHitClipFrameCount()
            );

        if (state == HeroState.HIT && !hitCanBeCancelled) {
            body.setLinearVelocity(0f, 0f);
        } else if (shouldCancelHitForMovement(
            state == HeroState.HIT,
            hitCanBeCancelled,
            hasInput
        )) {
            leaveHit();
        }

        if (shouldStartAttack(
            state == HeroState.ATTACKING
                || (state == HeroState.HIT && !hitCanBeCancelled)
                || recoveredFromHit,
            attackPressed
        )) {
            enterAttack(animation, dx, dy, hasInput);
        }

        if (state == HeroState.FREE && !recoveredFromHit) {
            updateLocomotionAnimation(
                body,
                animation,
                pixelsPerMeter,
                dt,
                dx,
                dy,
                hasInput
            );
        }

        if (!hasInput || (state == HeroState.HIT && !hitCanBeCancelled)) return;

        clampVelocity(body, pixelsPerMeter);
        float force = body.getMass() * MOVE_ACCELERATION_PIXELS / pixelsPerMeter;
        body.applyForceToCenter(dx * force, dy * force, true);
    }

    private void enterHit(
        Body body,
        AnimationFacade animation,
        float pixelsPerMeter
    ) {
        state = HeroState.FREE;
        attackImpactTriggered = false;

        if (!startHit(animation, hitClipFor(animation.clip()))) {
            combatState.setHeroHitActive(false);
            return;
        }

        state = HeroState.HIT;
        combatState.setHeroHitActive(true);
        restartBloodEffect(body, pixelsPerMeter);
    }

    private boolean updateFinishedAnimations(AnimationFacade animation) {
        if (!animation.isFinished()) return false;

        if (state == HeroState.HIT) {
            leaveHit();
            animation.play(HERO_IDLE_ANIMATION, idleClipFor(animation.clip()))
                .setLoop(true);
            return true;
        }

        if (state == HeroState.ATTACKING) {
            state = HeroState.FREE;
            attackImpactTriggered = false;
            animation.setLoop(true);
        }

        return false;
    }

    private void enterAttack(
        AnimationFacade animation,
        float dx,
        float dy,
        boolean hasInput
    ) {
        String directionalClip = hasInput
            ? runClipForDirection(dx, dy)
            : animation.clip();
        String attackClip = attackClipFor(directionalClip);

        if (!startAttack(animation, HERO_ATTACK_ANIMATION, attackClip)) return;

        if (state == HeroState.HIT) {
            combatState.setHeroHitActive(false);
        }

        state = HeroState.ATTACKING;
        attackImpactTriggered = false;
    }

    private void leaveHit() {
        if (state != HeroState.HIT) return;
        state = HeroState.FREE;
        combatState.setHeroHitActive(false);
    }

    private void updateLocomotionAnimation(
        Body body,
        AnimationFacade animation,
        float pixelsPerMeter,
        float dt,
        float dx,
        float dy,
        boolean hasInput
    ) {
        if (hasInput) {
            setLoopingAnimation(
                animation,
                HERO_RUN_ANIMATION,
                runClipForDirection(dx, dy)
            );
        }

        updateAnimationSpeed(body, animation, pixelsPerMeter, dt, hasInput);
    }

    private void publishHeroAttackImpact(AnimationFacade animation) {
        if (state != HeroState.ATTACKING || attackImpactTriggered) return;
        if (!isEffectiveAttackDirection(animation.clip())) return;
        if (!hasReachedHeroAttackImpactFrame(animation.stateTime(), animation.fps())) return;

        attackImpactTriggered = true;
        combatState.recordHeroAttackImpact();
    }

    private static void dampVelocity(Body body, float dt) {
        float safeDt = Math.min(dt, CAMERA_DT_MAX);
        Vector2 velocity = body.getLinearVelocity();
        float damping = (float) Math.exp(-IDLE_DAMPING_PER_SECOND * safeDt);
        velocity.scl(damping);

        if (velocity.len2() < 0.0001f) {
            velocity.setZero();
        }

        body.setLinearVelocity(velocity);
    }

    private static void clampVelocity(Body body, float pixelsPerMeter) {
        Vector2 velocity = body.getLinearVelocity();
        float maxSpeed = MAX_SPEED_PIXELS / pixelsPerMeter;
        if (velocity.len2() > maxSpeed * maxSpeed) {
            body.setLinearVelocity(velocity.nor().scl(maxSpeed));
        }
    }

    private void updateAnimationSpeed(
        Body body,
        AnimationFacade animation,
        float pixelsPerMeter,
        float dt,
        boolean hasInput
    ) {
        if (animation == null || !animation.exists()) return;

        Vector2 velocity = body.getLinearVelocity();
        float speedPixels = velocity.len() * pixelsPerMeter;

        if (!hasInput && speedPixels < ANIMATION_STOP_SPEED_PIXELS) {
            setLoopingAnimation(
                animation,
                HERO_IDLE_ANIMATION,
                idleClipFor(animation.clip())
            );
            return;
        }

        if (speedPixels >= ANIMATION_STOP_SPEED_PIXELS) {
            setLoopingAnimation(
                animation,
                HERO_RUN_ANIMATION,
                directionalClipFor("run", animation.clip())
            );
        }

        float safeDt = Math.min(dt, CAMERA_DT_MAX);
        float authoredFps = animation.fps();
        if (authoredFps <= 0f) return;

        animation.setStateTime(
            animation.stateTime()
                + speedPixels * safeDt / (ANIMATION_PIXELS_PER_FRAME * authoredFps)
                - safeDt
        );
    }

    static void setAnimationClip(AnimationFacade animation, String clipName) {
        if (animation == null || !animation.exists() || !animation.hasClip(clipName)) return;
        if (clipName.equals(animation.clip())) return;

        float stateTime = animation.stateTime();
        animation.play(clipName).setStateTime(stateTime);
    }

    static void setAnimation(
        AnimationFacade animation,
        String animationName,
        String clipName
    ) {
        if (animation == null || !animation.exists()) return;

        if (!animation.hasClip(clipName)) {
            animation.play(animationName, clipName);
            return;
        }

        setAnimationClip(animation, clipName);
    }

    static void setLoopingAnimation(
        AnimationFacade animation,
        String animationName,
        String clipName
    ) {
        setAnimation(animation, animationName, clipName);
        if (animation != null && animation.exists()) {
            animation.setLoop(true);
        }
    }

    static boolean startAttack(
        AnimationFacade animation,
        String animationName,
        String clipName
    ) {
        if (animation == null || !animation.exists()) return false;

        animation.play(animationName, clipName).setLoop(false);
        return true;
    }

    static boolean startHit(AnimationFacade animation, String clipName) {
        if (animation == null || !animation.exists()) return false;

        animation.play(HERO_HIT_ANIMATION, clipName).setLoop(false);
        return true;
    }

    static boolean shouldStartAttack(boolean attacking, boolean attackKeyJustPressed) {
        return !attacking && attackKeyJustPressed;
    }

    static String idleClipFor(String directionalClip) {
        return directionalClipFor("idle", directionalClip);
    }

    static String attackClipFor(String directionalClip) {
        return directionalClipFor("attack", directionalClip);
    }

    static String hitClipFor(String directionalClip) {
        return directionalClipFor("hit", directionalClip);
    }

    static boolean isEffectiveAttackDirection(String attackClip) {
        return EFFECTIVE_ATTACK_CLIP.equals(attackClip);
    }

    static boolean hasReachedHeroAttackImpactFrame(float stateTime, float fps) {
        return stateTime >= 0f
            && fps > 0f
            && stateTime * fps >= HERO_ATTACK_IMPACT_FRAME;
    }

    static boolean canCancelHitForAttack(float stateTime, float fps, int frameCount) {
        if (stateTime < 0f || fps <= 0f || frameCount <= 0) return false;
        return stateTime >= frameCount / (2f * fps);
    }

    static boolean shouldCancelHitForMovement(
        boolean hit,
        boolean hitCanBeCancelled,
        boolean hasMovementInput
    ) {
        return hit && hitCanBeCancelled && hasMovementInput;
    }

    private int heroHitClipFrameCount() {
        AnimationDefinition definition = api.animations().definition(HERO_HIT_ANIMATION);
        int clipCount = definition.clipCount();
        return clipCount > 0 ? definition.frameCount() / clipCount : 0;
    }

    private void ensureBloodEffect(Body body, float pixelsPerMeter) {
        if (bloodEffect != null && bloodEffect.entity().exists()) return;

        bloodEffect = api.particles().spawn(
            BLOOD_EFFECT,
            body.getPosition().x * pixelsPerMeter,
            body.getPosition().y * pixelsPerMeter
        );
        bloodEffect.entity()
            .renderOrder()
            .layerIndex(PHYSICS_LAYER_INDEX);
        bloodEffect.loop(false).stop();
    }

    private void restartBloodEffect(Body body, float pixelsPerMeter) {
        ensureBloodEffect(body, pixelsPerMeter);
        bloodEffect.transform().setPosition(
            body.getPosition().x * pixelsPerMeter,
            body.getPosition().y * pixelsPerMeter
        );
        bloodEffect.restart();
    }

    private static String directionalClipFor(String animation, String directionalClip) {
        if (directionalClip != null) {
            int separator = directionalClip.lastIndexOf('_');
            if (separator >= 0 && separator + 1 < directionalClip.length()) {
                return animation + "_" + directionalClip.substring(separator + 1);
            }
        }
        return animation + "_000";
    }

    static String runClipForDirection(float dx, float dy) {
        float degrees = (float) Math.toDegrees(Math.atan2(dx, dy));
        if (degrees < 0f) {
            degrees += 360f;
        }
        int sector = (int) Math.floor((degrees + 45f) / 90f) % RUN_CLIPS.length;
        return RUN_CLIPS[sector];
    }
}
