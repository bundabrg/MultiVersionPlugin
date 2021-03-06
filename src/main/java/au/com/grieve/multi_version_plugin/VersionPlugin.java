package au.com.grieve.multi_version_plugin;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;


/**
 * This is a proxy to the real JavaPlugin, and is extended by a Versioned Plugin in much the same way as they
 * would normally extend JavaPlugin
 */
public abstract class VersionPlugin {
    private final MultiVersionPlugin javaPlugin;

    public VersionPlugin(MultiVersionPlugin plugin) {
        javaPlugin = plugin;
    }

    public JavaPlugin getJavaPlugin() {
        return javaPlugin;
    }

    // Proxy Methods

    /**
     * Returns the folder that the plugin data's files are located in. The
     * folder may not yet exist.
     *
     * @return The folder.
     */
    public final File getDataFolder() {
        return javaPlugin.getDataFolder();
    }

    /**
     * Gets the associated PluginLoader responsible for this plugin
     *
     * @return PluginLoader that controls this plugin
     */
    public final PluginLoader getPluginLoader() {
        return javaPlugin.getPluginLoader();
    }

    /**
     * Returns the Server instance currently running this plugin
     *
     * @return Server running this plugin
     */
    public final Server getServer() {
        return javaPlugin.getServer();
    }

    /**
     * Returns a value indicating whether or not this plugin is currently
     * enabled
     *
     * @return true if this plugin is enabled, otherwise false
     */
    public final boolean isEnabled() {
        return javaPlugin.isEnabled();
    }

    /**
     * Returns the file which contains this plugin
     *
     * @return File containing this plugin
     */
    protected File getFile() {
        return javaPlugin.getFile();
    }

    /**
     * Returns the plugin.yaml file containing the details for this plugin
     *
     * @return Contents of the plugin.yaml file
     */
    public final PluginDescriptionFile getDescription() {
        return javaPlugin.getDescription();
    }

    public FileConfiguration getConfig() {
        return javaPlugin.getConfig();
    }

    public void reloadConfig() {
        javaPlugin.reloadConfig();
    }

    public void saveConfig() {
        javaPlugin.saveConfig();
    }

    public void saveDefaultConfig() {
        javaPlugin.saveDefaultConfig();
    }

    public void saveResource(String resourcePath, boolean replace) {
        javaPlugin.saveResource(resourcePath, replace);
    }

    public InputStream getResource(String filename) {
        return javaPlugin.getResource(filename);
    }

    /**
     * Gets the command with the given name, specific to this plugin. Commands
     * need to be registered in the {@link PluginDescriptionFile#getCommands()
     * PluginDescriptionFile} to exist at runtime.
     *
     * @param name name or alias of the command
     * @return the plugin command if found, otherwise null
     */
    public PluginCommand getCommand(String name) {
        return javaPlugin.getCommand(name);
    }

    public void onLoad() {}

    public void onDisable() {}

    public void onEnable() {}

    public final boolean isNaggable() {
        return javaPlugin.isNaggable();
    }

    public final void setNaggable(boolean canNag) {
        javaPlugin.setNaggable(canNag);
    }

    public Logger getLogger() {
        return javaPlugin.getLogger();
    }

    public String toString() {
        return javaPlugin.toString();
    }

    /**
     * This method provides fast access to the plugin that has {@link
     * #getProvidingPlugin(Class) provided} the given plugin class, which is
     * usually the plugin that implemented it.
     * <p>
     * An exception to this would be if plugin's jar that contained the class
     * does not extend the class, where the intended plugin would have
     * resided in a different jar / classloader.
     *
     * @param <T> a class that extends JavaPlugin
     * @param clazz the class desired
     * @return the plugin that provides and implements said class
     * @throws IllegalArgumentException if clazz is null
     * @throws IllegalArgumentException if clazz does not extend {@link
     *     JavaPlugin}
     * @throws IllegalStateException if clazz was not provided by a plugin,
     *     for example, if called with
     *     <code>JavaPlugin.getPlugin(JavaPlugin.class)</code>
     * @throws IllegalStateException if called from the static initializer for
     *     given JavaPlugin
     * @throws ClassCastException if plugin that provided the class does not
     *     extend the class
     */
    public static <T extends JavaPlugin> T getPlugin(Class<T> clazz) {
        return MultiVersionPlugin.getPlugin(clazz);
    }

    /**
     * This method provides fast access to the plugin that has provided the
     * given class.
     *
     * @param clazz a class belonging to a plugin
     * @return the plugin that provided the class
     * @throws IllegalArgumentException if the class is not provided by a
     *     JavaPlugin
     * @throws IllegalArgumentException if class is null
     * @throws IllegalStateException if called from the static initializer for
     *     given JavaPlugin
     */
    public static JavaPlugin getProvidingPlugin(Class<?> clazz) {
        return MultiVersionPlugin.getProvidingPlugin(clazz);
    }

}
