package games.pixscape.demo.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import games.pixscape.demo.Main;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

/** Opt-in desktop check of the authored scene, separate from the scene assets. */
public class SceneGlSmokeTest {
    @Test
    public void loadsAndCapturesSceneWithBaseShader() {
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Pixscape demo scene smoke");
        config.setWindowedMode(640, 480);
        config.setInitialVisible(false);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        config.disableAudio(true);
        config.useVsync(false);
        new Lwjgl3Application(new ApplicationAdapter() {
            Main demo;
            boolean started;
            int gameplayFrames;
            long startNanos;
            Field stageField;
            Field startField;

            @Override public void create() {
                try {
                    demo = new Main();
                    stageField = Main.class.getDeclaredField("loadingStage");
                    stageField.setAccessible(true);
                    startField = Main.class.getDeclaredField("startRequested");
                    startField.setAccessible(true);
                    startNanos = System.nanoTime();
                    demo.create();
                    demo.resize(640, 480);
                } catch (Throwable ex) {
                    failure[0] = ex;
                    Gdx.app.exit();
                }
            }

            @Override public void render() {
                if (failure[0] != null) return;
                try {
                    if (System.nanoTime() - startNanos > 90_000_000_000L) {
                        throw new AssertionError("Scene did not reach GAMEPLAY within 90 seconds");
                    }
                    demo.render();
                    String stage = stageField.get(demo).toString();
                    if ("MENU".equals(stage) && !started) {
                        demo.resize(0, 0);
                        demo.resize(640, 480);
                        startField.setBoolean(demo, true);
                        started = true;
                    }
                    if ("GAMEPLAY".equals(stage) && ++gameplayFrames >= 6) {
                        Pixmap shot = ScreenUtils.getFrameBufferPixmap(0, 0, 640, 480);
                        try {
                            int colored = 0;
                            for (int y = 0; y < 480; y += 8) {
                                for (int x = 0; x < 640; x += 8) {
                                    int rgba = shot.getPixel(x, y);
                                    if ((rgba >>> 8) != 0) colored++;
                                }
                            }
                            Assert.assertTrue("Scene framebuffer is blank", colored > 200);
                            PixmapIO.writePNG(Gdx.files.local("../build/reports/scene-gl-smoke/scene1.png"), shot);
                        } finally {
                            shot.dispose();
                        }
                        Gdx.app.exit();
                    }
                } catch (Throwable ex) {
                    failure[0] = ex;
                    Gdx.app.exit();
                }
            }

            @Override public void dispose() {
                if (demo != null) demo.dispose();
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Scene GL smoke failed", failure[0]);
    }
}
