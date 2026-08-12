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
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
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
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneLoadHandle;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    public static final float HTML_PRELOAD_SHARE = 0.10f;

    private static final String SCENE_NAME = "demo";
    private static final float PROJECT_SHARE = 0.10f;
    private static final float ANDROID_WORLD_WIDTH = 854f;
    private static final float ANDROID_WORLD_HEIGHT = 480f;

    private enum LoadingStage {
        SPLASH,
        PROJECT,
        SCENE,
        READY,
        GAMEPLAY
    }

    private PixscapeEngine engine;
    private OrthographicCamera worldCamera;
    private ExtendViewport androidViewport;
    private CameraEffects cameraEffects;
    private TouchHeroInput touchHeroInput;
    private SceneLoadHandle sceneLoad;
    private LoadingStage loadingStage;
    private LoadingSplash loadingSplash;
    private InputProcessor androidInputProcessor;
    private float coreProgress;

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
        HeroInput heroInput = createHeroInput();
        engine = new PixscapeEngine()
                .setWorldCamera(worldCamera);
        PixscapeAPI api = engine.api();
        CombatState combatState = new CombatState();
        engine.setPreRenderSystemCustomizer(builder ->
                builder.with(new CameraFollowSystem(worldCamera, api))
        );
        engine.setPostRenderSystemCustomizer(builder ->
                builder.with(
                        new HeroControlSystem(api, combatState, heroInput),
                        new EnemySystem(api, combatState, cameraEffects)
                )
        );
        engine.loadProject(projectJson.parent().parent());
        resizeWorld(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        sceneLoad = engine.beginLoadScene(SCENE_NAME);
    }

    private HeroInput createHeroInput() {
        if (!isAndroid()) return new KeyboardHeroInput();

        touchHeroInput = new TouchHeroInput();
        androidInputProcessor = new InputMultiplexer(
            new GestureDetector(touchHeroInput)
        );
        Gdx.input.setInputProcessor(androidInputProcessor);
        return touchHeroInput;
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
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);

        switch (loadingStage) {
            case SPLASH:
                loadingSplash.render(delta, displayedProgress());
                loadingStage = LoadingStage.PROJECT;
                return;
            case PROJECT:
                initializeProject();
                setCoreProgress(PROJECT_SHARE);
                loadingStage = LoadingStage.SCENE;
                loadingSplash.render(delta, displayedProgress());
                return;
            case SCENE:
                updateSceneLoading();
                loadingSplash.render(delta, displayedProgress());
                return;
            case READY:
                loadingSplash.dispose();
                loadingSplash = null;
                loadingStage = LoadingStage.GAMEPLAY;
                break;
            case GAMEPLAY:
                break;
            default:
                throw new IllegalStateException("Unknown loading stage: " + loadingStage);
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        engine.update(delta);
        updateCameraZoom(delta);
        engine.render();
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
        if (loadingSplash != null) {
            loadingSplash.resize(width, height);
        }
        if (engine != null) {
            resizeWorld(width, height);
        }
    }

    @Override
    public void dispose() {
        sceneLoad = null;
        if (androidInputProcessor != null
            && Gdx.input.getInputProcessor() == androidInputProcessor) {
            Gdx.input.setInputProcessor(null);
        }
        androidInputProcessor = null;
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
