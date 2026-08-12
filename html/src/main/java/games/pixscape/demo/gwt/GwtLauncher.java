package games.pixscape.demo.gwt;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.badlogic.gdx.backends.gwt.preloader.Preloader.Asset;
import com.badlogic.gdx.backends.gwt.preloader.Preloader.PreloaderCallback;
import com.badlogic.gdx.backends.gwt.preloader.Preloader.PreloaderState;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import games.pixscape.demo.Main;

/** Launches the GWT application. */
public class GwtLauncher extends GwtApplication {
        private static final String SPLASH_PATH = "splashscreen.png";

        @Override
        public GwtApplicationConfiguration getConfig () {
            // Resizable application, uses available space in browser with no padding:
            GwtApplicationConfiguration cfg = new GwtApplicationConfiguration(true);
            cfg.padVertical = 0;
            cfg.padHorizontal = 0;
            cfg.useGL30 = true;
            return cfg;
            // If you want a fixed size application, comment out the above resizable section,
            // and uncomment below:
            //return new GwtApplicationConfiguration(640, 480);
        }
        @Override
        public ApplicationListener createApplicationListener () {
            return new Main();
        }

        @Override
        public PreloaderCallback getPreloaderCallback() {
            final VerticalPanel panel = new VerticalPanel();
            panel.setStyleName("pixscape-preloader");
            panel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);

            final Image logo = new Image();
            logo.setStyleName("pixscape-preloader-logo");
            logo.setVisible(false);
            panel.add(logo);

            final SimplePanel meter = new SimplePanel();
            meter.setStyleName("pixscape-preloader-meter");
            final InlineHTML fill = new InlineHTML();
            fill.setStyleName("pixscape-preloader-meter-fill");
            meter.add(fill);
            panel.add(meter);
            getRootPanel().add(panel);

            return new PreloaderCallback() {
                private boolean logoResolved;

                @Override
                public void error(String file) {
                    System.out.println("Unable to preload: " + file);
                }

                @Override
                public void update(PreloaderState state) {
                    if (!logoResolved) {
                        for (Asset asset : state.assets) {
                            if (SPLASH_PATH.equals(asset.file)) {
                                logo.setUrl(getPreloaderBaseURL() + asset.url);
                                logo.setVisible(true);
                                logoResolved = true;
                                break;
                            }
                        }
                    }
                    fill.getElement().getStyle().setWidth(
                            100f * Main.HTML_PRELOAD_SHARE * state.getProgress(), Unit.PCT);
                }
            };
        }

        @Override
        public void onModuleLoad() {
            setLoadingListener(new LoadingListener() {
                @Override
                public void beforeSetup() {
                }

                @Override
                public void afterSetup() {
                    focusCanvas();
                }
            });
            super.onModuleLoad();
        }

        private void focusCanvas() {
            Element canvas = getCanvasElement();
            if (canvas != null) {
                canvas.setAttribute("tabindex", "0");
                canvas.focus();
            }
        }
}
