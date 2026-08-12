package games.pixscape.demo.gwt;

import com.badlogic.gdx.backends.gwt.preloader.AssetFilter;
import com.badlogic.gdx.backends.gwt.preloader.DefaultAssetFilter;

/** Keeps project bootstrap files eager while leaving scene resources available for lazy loading. */
public final class GwtAssetFilter implements AssetFilter {
    private static final String PROJECT_ROOT = "pixscape-project/";
    private final DefaultAssetFilter defaults = new DefaultAssetFilter();

    @Override
    public boolean accept(String file, boolean isDirectory) {
        String path = normalize(file);
        if (path.equals("assets.txt")) return false;
        if (path.endsWith("/libgdx.png") || path.equals("libgdx.png")) return false;
        if (path.startsWith("com/badlogic/gdx/")) return false;
        if (path.startsWith(PROJECT_ROOT + "shaders/")
                && path.contains("/desktop-gl30/")) return false;
        return defaults.accept(file, isDirectory);
    }

    @Override
    public boolean preload(String file) {
        String path = normalize(file);
        return path.equals("splashscreen.png")
                || path.equals(PROJECT_ROOT + "project.json")
                || path.equals(PROJECT_ROOT + "animations.json")
                || path.equals(PROJECT_ROOT + "tiled-animations.json")
                || path.equals(PROJECT_ROOT + "tileset-profiles.json")
                || path.startsWith(PROJECT_ROOT + "shaders/core/es3-webgl2/")
                || path.startsWith(PROJECT_ROOT + "shaders/examples/fx/es3-webgl2/")
                || path.startsWith(PROJECT_ROOT + "shaders/examples/material/es3-webgl2/")
                || path.equals(PROJECT_ROOT + "shaders/examples/params.json")
                || path.startsWith(PROJECT_ROOT + "shaders/includes/");
    }

    @Override
    public AssetType getType(String file) {
        return defaults.getType(file);
    }

    @Override
    public String getBundleName(String file) {
        return defaults.getBundleName(file);
    }

    private static String normalize(String file) {
        String path = file.replace('\\', '/');
        int assetRoot = path.lastIndexOf("/assets/");
        if (assetRoot >= 0) return path.substring(assetRoot + "/assets/".length());
        return path.startsWith("assets/") ? path.substring("assets/".length()) : path;
    }
}
