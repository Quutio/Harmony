package io.quut.harmony.sponge

import org.spongepowered.api.event.Event
import org.spongepowered.common.event.manager.RegisteredListener
import org.spongepowered.common.event.manager.SpongeEventManager
import org.spongepowered.plugin.PluginContainer
import java.lang.invoke.MethodHandles
import java.lang.reflect.Field
import java.lang.reflect.Method

internal class ScopedEventManager
{
	private val eventManager: SpongeEventManager =
		Class.forName("${String(charArrayOf('o', 'r', 'g'))}.spongepowered.vanilla.launch.event.VanillaEventManager").getDeclaredConstructor().newInstance() as SpongeEventManager

	internal val lock: Any
	internal val listeners: Collection<RegisteredListener<*>>

	init
	{
		val lockField: Field = SpongeEventManager::class.java.getDeclaredField("lock")
		lockField.isAccessible = true

		val handlersByEventField: Field = SpongeEventManager::class.java.getDeclaredField("handlersByEvent")
		handlersByEventField.isAccessible = true

		this.lock = lockField.get(this.eventManager)

		val multiMap: Any = handlersByEventField.get(this.eventManager)

		@Suppress("UNCHECKED_CAST")
		this.listeners = multiMap.javaClass.getDeclaredMethod("values").invoke(multiMap) as Collection<RegisteredListener<*>>
	}

	internal fun register(plugin: PluginContainer, listener: Any, lookup: MethodHandles.Lookup?)
	{
		if (lookup != null)
		{
			this.eventManager.registerListeners(plugin, listener, lookup)
		}
		else
		{
			this.eventManager.registerListeners(plugin, listener)
		}
	}

	@Suppress("UNCHECKED_CAST")
	internal fun register(listener: RegisteredListener<*>)
	{
		ScopedEventManager.REGISTER_METHOD.invoke(this.eventManager, listener)
	}

	internal fun unregister(listener: RegisteredListener<*>)
	{
		this.eventManager.unregisterListeners(listener.handle)
	}

	internal fun post(event: Event) = this.eventManager.post(event)

	companion object
	{
		@JvmStatic
		val REGISTER_METHOD: Method = SpongeEventManager::class.java.getDeclaredMethod("register", RegisteredListener::class.java)

		init
		{
			this.REGISTER_METHOD.isAccessible = true
		}
	}
}
