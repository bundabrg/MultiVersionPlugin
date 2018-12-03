package au.com.grieve.multi_version_plugin;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public abstract class MultiVersionPlugin extends JavaPlugin {
    // Static Variables
    private static MultiVersionPlugin instance;
    private static MultiVersionLoader loader;
    private static String localBase;
    private static String localPluginName;

    // Local Variables
    private VersionPlugin versionPlugin;

    /**
     * Initialize and load the appropriate Version
     */
    protected static void initPlugin(String base, String pluginName, List<String> versions) {
        localBase = base;
        localPluginName = pluginName;

        loader = new MultiVersionLoader(
                MultiVersionPlugin.class.getClassLoader(),
                base,
                versions);
    }

    /**
     * Return true if class exists otherwise false
     *
     * @param name Class Name
     * @return true if class exists
     */
    protected static boolean isClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Constructor
     * @return
     */
    public MultiVersionPlugin() {
        super();
        instance = this;

        // Load Plugin
        try {
            versionPlugin = (VersionPlugin) loader.loadClass(String.join(".", localBase, localPluginName))
                    .getConstructor(MultiVersionPlugin.class)
                    .newInstance(this);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public VersionPlugin getVersionPlugin() {
        return versionPlugin;
    }

    @Override
    public void onEnable() {
        getVersionPlugin().onEnable();
    }

    @Override
    public void onDisable() {
        getVersionPlugin().onDisable();
    }

    // Make some methods public
    @Override
    public File getFile() {
        return super.getFile();
    }

}
