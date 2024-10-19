package io.quut.harmony.sponge

import io.quut.harmony.api.IHarmonyEventListener
import org.spongepowered.api.event.Event
import org.spongepowered.api.event.EventListener
import org.spongepowered.api.event.EventListenerRegistration
import org.spongepowered.common.event.manager.RegisteredListener
import org.spongepowered.common.event.manager.SpongeEventManager
import org.spongepowered.plugin.PluginContainer
import java.lang.reflect.Field

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

	internal fun <T : Any> registerAll(scope: T, listeners: Collection<IHarmonyEventListener<T>>)
	{
		listeners.forEach { listener -> this.register(scope, listener) }
	}

	private fun <T : Any> register(scope: T, listener: IHarmonyEventListener<T>)
	{
		if (listener.lookup != null)
		{
			this.eventManager.registerListeners(listener.plugin as PluginContainer, listener.listener.invoke(scope), listener.lookup)
		}
		else
		{
			this.eventManager.registerListeners(listener.plugin as PluginContainer, listener.listener.invoke(scope))
		}
	}

	@Suppress("UNCHECKED_CAST")
	internal fun register(listener: RegisteredListener<*>)
	{
		this.eventManager.registerListener(
			EventListenerRegistration.builder(listener.eventType.type)
				.plugin(listener.plugin)
				.order(listener.order)
				.beforeModifications(listener.isBeforeModifications)
				.listener(ScopedEventManager.registeredListenerListenerField.get(listener) as EventListener<Event>)
				.build())
	}

	internal fun unregister(listener: RegisteredListener<*>)
	{
		this.eventManager.unregisterListeners(listener.handle)
	}

	internal fun post(event: Event) = this.eventManager.post(event)

	companion object
	{
		val registeredListenerListenerField: Field = RegisteredListener::class.java.getDeclaredField("listener")

		init
		{
			this.registeredListenerListenerField.isAccessible = true
		}
	}
}
