package au.com.grieve.multi_version_plugin;

import org.apache.commons.io.IOUtils;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public MultiVersionLoader(ClassLoader parent, String base, String[] versions) {
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
        this.versions = Arrays.asList(versions);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Only interested if its base package name matches us
        if (!name.startsWith(base)) {
            return super.loadClass(name, resolve);
        }

        // If parent loader already knows about this class, use that
        if (new UnlockedClassLoader(getParent()).unlockedFindLoadedClass("name") != null) {
            return super.loadClass(name, resolve);
        }

        List<String> names = new ArrayList<>();
        String definedName = name;

        // Add each Version
        for (String version : versions) {
            names.add(base + ".v" + version + definedName.substring(base.length()));
        }

        // Add base defined name as well
        names.add(definedName);

        // See if this class is already loaded
        Class <?> c = findLoadedClass(definedName);
        if (c == null) {
            c = loadAndDefineClass(names, definedName, resolve);
        }

        // Save it with JavaPlugin
        if (c != null) {
            try {
                Method setClassMethod = javaPluginLoader.getClass().getDeclaredMethod("setClass", String.class, Class.class);
                setClassMethod.setAccessible(true);
                setClassMethod.invoke(javaPluginLoader, definedName, c);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return c;
    }

    public Class<?> loadAndDefineClass(List<String> names, String definedName, boolean resolve) throws ClassNotFoundException {
        Class <?> c;

        // Define package if needed
        String packageName = getPackageName(definedName);

        if (packageName != null && getPackage(packageName) == null) {
            definePackage(packageName, null, null, null, null, null, null, null);
        }

        for (String name: names) {
            String filename = name.replaceAll("\\.", "/") + ".class";

            try (InputStream in = getParent().getResourceAsStream(filename)) {
                // Read all the bytes
                byte[] bytes = rewritePackageName(IOUtils.toByteArray(in));

                c = defineClass(definedName, bytes, 0, bytes.length);

                if (resolve) {
                    resolveClass(c);
                }

                return c;
            } catch (IOException | NullPointerException ignored) {
            }
        }

        throw new ClassNotFoundException("Unable to load class: " + names.get(0));
    }

    public byte[] rewritePackageName(byte[] bytecode) throws IOException {
        ClassReader classReader = new ClassReader(bytecode);
        ClassWriter classWriter = new ClassWriter(classReader, 0);
        String mapperName = base.replaceAll("\\.", "/");

        classReader.accept(
                new ClassRemapper(classWriter, new Remapper() {
                    @Override
                    public String map(String typeName) {
                        if (typeName.startsWith(mapperName)) {
                            // Check if version is specified and remove if needed (make life easier for the dev)
                            String [] nameParts = typeName.substring(mapperName.length() + 1).split("/");
                            if (versions.contains(nameParts[0].substring(1))) {
                                return mapperName + typeName.substring(mapperName.length() + 1 + nameParts[0].length());
                            }
                        }

                        return super.map(typeName);
                    }
                }),
                0);


        return classWriter.toByteArray();
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

    /**
     * This class allows me to call some protected functions in a classloader
     */
    private static class UnlockedClassLoader extends ClassLoader
    {
        public UnlockedClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> unlockedFindLoadedClass(String name) {
            return findLoadedClass(name);
        }
    }

}