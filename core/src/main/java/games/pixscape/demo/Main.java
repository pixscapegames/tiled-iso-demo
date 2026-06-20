package games.pixscape.demo;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.utils.GdxRuntimeException;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.optional.PhysicsMouseDragSystem;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private static final String SCENE_NAME = "iso2";

    private static final float CAMERA_DT_MAX = 1f / 30f;
    private static final float CAMERA_ZOOM_SPEED = 1.5f;
    private static final float CAMERA_ZOOM_MIN = 0.2f;
    private static final float CAMERA_ZOOM_MAX = 10f;

    private PixscapeEngine engine;
    @SuppressWarnings("unused")
    private Box2dWorldService box2d;

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_INFO);
        Gdx.input.setCatchKey(Input.Keys.SPACE, true);
        Gdx.input.setCatchKey(Input.Keys.LEFT, true);
        Gdx.input.setCatchKey(Input.Keys.RIGHT, true);
        Gdx.input.setCatchKey(Input.Keys.UP, true);
        Gdx.input.setCatchKey(Input.Keys.DOWN, true);
        DemoHeroControlSystem.catchDirectionKeys();

        FileHandle projectJson = Gdx.files.internal(PixscapeEngine.RUNTIME_DIR_NAME + "/project.json");
        if (!projectJson.exists()) {
            throw new GdxRuntimeException("Missing Pixscape runtime project: " + projectJson.path());
        }

        OrthographicCamera worldCamera = new OrthographicCamera();
        PhysicsMouseDragSystem dragSystem = new PhysicsMouseDragSystem(worldCamera);
        dragSystem.setMaxForce(2000f);
        dragSystem.setFrequencyHz(5f);
        dragSystem.setDampingRatio(0.7f);
        dragSystem.setGrabRadiusMeters(0.25f);

        engine = new PixscapeEngine()
                .setWorldCamera(worldCamera)
                .setConfigurationCustomizer(builder -> builder.with(
                        dragSystem,
                        new DemoHeroControlSystem(worldCamera)
                ));
        engine.setPlatformTarget(platformTarget());
        engine.loadProject(projectJson.parent().parent());
        engine.loadScene(SCENE_NAME);
        dragSystem.setLayerState(engine.getLayerState());
        box2d = engine.getBox2dWorldService();

        Gdx.input.setInputProcessor(new InputMultiplexer(
                new TouchCameraInputAdapter(worldCamera),
                new PreviewInputAdapter()
        ));
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        if (engine != null) handleCameraZoom(dt);

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (engine != null) {
            engine.update(dt);

            engine.render();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (engine != null) {
            engine.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        if (engine != null) {
            engine.dispose();
            engine = null;
        }
    }

    private static PlatformTarget platformTarget() {
        switch (Gdx.app.getType()) {
            case Android:
                return PlatformTarget.ANDROID_ES3;
            case WebGL:
                return PlatformTarget.HTML_WEBGL2;
            case Desktop:
            default:
                return PlatformTarget.DESKTOP_GL30;
        }
    }

    private void handleCameraZoom(float dt) {
        OrthographicCamera cam = engine.getCamera();
        if (cam == null) return;

        float safeDt = Math.min(dt, CAMERA_DT_MAX);

        float zoomDelta = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.PLUS)
                || Gdx.input.isKeyPressed(Input.Keys.EQUALS)
                || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_ADD)) {
            zoomDelta -= CAMERA_ZOOM_SPEED * safeDt;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.MINUS)
                || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_SUBTRACT)) {
            zoomDelta += CAMERA_ZOOM_SPEED * safeDt;
        }

        if (zoomDelta != 0f) {
            cam.zoom = Math.max(CAMERA_ZOOM_MIN, Math.min(CAMERA_ZOOM_MAX, cam.zoom + zoomDelta));
        }

        cam.update();
    }

    private static final class PreviewInputAdapter extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            return isPreviewKey(keycode);
        }

        @Override
        public boolean keyUp(int keycode) {
            return isPreviewKey(keycode);
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            return true;
        }

        private static boolean isPreviewKey(int keycode) {
            return keycode == Input.Keys.LEFT
                    || keycode == Input.Keys.RIGHT
                    || keycode == Input.Keys.UP
                    || keycode == Input.Keys.DOWN
                    || DemoHeroControlSystem.isDirectionKey(keycode)
                    || keycode == Input.Keys.PLUS
                    || keycode == Input.Keys.EQUALS
                    || keycode == Input.Keys.MINUS
                    || keycode == Input.Keys.NUMPAD_ADD
                    || keycode == Input.Keys.NUMPAD_SUBTRACT;
        }
    }

    private final class TouchCameraInputAdapter extends InputAdapter {
        private static final float BODY_PICK_RADIUS_METERS = 0.25f;

        private final OrthographicCamera camera;
        private final Vector3 lastWorld = new Vector3();
        private final Vector3 currentWorld = new Vector3();
        private final Vector2 queryPoint = new Vector2();

        private boolean panning;
        private boolean pinching;
        private float pinchStartDistance;
        private float pinchStartZoom;
        private float lastPinchCenterX;
        private float lastPinchCenterY;

        private Body pickedBody;
        private float pickedDist2;

        private final QueryCallback pickCallback = new QueryCallback() {
            @Override
            public boolean reportFixture(Fixture fixture) {
                if (fixture == null || fixture.isSensor()) return true;

                Body body = fixture.getBody();
                if (body == null || body.getType() != BodyDef.BodyType.DynamicBody) return true;
                if (!fixture.testPoint(queryPoint)) return true;

                float dist2 = body.getPosition().dst2(queryPoint);
                if (pickedBody == null || dist2 < pickedDist2) {
                    pickedBody = body;
                    pickedDist2 = dist2;
                }
                return true;
            }
        };

        private TouchCameraInputAdapter(OrthographicCamera camera) {
            this.camera = camera;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (pointer == 1) {
                beginPinch();
                return true;
            }

            if (pointer != 0) return false;

            panning = !hasBodyAt(screenX, screenY);
            if (panning) {
                lastWorld.set(screenX, screenY, 0f);
                camera.unproject(lastWorld);
            }
            return panning;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (Gdx.input.isTouched(0) && Gdx.input.isTouched(1)) {
                updatePinch();
                return true;
            }

            if (pointer != 0 || !panning || pinching) return false;

            currentWorld.set(screenX, screenY, 0f);
            camera.unproject(currentWorld);
            camera.position.add(lastWorld.x - currentWorld.x, lastWorld.y - currentWorld.y, 0f);
            camera.update();

            lastWorld.set(screenX, screenY, 0f);
            camera.unproject(lastWorld);
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (pointer == 0) {
                panning = false;
            }

            if (!Gdx.input.isTouched(0) || !Gdx.input.isTouched(1)) {
                pinching = false;
            }
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            camera.zoom = clampZoom(camera.zoom + amountY * 0.1f * camera.zoom);
            camera.update();
            return true;
        }

        private void beginPinch() {
            pinching = true;
            panning = false;
            pinchStartDistance = pointerDistance();
            pinchStartZoom = camera.zoom;
            lastPinchCenterX = pointerCenterX();
            lastPinchCenterY = pointerCenterY();
        }

        private void updatePinch() {
            if (!pinching) {
                beginPinch();
            }

            float centerX = pointerCenterX();
            float centerY = pointerCenterY();
            lastWorld.set(lastPinchCenterX, lastPinchCenterY, 0f);
            camera.unproject(lastWorld);
            currentWorld.set(centerX, centerY, 0f);
            camera.unproject(currentWorld);
            camera.position.add(lastWorld.x - currentWorld.x, lastWorld.y - currentWorld.y, 0f);

            float distance = pointerDistance();
            if (pinchStartDistance > 0f && distance > 0f) {
                camera.zoom = clampZoom(pinchStartZoom * pinchStartDistance / distance);
            }

            camera.update();
            lastPinchCenterX = centerX;
            lastPinchCenterY = centerY;
        }

        private boolean hasBodyAt(int screenX, int screenY) {
            Box2dWorldService currentBox2d = box2d;
            if (currentBox2d == null || currentBox2d.world == null || currentBox2d.isDisposed()) {
                return false;
            }

            currentWorld.set(screenX, screenY, 0f);
            camera.unproject(currentWorld);

            LayerStateSOA layerState = engine != null ? engine.getLayerState() : null;
            float parallaxX = physicsParallaxX(layerState);
            float parallaxY = physicsParallaxY(layerState);
            float offsetX = (1f - parallaxX) * camera.position.x;
            float offsetY = (1f - parallaxY) * camera.position.y;

            float logicalX = currentWorld.x - offsetX;
            float logicalY = currentWorld.y - offsetY;
            queryPoint.set(currentBox2d.pxToM(logicalX), currentBox2d.pxToM(logicalY));

            pickedBody = null;
            pickedDist2 = Float.POSITIVE_INFINITY;
            float radius = BODY_PICK_RADIUS_METERS;
            currentBox2d.world.QueryAABB(
                    pickCallback,
                    queryPoint.x - radius,
                    queryPoint.y - radius,
                    queryPoint.x + radius,
                    queryPoint.y + radius
            );
            return pickedBody != null;
        }

        private float pointerDistance() {
            float dx = Gdx.input.getX(0) - Gdx.input.getX(1);
            float dy = Gdx.input.getY(0) - Gdx.input.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private float pointerCenterX() {
            return (Gdx.input.getX(0) + Gdx.input.getX(1)) * 0.5f;
        }

        private float pointerCenterY() {
            return (Gdx.input.getY(0) + Gdx.input.getY(1)) * 0.5f;
        }

        private float clampZoom(float zoom) {
            return Math.max(CAMERA_ZOOM_MIN, Math.min(CAMERA_ZOOM_MAX, zoom));
        }

        private float physicsParallaxX(LayerStateSOA layerState) {
            if (layerState == null || Float.isNaN(layerState.physicsParallaxX)) return 1f;
            return layerState.physicsParallaxX;
        }

        private float physicsParallaxY(LayerStateSOA layerState) {
            if (layerState == null || Float.isNaN(layerState.physicsParallaxY)) return 1f;
            return layerState.physicsParallaxY;
        }
    }
}
