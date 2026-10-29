package games.pixscape.demo;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.github.tommyettinger.textra.TypingLabel;
import games.pixscape.demo.camera.CameraEffects;
import games.pixscape.demo.input.DemoInputKeys;
import games.pixscape.demo.input.HeroInput;
import games.pixscape.demo.input.KeyboardHeroInput;
import games.pixscape.demo.input.TouchHeroInput;
import games.pixscape.demo.loading.LoadingSplash;
import games.pixscape.demo.system.EnemySystem;
import games.pixscape.demo.system.CameraFollowSystem;
import games.pixscape.demo.util.CombatState;
import games.pixscape.demo.system.HeroControlSystem;
import games.pixscape.runtime.api.PixscapeAPI;
import games.pixscape.runtime.api.ParticleRef;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.hud.HudScreenRuntime;
import games.pixscape.runtime.hud.MaterializedHud;
import games.pixscape.runtime.loading.SceneLoadHandle;
import games.pixscape.runtime.service.ShaderRegistry;

import java.util.ArrayList;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    public static final float HTML_PRELOAD_SHARE = 0.10f;

    private static final String SCENE_NAME = "demo";
    private static final String MENU_SCREEN_ID = "hud/menu";
    private static final String GAME_OVER_SCREEN_ID = "hud/gameover";
    private static final float GAME_OVER_LABEL_SECONDS = 3f;
    private static final String REWARD_EFFECT = "stars.p";
    private static final float REWARD_TRAVEL_SECONDS = 0.65f;
    private static final float SCORE_PULSE_PEAK = 1.30f;
    private static final float SNAKE_BONUS_PULSE_PEAK = 1.60f;
    private static final int SNAKE_DEATH_BONUS_POINTS = 50;
    private static final float SCORE_PULSE_IN_SECONDS = 0.12f;
    private static final float SCORE_PULSE_OUT_SECONDS = 0.18f;
    private static final float PROJECT_SHARE = 0.10f;
    private static final float ANDROID_WORLD_WIDTH = 854f;
    private static final float ANDROID_WORLD_HEIGHT = 480f;

    private enum LoadingStage {
        SPLASH,
        PROJECT,
        MENU,
        SCENE,
        READY,
        GAMEPLAY
    }

    private enum SnakeDeathMessageStage {
        NONE,
        FIRST,
        SECOND
    }

    private PixscapeEngine engine;
    private FileHandle runtimeProjectDir;
    private CombatState combatState;
    private OrthographicCamera worldCamera;
    private ExtendViewport androidViewport;
    private CameraEffects cameraEffects;
    private TouchHeroInput touchHeroInput;
    private SceneLoadHandle sceneLoad;
    private LightTravel lightTravel;
    private HudScreenRuntime menuHud;
    private HudScreenRuntime gameOverHud;
    private Actor gameOverLabel;
    private Actor restartButton;
    private Actor exitButton;
    private float gameOverLabelRemaining;
    private boolean startRequested;
    private boolean restartRequested;
    private boolean exitRequested;
    private final ScoreRewards scoreRewards = new ScoreRewards();
    private final ArrayList<RewardFlight> rewardFlights = new ArrayList<>();
    private final Vector2 hudPoint = new Vector2();
    private final Vector3 projectedPoint = new Vector3();
    private Actor pulseTarget;
    private Action pulseAction;
    private float pulseRestScaleX;
    private float pulseRestScaleY;
    private MaterializedHud snakeDeathHud;
    private TypingLabel snakeDeathFirst;
    private TypingLabel snakeDeathThird;
    private SnakeDeathMessageStage snakeDeathMessageStage = SnakeDeathMessageStage.NONE;
    private LoadingStage loadingStage;
    private LoadingSplash loadingSplash;
    private InputProcessor installedInputProcessor;
    private float coreProgress;

    private static final class RewardFlight {
        final ScoreRewards.Ticket ticket;
        final ParticleRef effect;
        final MaterializedHud hud;
        float startX;
        float startY;
        float x;
        float y;
        float elapsed;
        boolean arrived;

        RewardFlight(ScoreRewards.Ticket ticket, ParticleRef effect,
                     MaterializedHud hud, float sourceWorldX, float sourceWorldY) {
            this.ticket = ticket;
            this.effect = effect;
            this.hud = hud;
            this.startX = sourceWorldX;
            this.startY = sourceWorldY;
            this.x = sourceWorldX;
            this.y = sourceWorldY;
        }
    }

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_INFO);
        DemoInputKeys.catchHandledKeys();

        loadingSplash = new LoadingSplash(displayedProgress());
        loadingStage = LoadingStage.SPLASH;
    }

    private void initializeProject() {
        FileHandle projectJson = Gdx.files.internal(
                PixscapeEngine.RUNTIME_DIR_NAME + "/project.json");
        if (!projectJson.exists()) {
            throw new GdxRuntimeException("Missing Pixscape runtime project: " + projectJson.path());
        }

        worldCamera = new OrthographicCamera();
        if (isAndroid()) {
            androidViewport = new ExtendViewport(
                ANDROID_WORLD_WIDTH,
                ANDROID_WORLD_HEIGHT,
                worldCamera
            );
        }
        cameraEffects = new CameraEffects();
        engine = new PixscapeEngine()
                .setWorldCamera(worldCamera);
        HeroInput heroInput = createHeroInput();
        PixscapeAPI api = engine.api();
        combatState = new CombatState();
        engine.setPreRenderSystemCustomizer(builder ->
                builder.with(new CameraFollowSystem(worldCamera, api))
        );
        engine.setPostRenderSystemCustomizer(builder ->
                builder.with(
                        new HeroControlSystem(api, combatState, heroInput),
                        new EnemySystem(api, combatState, cameraEffects,
                                this::addScore, this::onSnakeDeathAnimationFinished)
                )
        );
        engine.loadProject(projectJson.parent().parent());
        runtimeProjectDir = projectJson.parent();
        menuHud = new HudScreenRuntime(runtimeProjectDir,
                ShaderRegistry.get(RuntimeFs.HUD_TEXTURE_ARRAY));
        MaterializedHud menu = menuHud.show(MENU_SCREEN_ID).materializedHud();
        Actor start = menu.actor("text-button-1");
        if (!(start instanceof TextButton)) {
            throw new GdxRuntimeException("HUD menu is missing text-button-1.");
        }
        start.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                startRequested = true;
            }
        });
        Gdx.input.setInputProcessor(menuHud.inputProcessor());
        resizeWorld(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        menuHud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private HeroInput createHeroInput() {
        if (!isAndroid()) return new KeyboardHeroInput(engine::hasHudKeyboardFocus);

        touchHeroInput = new TouchHeroInput();
        return touchHeroInput;
    }

    private void installInputProcessor() {
        InputProcessor worldInput = touchHeroInput == null
            ? null : new GestureDetector(touchHeroInput);
        installedInputProcessor = routeInput(engine.getHudInputProcessor(), worldInput);
        Gdx.input.setInputProcessor(installedInputProcessor);
    }

    static InputProcessor routeInput(InputProcessor hudInput, InputProcessor worldInput) {
        return worldInput == null
            ? hudInput : new InputMultiplexer(hudInput, worldInput);
    }

    private void resizeWorld(int width, int height) {
        engine.resize(width, height);
        if (androidViewport != null) {
            androidViewport.update(width, height, false);
        }
    }

    private static boolean isAndroid() {
        return Gdx.app.getType() == Application.ApplicationType.Android;
    }

    @Override
    public void render() {
        float lightDelta = Gdx.graphics.getDeltaTime();
        float delta = Math.min(lightDelta, 0.1f);

        switch (loadingStage) {
            case SPLASH:
                loadingSplash.render(delta, displayedProgress());
                loadingStage = LoadingStage.PROJECT;
                return;
            case PROJECT:
                initializeProject();
                setCoreProgress(PROJECT_SHARE);
                loadingStage = LoadingStage.MENU;
                renderMenu(delta);
                return;
            case MENU:
                if (startRequested) {
                    beginDemo();
                    loadingSplash.render(delta, displayedProgress());
                    return;
                }
                renderMenu(delta);
                return;
            case SCENE:
                updateSceneLoading();
                loadingSplash.render(delta, displayedProgress());
                return;
            case READY:
                // Approximate ground footprint of willow2, not the centre of its image.
                lightTravel = new LightTravel(
                    engine.api().entities().requireName("Point light").transform(), -1145f, 3385f);
                lightDelta = 0f; // First frame stays at the authored starting position.
                loadingSplash.dispose();
                loadingSplash = null;
                loadingStage = LoadingStage.GAMEPLAY;
                break;
            case GAMEPLAY:
                if (exitRequested) {
                    exitRequested = false;
                    Gdx.app.exit();
                    return;
                }
                if (restartRequested) {
                    beginDemo();
                    loadingSplash.render(delta, displayedProgress());
                    return;
                }
                break;
            default:
                throw new IllegalStateException("Unknown loading stage: " + loadingStage);
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        lightTravel.update(lightDelta);
        engine.update(delta);
        if (combatState.consumeHeroDeath()) {
            showGameOver();
        } else if (gameOverHud != null && gameOverLabelRemaining > 0f) {
            gameOverLabelRemaining = Math.max(0f, gameOverLabelRemaining - delta);
            if (gameOverLabelRemaining == 0f) {
                gameOverLabel.setVisible(false);
                restartButton.setVisible(true);
                restartButton.setTouchable(Touchable.enabled);
                if (Gdx.app.getType() != Application.ApplicationType.WebGL) {
                    exitButton.setVisible(true);
                    exitButton.setTouchable(Touchable.enabled);
                }
            }
        }
        advanceSnakeDeathMessages();
        updateCameraZoom(delta);
        updateRewardFlights(delta);
        engine.render();
        if (gameOverHud != null) {
            gameOverHud.act(delta);
            gameOverHud.draw();
        }
    }

    private void renderMenu(float delta) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        menuHud.act(delta);
        menuHud.draw();
    }

    private void beginDemo() {
        lightTravel = null;
        clearSnakeDeathMessages();
        clearRewardFlights(true);
        clearScorePulse();
        scoreRewards.newSession();
        startRequested = false;
        restartRequested = false;
        exitRequested = false;
        Gdx.input.setInputProcessor(null);
        if (menuHud != null) {
            menuHud.dispose();
            menuHud = null;
        }
        if (gameOverHud != null) {
            gameOverHud.dispose();
            gameOverHud = null;
        }
        gameOverLabel = null;
        restartButton = null;
        exitButton = null;
        gameOverLabelRemaining = 0f;
        combatState.reset();
        coreProgress = PROJECT_SHARE;
        if (loadingSplash == null) loadingSplash = new LoadingSplash(displayedProgress());
        installInputProcessor();
        sceneLoad = engine.beginLoadScene(SCENE_NAME);
        loadingStage = LoadingStage.SCENE;
    }

    private void showGameOver() {
        clearSnakeDeathMessages();
        clearRewardFlights(true);
        clearScorePulse();
        gameOverHud = new HudScreenRuntime(runtimeProjectDir,
                ShaderRegistry.get(RuntimeFs.HUD_TEXTURE_ARRAY));
        MaterializedHud hud = gameOverHud.show(GAME_OVER_SCREEN_ID).materializedHud();
        gameOverLabel = hud.actor("textra-label-1");
        restartButton = hud.actor("text-button-1");
        exitButton = hud.actor("text-button-2");
        if (gameOverLabel == null || !(restartButton instanceof TextButton)
                || !(exitButton instanceof TextButton)) {
            throw new GdxRuntimeException(
                    "HUD gameover needs textra-label-1, text-button-1 and text-button-2.");
        }
        gameOverLabel.setVisible(true);
        restartButton.setVisible(false);
        restartButton.setTouchable(Touchable.disabled);
        exitButton.setVisible(false);
        exitButton.setTouchable(Touchable.disabled);
        restartButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                restartRequested = true;
            }
        });
        exitButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                exitRequested = true;
            }
        });
        gameOverLabelRemaining = GAME_OVER_LABEL_SECONDS;
        gameOverHud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(gameOverHud.inputProcessor());
    }

    private void addScore(int points, float worldX, float worldY) {
        ScoreRewards.Ticket ticket = scoreRewards.earn(points);
        MaterializedHud hud = engine.getActiveHud();
        Actor target = hud == null ? null : hud.actor("label-2");
        if (!(target instanceof Label) || target.getStage() == null) {
            displayReward(ticket);
            return;
        }
        try {
            ParticleRef effect = engine.api().particles().spawn(REWARD_EFFECT, worldX, worldY);
            effect.particles().setIgnoreAmbientLighting(true);
            effect.entity().renderOrder()
                .layerIndex(engine.api().entities().requireName("hero").renderOrder().layerIndex())
                .zIndex(100);
            rewardFlights.add(new RewardFlight(ticket, effect, hud, worldX, worldY));
        } catch (RuntimeException unavailable) {
            Gdx.app.error("Reward", "HUD reward effect is unavailable.", unavailable);
            displayReward(ticket);
        }
    }

    private void displayReward(ScoreRewards.Ticket ticket) {
        displayReward(ticket, SCORE_PULSE_PEAK);
    }

    private void displayReward(ScoreRewards.Ticket ticket, float pulsePeak) {
        if (scoreRewards.display(ticket)) {
            updateScoreLabel();
            pulseScoreLabel(pulsePeak);
        }
    }

    private void onSnakeDeathAnimationFinished() {
        MaterializedHud hud = engine.getActiveHud();
        Actor first = hud == null ? null : hud.actor("textra-label-1");
        Actor third = hud == null ? null : hud.actor("textra-label-3");
        if (!(first instanceof TypingLabel) || !(third instanceof TypingLabel)) {
            awardSnakeDeathBonus();
            return;
        }

        clearSnakeDeathMessages();
        snakeDeathHud = hud;
        snakeDeathFirst = (TypingLabel) first;
        snakeDeathThird = (TypingLabel) third;
        snakeDeathThird.setVisible(false);
        snakeDeathFirst.restart();
        snakeDeathFirst.setVisible(true);
        snakeDeathMessageStage = SnakeDeathMessageStage.FIRST;
    }

    private void advanceSnakeDeathMessages() {
        if (snakeDeathMessageStage == SnakeDeathMessageStage.NONE) return;
        if (engine.getActiveHud() != snakeDeathHud) {
            clearSnakeDeathMessages();
            return;
        }
        if (snakeDeathMessageStage == SnakeDeathMessageStage.FIRST
                && snakeDeathFirst.hasEnded()) {
            snakeDeathThird.restart();
            snakeDeathThird.setVisible(true);
            snakeDeathMessageStage = SnakeDeathMessageStage.SECOND;
        } else if (snakeDeathMessageStage == SnakeDeathMessageStage.SECOND
                && snakeDeathThird.hasEnded()) {
            awardSnakeDeathBonus();
            clearSnakeDeathMessages();
        }
    }

    private void awardSnakeDeathBonus() {
        displayReward(scoreRewards.earn(SNAKE_DEATH_BONUS_POINTS), SNAKE_BONUS_PULSE_PEAK);
    }

    private void clearSnakeDeathMessages() {
        if (snakeDeathFirst != null) snakeDeathFirst.setVisible(false);
        if (snakeDeathThird != null) snakeDeathThird.setVisible(false);
        snakeDeathHud = null;
        snakeDeathFirst = null;
        snakeDeathThird = null;
        snakeDeathMessageStage = SnakeDeathMessageStage.NONE;
    }

    private void updateScoreLabel() {
        MaterializedHud hud = engine.getActiveHud();
        Actor actor = hud == null ? null : hud.actor("label-2");
        if (actor instanceof Label) {
            ((Label) actor).setText(Integer.toString(scoreRewards.displayed()));
        }
    }

    private void updateRewardFlights(float delta) {
        MaterializedHud hud = engine.getActiveHud();
        for (int i = rewardFlights.size() - 1; i >= 0; i--) {
            RewardFlight flight = rewardFlights.get(i);
            if (!flight.effect.entity().exists()) {
                displayReward(flight.ticket);
                rewardFlights.remove(i);
                continue;
            }
            if (!flight.arrived) {
                Actor target = hud == flight.hud && hud != null ? hud.actor("label-2") : null;
                if (target == null || target.getStage() == null) {
                    displayReward(flight.ticket);
                    flight.effect.complete();
                    flight.arrived = true;
                    continue;
                }
                Stage stage = target.getStage();
                if (hud.root() instanceof Layout) ((Layout) hud.root()).validate();
                hudPoint.set(target.getWidth() * 0.5f, target.getHeight() * 0.5f);
                target.localToStageCoordinates(hudPoint);
                hudToWorld(stage);
                flight.elapsed = Math.min(REWARD_TRAVEL_SECONDS, flight.elapsed + delta);
                float t = flight.elapsed / REWARD_TRAVEL_SECONDS;
                float eased = t * t * (3f - 2f * t);
                flight.x = MathUtils.lerp(flight.startX, hudPoint.x, eased);
                flight.y = MathUtils.lerp(flight.startY, hudPoint.y, eased);
                flight.effect.transform().setPosition(flight.x, flight.y);
                if (flight.elapsed >= REWARD_TRAVEL_SECONDS) {
                    flight.arrived = true;
                    displayReward(flight.ticket);
                    flight.effect.complete();
                }
            }
        }
    }

    private void hudToWorld(Stage stage) {
        stage.getViewport().project(hudPoint);
        // Viewport.project uses a bottom-left origin; unproject expects touch coordinates
        // with a top-left origin, even when the world viewport has screen offsets.
        projectedPoint.set(hudPoint.x, Gdx.graphics.getHeight() - hudPoint.y, 0f);
        if (androidViewport != null) {
            androidViewport.unproject(projectedPoint);
        } else {
            worldCamera.unproject(projectedPoint, 0, 0,
                    Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        hudPoint.set(projectedPoint.x, projectedPoint.y);
    }

    private void clearRewardFlights(boolean flushScore) {
        for (int i = 0; i < rewardFlights.size(); i++) {
            rewardFlights.get(i).effect.remove();
        }
        rewardFlights.clear();
        if (flushScore && scoreRewards.flush() && engine != null) updateScoreLabel();
    }

    private void pulseScoreLabel(float peak) {
        MaterializedHud hud = engine.getActiveHud();
        Actor label = hud == null ? null : hud.actor("label-2");
        Actor container = hud == null ? null : hud.actor("container-1");
        if (!(label instanceof Label) || !(container instanceof Container)
                || label.getParent() != container) return;
        if (pulseTarget != container) {
            clearScorePulse();
            pulseTarget = container;
            pulseRestScaleX = container.getScaleX();
            pulseRestScaleY = container.getScaleY();
        }
        if (pulseAction != null) {
            container.removeAction(pulseAction);
            pulseAction = null;
        }
        if (hud.root() instanceof Layout) ((Layout) hud.root()).validate();
        container.setOrigin(container.getWidth() * 0.5f, container.getHeight() * 0.5f);
        ((Container<?>) container).setTransform(true);
        pulseAction = Actions.sequence(
                Actions.scaleTo(pulseRestScaleX * peak,
                        pulseRestScaleY * peak,
                        SCORE_PULSE_IN_SECONDS, Interpolation.sineOut),
                Actions.scaleTo(pulseRestScaleX, pulseRestScaleY,
                        SCORE_PULSE_OUT_SECONDS, Interpolation.bounceOut),
                Actions.run(() -> {
                    container.setScale(pulseRestScaleX, pulseRestScaleY);
                    pulseAction = null;
                }));
        container.addAction(pulseAction);
    }

    private void clearScorePulse() {
        if (pulseTarget != null) {
            if (pulseAction != null) pulseTarget.removeAction(pulseAction);
            pulseTarget.setScale(pulseRestScaleX, pulseRestScaleY);
        }
        pulseAction = null;
        pulseTarget = null;
    }

    private void updateCameraZoom(float delta) {
        cameraEffects.update(delta);
        float userZoom = touchHeroInput != null
            ? touchHeroInput.userZoom()
            : 1f;
        worldCamera.zoom = userZoom * cameraEffects.zoomMultiplier();
        worldCamera.update();
    }

    private void updateSceneLoading() {
        sceneLoad.update();
        if (sceneLoad.isFailed()) {
            throw new GdxRuntimeException("Failed to load scene: " + SCENE_NAME, sceneLoad.failure());
        }
        setCoreProgress(PROJECT_SHARE + (1f - PROJECT_SHARE) * sceneLoad.progress());
        if (!sceneLoad.isReady()) return;

        sceneLoad = null;
        setCoreProgress(1f);
        updateScoreLabel();
        loadingStage = LoadingStage.READY;
    }

    private void setCoreProgress(float value) {
        coreProgress = Math.max(coreProgress, MathUtils.clamp(value, 0f, 1f));
    }

    private float displayedProgress() {
        if (Gdx.app.getType() == Application.ApplicationType.WebGL) {
            return HTML_PRELOAD_SHARE + (1f - HTML_PRELOAD_SHARE) * coreProgress;
        }
        return coreProgress;
    }

    @Override
    public void resize(int width, int height) {
        // GLFW can report a zero-sized framebuffer while the window is minimized.
        if (width <= 0 || height <= 0) return;
        if (loadingSplash != null) {
            loadingSplash.resize(width, height);
        }
        if (engine != null) {
            resizeWorld(width, height);
        }
        if (menuHud != null) menuHud.resize(width, height);
        if (gameOverHud != null) gameOverHud.resize(width, height);
    }

    @Override
    public void dispose() {
        clearSnakeDeathMessages();
        clearRewardFlights(true);
        clearScorePulse();
        sceneLoad = null;
        if (menuHud != null) {
            if (Gdx.input.getInputProcessor() == menuHud.inputProcessor()) {
                Gdx.input.setInputProcessor(null);
            }
            menuHud.dispose();
            menuHud = null;
        }
        if (gameOverHud != null) {
            if (Gdx.input.getInputProcessor() == gameOverHud.inputProcessor()) {
                Gdx.input.setInputProcessor(null);
            }
            gameOverHud.dispose();
            gameOverHud = null;
        }
        if (installedInputProcessor != null
            && Gdx.input.getInputProcessor() == installedInputProcessor) {
            Gdx.input.setInputProcessor(null);
        }
        installedInputProcessor = null;
        touchHeroInput = null;
        androidViewport = null;
        cameraEffects = null;
        if (loadingSplash != null) {
            loadingSplash.dispose();
            loadingSplash = null;
        }
        if (engine != null) {
            engine.dispose();
            engine = null;
        }
    }

}
