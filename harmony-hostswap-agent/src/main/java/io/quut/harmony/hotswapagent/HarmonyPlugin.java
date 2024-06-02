package io.quut.harmony.hotswapagent;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.plugin.sponge.SpongePlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Plugin(name = "Harmony",
		description = "Refreshes IHarmonyEventManager when scopes have their listeners updated",
		testedVersions = { "1.0.0 "},
		expectedVersions = { "1.0.0" })
public final class HarmonyPlugin
{
	@Init
	PluginManager pluginManager;

	@Init
	Scheduler scheduler;

	@Init
	ClassLoader appClassLoader;

	private final Set<Object> eventManagers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
	private final AtomicBoolean callbackHookAdded = new AtomicBoolean(false);

	@OnClassLoadEvent(classNameRegexp = "io.quut.harmony.sponge.SpongeHarmonyEventManager")
	public static void registerManager(final CtClass ctClass, final ClassPool classPool) throws NotFoundException, CannotCompileException
	{
		final StringBuilder initialization = new StringBuilder("{");
		initialization.append(PluginManagerInvoker.buildInitializePlugin(HarmonyPlugin.class));
		initialization.append(PluginManagerInvoker.buildCallPluginMethod(HarmonyPlugin.class, "registerEventManager", "this", "java.lang.Object"));
		initialization.append("}");

		ctClass.getDeclaredConstructor(new CtClass[]
		{
			classPool.get("java.lang.Class"),
			classPool.get("org.spongepowered.plugin.PluginContainer"),
			classPool.get("java.util.Map"),
			classPool.get("java.util.Map"),
		}).insertAfter(initialization.toString());
	}

	public void registerEventManager(final Object eventManager)
	{
		if (this.callbackHookAdded.compareAndSet(false, true))
		{
			this.pluginManager.getPlugin(SpongePlugin.class, eventManager.getClass().getClassLoader()).addCallback(clazz ->
					this.scheduler.scheduleCommand(new ReloadListenersCommand(this.appClassLoader, this.eventManagers, clazz)));
		}

		this.eventManagers.add(eventManager);
	}
}
