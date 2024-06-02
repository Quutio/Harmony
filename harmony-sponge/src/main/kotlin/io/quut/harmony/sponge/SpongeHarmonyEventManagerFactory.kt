package io.quut.harmony.sponge

import io.quut.harmony.api.IHarmonyEventManager
import org.spongepowered.plugin.PluginContainer

class SpongeHarmonyEventManagerFactory<T> : IHarmonyEventManager.IFactory<T>
{
	override fun builder(scopeClass: Class<T>, plugin: Any): IHarmonyEventManager.IBuilder<T> =
		SpongeHarmonyEventManager.Builder(scopeClass, plugin as PluginContainer)
}
