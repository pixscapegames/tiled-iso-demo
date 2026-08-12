package games.pixscape.demo.loading;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Small, application-owned presentation for startup and progressive scene loading. */
public final class LoadingSplash implements AutoCloseable {
    private static final String SPLASH_PATH = "splashscreen.png";

    private final SpriteBatch batch;
    private final Texture splashTexture;
    private final Texture barTexture;
    private final ProgressBar progressBar;
    private final Matrix4 projection = new Matrix4();

    private float elapsed;
    private float targetWidth;
    private float targetHeight;
    private int viewportWidth;
    private int viewportHeight;
    private boolean disposed;

    public LoadingSplash(float initialProgress) {
        batch = new SpriteBatch();
        splashTexture = new Texture(Gdx.files.internal(SPLASH_PATH));

        Pixmap barPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        barPixmap.setColor(Color.WHITE);
        barPixmap.fill();
        barTexture = new Texture(barPixmap);
        barPixmap.dispose();

        TextureRegion region = new TextureRegion(barTexture);
        TextureRegionDrawable background = new TextureRegionDrawable(region);
        background.setMinHeight(10f);
        TextureRegionDrawable fill = new TextureRegionDrawable(region);
        fill.setMinHeight(10f);

        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle();
        style.background = background.tint(new Color(0.08f, 0.11f, 0.14f, 0.8f));
        style.knobBefore = fill.tint(new Color(0.25f, 0.82f, 0.94f, 1f));
        progressBar = new ProgressBar(0f, 1f, 0.001f, false, style);
        progressBar.setValue(MathUtils.clamp(initialProgress, 0f, 1f));
        progressBar.setAnimateDuration(0.12f);
        progressBar.setAnimateInterpolation(Interpolation.smooth);
        progressBar.setProgrammaticChangeEvents(false);

        if (Gdx.app.getType() == Application.ApplicationType.WebGL) {
            elapsed = 0.67f;
        }
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void resize(int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
        float imageAspect = (float) splashTexture.getWidth() / splashTexture.getHeight();
        targetWidth = Math.min(viewportWidth * 0.78f, 640f);
        targetHeight = targetWidth / imageAspect;
        float maxHeight = viewportHeight * 0.62f;
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight;
            targetWidth = targetHeight * imageAspect;
        }

        float barWidth = Math.min(viewportWidth * 0.52f, 420f);
        float barY = Math.max(18f, (viewportHeight - targetHeight) * 0.5f - 18f);
        progressBar.setBounds((viewportWidth - barWidth) * 0.5f, barY, barWidth, 10f);
        projection.setToOrtho2D(0f, 0f, viewportWidth, viewportHeight);
    }

    public void render(float delta, float progress) {
        elapsed += Math.min(delta, 0.1f);
        progressBar.setValue(MathUtils.clamp(progress, 0f, 1f));
        progressBar.act(delta);

        Gdx.gl.glClearColor(0.035f, 0.047f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float intro = Interpolation.smooth.apply(MathUtils.clamp(elapsed / 0.55f, 0f, 1f));
        float scale = MathUtils.lerp(0.18f, 1f, intro);
        float drawWidth = targetWidth * scale;
        float drawHeight = targetHeight * scale;
        float imageX = (viewportWidth - drawWidth) * 0.5f;
        float imageY = (viewportHeight - drawHeight) * 0.5f + 24f;
        float barAlpha = Interpolation.fade.apply(
                MathUtils.clamp((elapsed - 0.22f) / 0.45f, 0f, 1f));

        batch.setProjectionMatrix(projection);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(splashTexture, imageX, imageY, drawWidth, drawHeight);
        progressBar.draw(batch, barAlpha);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    @Override
    public void close() {
        dispose();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        batch.dispose();
        splashTexture.dispose();
        barTexture.dispose();
    }
}
