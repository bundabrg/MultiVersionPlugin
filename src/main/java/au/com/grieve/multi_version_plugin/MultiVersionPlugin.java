package au.com.grieve.multi_version_plugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public abstract class MultiVersionPlugin extends JavaPlugin {
    // Static Variables
    private static MultiVersionPlugin instance;
    private static MultiVersionLoader loader;
    private static String localBase;
    private static String localPluginName;
    private final static String serverVersion = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3].substring(1);

    // Local Variables
    private VersionPlugin versionPlugin;

    /**
     * Return Current Plugin instance
     */
    public static MultiVersionPlugin getInstance() {
        return instance;
    }

    /**
     * Initialize and load the appropriate Version
     */
    protected static void initPlugin(String base, String pluginName) {
        localBase = base;
        localPluginName = pluginName;

        loader = new MultiVersionLoader(
                MultiVersionPlugin.class.getClassLoader(),
                base,
                serverVersion);
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
            versionPlugin = (VersionPlugin) loader.loadClass(String.join(".", localBase, localPluginName)).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
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
