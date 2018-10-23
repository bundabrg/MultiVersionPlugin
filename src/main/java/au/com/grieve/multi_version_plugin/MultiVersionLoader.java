package au.com.grieve.multi_version_plugin;

import org.apache.commons.io.IOUtils;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.cougaar.util.NaturalOrderComparator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * When first loaded it will pre-cache all the versioned classes with JavaPlugin so that any other plugins will
 * use the versioned classes.
 *
 * It may well be the most evil thing I've ever written.
 */

public class MultiVersionLoader extends ClassLoader {
    private final String base;
    private final JavaPluginLoader javaPluginLoader;
    private Map<String, String> versionedClasses;

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

        // Build list of versions
        List<String> versions = new ArrayList<>();
        Map<String, List<String>> versionClassFilenames = new HashMap<>();
        Comparator<String> naturalComparator = new NaturalOrderComparator<>(true);

        try (JarFile jar = new JarFile(getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath())) {
            Enumeration<JarEntry> entries = jar.entries();
            while(entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                String name = entry.getName();
                if (name.startsWith("MVP/") && !name.equals("MVP/")) {
                    // Grab the version
                    String version = name.substring(4,name.indexOf("/", 4));

                    // Only interested if version is equal or greater than current serverVersion
                    if (naturalComparator.compare(serverVersion, version) <= 0) {

                        // Add to Version list
                        if (!versions.contains(version)) {
                            versions.add(version);
                            versionClassFilenames.put(version, new ArrayList<>());
                        }

                        // If its a class, store a reference to it
                        if (name.endsWith(".class")) {
                            versionClassFilenames.get(version).add(name.substring(4 + version.length() + 1));
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

        // Sort it naturally
        versions.sort(naturalComparator);

        // Store unique list of versioned classes
        versionedClasses = new HashMap<>();

        for (String version: versions) {
            for (String classFileName : versionClassFilenames.get(version)) {
                String className = classFileName.substring(0, classFileName.length() - 6).replace("/", ".");
                if (!versionClassFilenames.containsKey(className)) {
                    versionedClasses.put(className, "MVP/" + version + "/" + classFileName);
                }
            }
        }

        // Inject all versioned classes
        for (String className : versionedClasses.keySet()) {
            Class<?> c;
            try {
                c = loadClassFromStream(className, parent.getResourceAsStream(versionedClasses.get(className)));
            } catch (IOException e) {
                continue;
            }
            if (c != null) {
                cacheClass(className, c);
            }
        }
    }

    private void cacheClass(String name, Class<?> clazz) {
        // Save it with JavaPlugin
        if (clazz != null) {
            try {
                Method setClassMethod = javaPluginLoader.getClass().getDeclaredMethod("setClass", String.class, Class.class);
                setClassMethod.setAccessible(true);
                setClassMethod.invoke(javaPluginLoader, name, clazz);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Class<?> getCachedClass(String name) {
        // Check if JavaPlugin knows about this class
        try {
            Method getClassByNameMethod = javaPluginLoader.getClass().getDeclaredMethod("getClassByName", String.class);
            getClassByNameMethod.setAccessible(true);
            return (Class<?>) getClassByNameMethod.invoke(javaPluginLoader, name);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeCachedClass(String name) {
        try {
            Method setClassMethod = javaPluginLoader.getClass().getDeclaredMethod("removeClass", String.class);
            setClassMethod.setAccessible(true);
            setClassMethod.invoke(javaPluginLoader, name);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private Class <?> loadClassFromStream(String name, InputStream stream) throws IOException {
        // Define package if needed
        String packageName = getPackageName(name);

        if (packageName != null && getPackage(packageName) == null) {
            definePackage(packageName, null, null, null, null, null, null, null);
        }

        // Read all the bytes
        byte[] bytes = IOUtils.toByteArray(stream);

        return defineClass(name, bytes, 0, bytes.length);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException();
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class <?> c = null;

        // If no versionedClasses we use normal ClassLoader
        if (versionedClasses.size() == 0) {
            return super.loadClass(name, resolve);
        }

        // Only interested if its base package name matches us
        if (!name.startsWith(base)) {
            return super.loadClass(name, resolve);
        }

        try {
            // Try load from Cache first
            c = getCachedClass(name);

            if (c != null) {
                if (!(c.getClassLoader() instanceof MultiVersionLoader)) {
                    // Kind of a hack. Cached is not through us so we replace it
                    removeCachedClass(name);
                    c = null;
                } else{
                    return c;
                }
            }

            // Try load versioned
            if (versionedClasses.containsKey(name)) {
                try {
                    c = loadClassFromStream(name, getParent().getResourceAsStream(versionedClasses.get(name)));
                    cacheClass(name, c);
                    return c;
                } catch (IOException ignored) {
                }
            }

            // Try load Standard
            try {
                c = loadClassFromStream(name, getParent().getResourceAsStream(name.replace(".", "/") + ".class"));
                cacheClass(name, c);
                return c;
            } catch (IOException ignored) {
            }

        } catch (SecurityException ignored) {
        }

        // Delegate to Parent
        return super.loadClass(name, resolve);

    }

//    /**
//     * Go through all the versions available in order. If a jar called MultiVersion-{version}.jar exists in
//     * our resources then we will look for the class inside that jar. If all else fails, we will try load the
//     * class directly.
//     */
//    private Class<?> loadVersionedClass(String name, boolean resolve) throws ClassNotFoundException {
//        Class <?> c;
//
//        // Define package if needed
//        String packageName = getPackageName(name);
//
//        if (packageName != null && getPackage(packageName) == null) {
//            definePackage(packageName, null, null, null, null, null, null, null);
//        }
//
//        byte[] bytes = null;
//
//        // Check each version
//        for (String version: versions) {
//            String filename = "MVP/" + version + "/" + name.replaceAll("\\.", "/") + ".class";
//            try (InputStream in = getParent().getResourceAsStream(filename)) {
//
//
//                // Read all the bytes
//                bytes = IOUtils.toByteArray(in);
//                break;
//            } catch (IOException | NullPointerException ignored) {
//            }
//        }
//
//        // Check Latest
//        if (bytes == null) {
//            String filename = name.replaceAll("\\.", "/") + ".class";
//            try (InputStream in = getParent().getResourceAsStream(filename)) {
//                // Read all the bytes
//                bytes = IOUtils.toByteArray(in);
//            } catch (IOException | NullPointerException ignored) {
//            }
//        }
//
//        if (bytes == null) {
//            throw new ClassNotFoundException("Unable to load class: " + name);
//        }
//
//        c = defineClass(name, bytes, 0, bytes.length);
//
//        if (resolve) {
//            resolveClass(c);
//        }
//
//        return c;
//    }

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