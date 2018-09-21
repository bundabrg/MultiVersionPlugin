# MultiVersionPlugin
A MultiVersionPlugin for Bukkit/SpigotMC/PaperMC

This provides a simple way to manage the following: -
  * Multiple Versions of your API
  * Multiple Versions of Dependencies
  * Differences in code between versions
  
# How To Use

## 1. Create a new bootstrap module for your Plugin. 

Something as follows assuming your Plugin that extends JavaPlugin is called MyPlugin.
```
import au.com.grieve.multi_version_plugin.MultiVersionPlugin;

public class BootstrapPlugin extends MultiVersionPlugin {

    public BootstrapPlugin() {
        super("org.my.project",
                "MyPlugin");
    }
}
```

## 2. Update your plugin to extend VersionPlugin instead of JavaPlugin

```
public class MyPlugin extends VersionPlugin {
	@Override
	public void onEnable() {
    ...
  }
  
  @Override
  public void onDisable() {
    ...
  }
}
```

## 3. Configure your build to shade them together
You will have your existing project but it will have the BootstrapPlugin as its entrypoint. 

If your own plugin needs to access a JavaPlugin instance (for example when accessing Bukkit events etc) you can use getPlugin() instead of this.

ie:
```
Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable() {
   ...
```

## 4. Make sure it works
Right now its not MultiVersion but as long as it works like it did then we're doing well.

## 5. Now create alternative versions as seperate modules
Lets say you want to support V1.12-R3. 

* Create a separate module to hold your code for V1.12-R3. For example:
MyProject-v1.12-R3

* Copy all the code from your existing module into there. 
* Update the v1.12-R3 pom to depend on your existing module with a scope of compile. 
* Update the 1.12-R3 pom to depend on 1.12-R3 dependencies.
* Fix the code so it compiles.
* Now find all the files that have not changed between this version and the higher one, and delete them from the v1.12-R3 module.
* You should ONLY have the exact files that changed between versions. It will still compile because it can find them through the dependency to higher version.

Now the hard part. Build a multiversion jar that shades in the bootstrap and your existing module. It will also need to unpack and repack your v1.12-R3 module into the same module prefixed with the folder 'MVP/1_12_R3'.

Lets assume you have 3 classes in your project.
* org.my.project.Class1
* org.my.project.Class2
* org.my.project.Class3

To get it workign in V1.12-R3, you needed to change Class2, so it only has Class2 inside it.

When you compile your existing module, it creates a jar with the following structure inside:
```
org/my/project/Class1.class
org/my/project/Class2.class
org/my/project/Class3.class
```

If you shade in v1.12-R3, it will just overwrite one of these files so what you need to do is create an assembly that copies it in as follows:
```
MVP/1_12_R3/org/my/project/Class2.class
```

See a demo here of where I used this on Betonquest: https://github.com/bundabrg/BetonQuest/tree/feature/multiversion-reverse

# How it Works
This provides a special classloader. When you attempt to load class it will check what server version is running, then load it from a MVP/{server_version} and any versions above (in order). If the classloader can't find it, it will load it from the standard location.

This allows patching in classes based upon the loaded server. 






   
