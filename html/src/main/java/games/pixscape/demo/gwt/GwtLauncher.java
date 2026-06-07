package games.pixscape.demo.gwt;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import games.pixscape.demo.Main;

/** Launches the GWT application. */
public class GwtLauncher extends GwtApplication {
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
        public void onModuleLoad() {
            super.onModuleLoad();

            com.google.gwt.dom.client.Element canvas =
                    com.google.gwt.dom.client.Document.get()
                            .getElementsByTagName("canvas")
                            .getItem(0);

            if (canvas != null) {
                canvas.setAttribute("tabindex", "0");
                canvas.focus();
            }
        }
}
