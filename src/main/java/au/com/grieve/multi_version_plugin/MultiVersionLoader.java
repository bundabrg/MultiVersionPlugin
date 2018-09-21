package au.com.grieve.multi_version_plugin;

import org.apache.commons.io.IOUtils;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.cougaar.util.NaturalOrderComparator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This ClassLoader allows a plugin to load their classes in a versioning method. It will scan through the
 * different versions of packages available and if found will remap and rewrite the class on the fly so that it
 * looks and behaves like the class it is overwriting.
 *
 * It makes use of JavaPluginLoader's capability of caching already loaded classes to extend this behaviour to
 * other plugins that will make use of this plugin.
 *
 * It may well be the most evil thing I've ever written.
 */

public class MultiVersionLoader extends ClassLoader {
    private final String base;
    private final List<String> versions;
    private final JavaPluginLoader javaPluginLoader;

    public MultiVersionLoader(ClassLoader parent, String base, String serverVersion) {
        super(parent);

        Class<?> pluginClassLoader;

        // Make sure parent is a PluginClassLoader and Extract the JavaPluginLoader
        try {
            pluginClassLoader = Class.forName("org.bukkit.plugin.java.PluginClassLoader");
            if (!pluginClassLoader.isAssignableFrom(parent.getClass()))  {
                throw new RuntimeException("MultiVersionLoader's parent MUST inherit from PluginClassLoader");
            }

            Field loaderField = pluginClassLoader.getDeclaredField("loader");
            loaderField.setAccessible(true);
            javaPluginLoader = (JavaPluginLoader) loaderField.get(parent);

        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Invalid Parent", e);
        }

        this.base = base;

        // Build list of available versions
        versions = new ArrayList<>();
        Comparator<String> naturalComparator = new NaturalOrderComparator<>(true);

        try (JarFile jar = new JarFile(getClass().getProtectionDomain().getCodeSource().getLocation().getPath())) {
            Enumeration<JarEntry> entries = jar.entries();
            while(entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    continue;
                }

                String name = entries.nextElement().getName();
                if (name.startsWith("MVP/") && !name.equals("MVP/")) {
                    String version = name.substring(4,name.indexOf("/",4));

                    // Only interested if version is equal or greater than current serverVersion
                    if (!versions.contains(version) && naturalComparator.compare(serverVersion, version) <= 0) {
                        versions.add(version);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Sort it naturally
        versions.sort(naturalComparator);

    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException();
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class <?> c = null;

        // Only interested if its base package name matches us
        if (!name.startsWith(base)) {
            return super.loadClass(name, resolve);
        }

        try {
            // Commented as there's a chance someone holds the wrong reference. Maybe there's a way to do it?
//            // Check if JavaPlugin knows about this class
//            try {
//                Method getClassByNameMethod = javaPluginLoader.getClass().getDeclaredMethod("getClassByName", String.class);
//                getClassByNameMethod.setAccessible(true);
//                c = (Class<?>) getClassByNameMethod.invoke(javaPluginLoader, name);
//            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
//                throw new RuntimeException(e);
//            }

            // Try to Load the class ourselves
            if (c == null) {
                c = loadVersionedClass(name, resolve);
            }

            // Save it with JavaPlugin
            if (c != null) {
                try {
                    Method setClassMethod = javaPluginLoader.getClass().getDeclaredMethod("setClass", String.class, Class.class);
                    setClassMethod.setAccessible(true);
                    setClassMethod.invoke(javaPluginLoader, name, c);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }

                return c;
            }

            return super.loadClass(name, resolve);

        } catch (ClassNotFoundException | SecurityException e) {
            // Delegate to Parent
            return super.loadClass(name, resolve);
        }
    }

    /**
     * Go through all the versions available in order. If a jar called MultiVersion-{version}.jar exists in
     * our resources then we will look for the class inside that jar. If all else fails, we will try load the
     * class directly.
     */
    private Class<?> loadVersionedClass(String name, boolean resolve) throws ClassNotFoundException {
        Class <?> c;

        // Define package if needed
        String packageName = getPackageName(name);

        if (packageName != null && getPackage(packageName) == null) {
            definePackage(packageName, null, null, null, null, null, null, null);
        }

        byte[] bytes = null;

        // Check each version
        for (String version: versions) {
            String filename = "MVP/" + version + "/" + name.replaceAll("\\.", "/") + ".class";
            try (InputStream in = getParent().getResourceAsStream(filename)) {


                // Read all the bytes
                bytes = IOUtils.toByteArray(in);
                break;
            } catch (IOException | NullPointerException ignored) {
            }
        }

        // Check Latest
        if (bytes == null) {
            String filename = name.replaceAll("\\.", "/") + ".class";
            try (InputStream in = getParent().getResourceAsStream(filename)) {
                // Read all the bytes
                bytes = IOUtils.toByteArray(in);
            } catch (IOException | NullPointerException ignored) {
            }
        }

        if (bytes == null) {
            throw new ClassNotFoundException("Unable to load class: " + name);
        }

        c = defineClass(name, bytes, 0, bytes.length);

        if (resolve) {
            resolveClass(c);
        }

        return c;
    }

    private String getPackageName(String className) {
        int i = className.lastIndexOf('.');
        if (i > 0) {
            return className.substring(0, i);
        } else {
            // No package name, e.g. LsomeClass;
            return null;
        }
    }

}