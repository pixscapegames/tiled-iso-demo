package games.pixscape.demo.system;

import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.api.AnimationFacade;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeroControlSystemTest {
    @Test
    public void isPublicFinalAndHasNoCameraState() {
        int modifiers = HeroControlSystem.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));

        for (Field field : HeroControlSystem.class.getDeclaredFields()) {
            assertFalse(OrthographicCamera.class.isAssignableFrom(field.getType()));
        }
    }

    @Test
    public void canBeConstructedWithoutCameraDependency() {
        new HeroControlSystem();
    }

    @Test
    public void mapsCardinalDirectionsToTheirRunClips() {
        assertEquals("run_000", HeroControlSystem.runClipForDirection(0f, 1f));
        assertEquals("run_090", HeroControlSystem.runClipForDirection(1f, 0f));
        assertEquals("run_180", HeroControlSystem.runClipForDirection(0f, -1f));
        assertEquals("run_270", HeroControlSystem.runClipForDirection(-1f, 0f));
    }

    @Test
    public void mapsDiagonalDirectionsToTheNearestCardinalRunClips() {
        assertEquals("run_090", HeroControlSystem.runClipForDirection(1f, 1f));
        assertEquals("run_180", HeroControlSystem.runClipForDirection(1f, -1f));
        assertEquals("run_270", HeroControlSystem.runClipForDirection(-1f, -1f));
        assertEquals("run_000", HeroControlSystem.runClipForDirection(-1f, 1f));
    }

    @Test
    public void changesAndPlaysTheRequestedClipWhilePreservingItsPhase() {
        RecordingAnimation animation = new RecordingAnimation("run_000", 4.25f);

        HeroControlSystem.setAnimationClip(animation, "run_090");

        assertEquals("run_090", animation.clip());
        assertEquals(4.25f, animation.stateTime(), 0f);
        assertTrue(animation.isPlaying());
    }

    @Test
    public void mapsDirectionalRunClipsToIdleClips() {
        assertEquals("idle_000", HeroControlSystem.idleClipFor("run_000"));
        assertEquals("idle_090", HeroControlSystem.idleClipFor("run_090"));
        assertEquals("idle_180", HeroControlSystem.idleClipFor("run_180"));
        assertEquals("idle_270", HeroControlSystem.idleClipFor("run_270"));
    }

    @Test
    public void mapsDirectionalLocomotionClipsToAttackClips() {
        assertEquals("attack_000", HeroControlSystem.attackClipFor("idle_000"));
        assertEquals("attack_090", HeroControlSystem.attackClipFor("run_090"));
        assertEquals("attack_180", HeroControlSystem.attackClipFor("idle_180"));
        assertEquals("attack_270", HeroControlSystem.attackClipFor("run_270"));
    }

    @Test
    public void mapsEveryDirectionalClipToTheMatchingHitClip() {
        assertEquals("hit_000", HeroControlSystem.hitClipFor("attack_000"));
        assertEquals("hit_090", HeroControlSystem.hitClipFor("run_090"));
        assertEquals("hit_180", HeroControlSystem.hitClipFor("idle_180"));
        assertEquals("hit_270", HeroControlSystem.hitClipFor("hit_270"));
    }

    @Test
    public void onlyTheNinetyDegreeAttackCanHitTheSnake() {
        assertFalse(HeroControlSystem.isEffectiveAttackDirection("attack_000"));
        assertTrue(HeroControlSystem.isEffectiveAttackDirection("attack_090"));
        assertFalse(HeroControlSystem.isEffectiveAttackDirection("attack_180"));
        assertFalse(HeroControlSystem.isEffectiveAttackDirection("attack_270"));
    }

    @Test
    public void heroAttackBecomesEffectiveOnItsTenthFrame() {
        assertFalse(HeroControlSystem.hasReachedHeroAttackImpactFrame(8f / 12f, 12f));
        assertTrue(HeroControlSystem.hasReachedHeroAttackImpactFrame(9f / 12f, 12f));
        assertTrue(HeroControlSystem.hasReachedHeroAttackImpactFrame(10f / 12f, 12f));
        assertFalse(HeroControlSystem.hasReachedHeroAttackImpactFrame(1f, 0f));
    }

    @Test
    public void heroHitCanBeCancelledFromHalfOfItsAnimation() {
        assertFalse(HeroControlSystem.canCancelHitForAttack(0.65f, 12f, 16));
        assertTrue(HeroControlSystem.canCancelHitForAttack(8f / 12f, 12f, 16));
        assertTrue(HeroControlSystem.canCancelHitForAttack(1f, 12f, 16));
    }

    @Test
    public void invalidHitTimingCannotBeCancelled() {
        assertFalse(HeroControlSystem.canCancelHitForAttack(1f, 0f, 16));
        assertFalse(HeroControlSystem.canCancelHitForAttack(1f, 12f, 0));
        assertFalse(HeroControlSystem.canCancelHitForAttack(-1f, 12f, 16));
    }

    @Test
    public void movementCancelsHeroHitOnlyFromHalfOfTheAnimation() {
        assertFalse(HeroControlSystem.shouldCancelHitForMovement(true, false, true));
        assertFalse(HeroControlSystem.shouldCancelHitForMovement(true, true, false));
        assertFalse(HeroControlSystem.shouldCancelHitForMovement(false, true, true));
        assertTrue(HeroControlSystem.shouldCancelHitForMovement(true, true, true));
    }

    @Test
    public void changesAnimationAssetAndRestartsItsDirectionalClip() {
        RecordingAnimation animation = new RecordingAnimation("run_180", 4.25f);

        HeroControlSystem.setAnimation(animation, "hero_idle", "idle_180");

        assertEquals("hero_idle", animation.animationName);
        assertEquals("idle_180", animation.clip());
        assertEquals(0f, animation.stateTime(), 0f);
        assertEquals(8f, animation.fps(), 0f);
        assertTrue(animation.isPlaying());
    }

    @Test
    public void locomotionRestoresLoopAfterInterruptingANonLoopingHit() {
        RecordingAnimation animation = new RecordingAnimation("hit_090", 0.75f);
        animation.looping = false;

        HeroControlSystem.setLoopingAnimation(
            animation,
            "hero_run",
            "run_090"
        );

        assertEquals("hero_run", animation.animationName);
        assertEquals("run_090", animation.clip());
        assertTrue(animation.isLooping());
    }

    @Test
    public void preservesPhaseWhenOnlyTheDirectionChanges() {
        RecordingAnimation animation = new RecordingAnimation("idle_000", 2.5f);

        HeroControlSystem.setAnimation(animation, "hero_idle", "idle_090");

        assertEquals("idle_090", animation.clip());
        assertEquals(2.5f, animation.stateTime(), 0f);
        assertEquals(12f, animation.fps(), 0f);
    }

    @Test
    public void startsDirectionalAttackOnceWithItsAuthoredFps() {
        RecordingAnimation animation = new RecordingAnimation("idle_270", 2.5f);
        animation.fps = 7.5f;

        boolean started = HeroControlSystem.startAttack(
                animation,
                "hero_attack",
                "attack_270"
        );

        assertTrue(started);
        assertEquals("hero_attack", animation.animationName);
        assertEquals("attack_270", animation.clip());
        assertEquals(0f, animation.stateTime(), 0f);
        assertEquals(9f, animation.fps(), 0f);
        assertFalse(animation.isLooping());
        assertTrue(animation.isPlaying());
    }

    @Test
    public void cannotRestartHeroAttackBeforeTheCurrentAttackFinishes() {
        assertTrue(HeroControlSystem.shouldStartAttack(false, true));
        assertFalse(HeroControlSystem.shouldStartAttack(true, true));
        assertFalse(HeroControlSystem.shouldStartAttack(false, false));
    }

    @Test
    public void startsDirectionalHeroHitOnceWithItsAuthoredFps() {
        RecordingAnimation animation = new RecordingAnimation("attack_090", 1f);

        assertTrue(HeroControlSystem.startHit(animation, "hit_090"));

        assertEquals("hero_hit", animation.animationName);
        assertEquals("hit_090", animation.clip());
        assertEquals(0f, animation.stateTime(), 0f);
        assertEquals(6f, animation.fps(), 0f);
        assertFalse(animation.isLooping());
    }

    private static final class RecordingAnimation implements AnimationFacade {
        private String clip;
        private float stateTime;
        private boolean playing;
        private boolean looping = true;
        private float fps = 12f;
        private String animationName;

        private RecordingAnimation(String clip, float stateTime) {
            this.clip = clip;
            this.stateTime = stateTime;
            this.animationName = "hero_" + clip.substring(0, clip.indexOf('_'));
        }

        @Override public boolean exists() { return true; }
        @Override public String clip() { return clip; }
        @Override public boolean hasClip(String clipName) {
            return clipName != null && clipName.startsWith(animationPrefix() + "_");
        }
        @Override public int frame() { return -1; }
        @Override public float stateTime() { return stateTime; }
        @Override public AnimationFacade play() { playing = true; return this; }
        @Override public AnimationFacade pause() { playing = false; return this; }
        @Override public AnimationFacade stop() { playing = false; stateTime = 0f; return this; }
        @Override public AnimationFacade restart() { playing = true; stateTime = 0f; return this; }
        @Override public AnimationFacade setAnimation(int assetId) { stateTime = 0f; return this; }
        @Override public AnimationFacade setAnimation(String animationName) { this.animationName = animationName; stateTime = 0f; return this; }
        @Override public AnimationFacade play(String clipName) { clip = clipName; stateTime = 0f; playing = true; return this; }
        @Override public AnimationFacade play(int assetId, String clipName) { return play(clipName); }
        @Override public AnimationFacade play(String animationName, String clipName) {
            this.animationName = animationName;
            if ("hero_idle".equals(animationName)) fps = 8f;
            if ("hero_attack".equals(animationName)) fps = 9f;
            if ("hero_hit".equals(animationName)) fps = 6f;
            return play(clipName);
        }
        @Override public AnimationFacade setClip(String clipName) { clip = clipName; stateTime = 0f; return this; }
        @Override public AnimationFacade setLoop(boolean loop) { looping = loop; return this; }
        @Override public AnimationFacade setFps(float fps) { this.fps = fps; return this; }
        @Override public AnimationFacade setStateTime(float stateTime) { this.stateTime = stateTime; return this; }
        @Override public boolean isPlaying() { return playing; }
        @Override public boolean isLooping() { return looping; }
        @Override public float fps() { return fps; }
        @Override public boolean isFinished() { return false; }

        private String animationPrefix() {
            int separator = animationName.lastIndexOf('_');
            return separator >= 0 ? animationName.substring(separator + 1) : animationName;
        }
    }
}
