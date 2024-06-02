package io.quut.harmony.sponge

import io.quut.harmony.api.IHarmonyEventListener
import io.quut.harmony.api.IHarmonyEventManager
import io.quut.harmony.api.IHarmonyScopeOptions
import org.spongepowered.api.Sponge
import org.spongepowered.api.event.Event
import org.spongepowered.api.event.EventListener
import org.spongepowered.api.event.EventListenerRegistration
import org.spongepowered.api.event.Order
import org.spongepowered.common.event.manager.EventType
import org.spongepowered.common.event.manager.RegisteredListener
import org.spongepowered.plugin.PluginContainer
import java.lang.invoke.MethodHandles
import java.util.PriorityQueue

internal class SpongeHarmonyEventManager<T>(
	private val scopeClass: Class<T>,
	private val plugin: PluginContainer,
	private val mappings: MutableMap<Class<*>, (Any) -> T?>,
	private val listeners: MutableMap<Class<*>, MutableSet<IHarmonyEventListener<T>>>) : IHarmonyEventManager<T>
{
	private val listenerRegistrations: MutableMap<Triple<EventType<*>, Order, Boolean>, ListenerRegistration> = hashMapOf()
	private val scopes: MutableMap<T, ScopedEventManager> = hashMapOf()

	override fun <TScope : T> registerScope(scope: TScope, options: IHarmonyScopeOptions<TScope>)
	{
		val scopeEventManager = ScopedEventManager()
		scopeEventManager.registerAll(scope, options.listeners)

		this.listeners.filter { (key) -> key.isInstance(scope) }.forEach()
		{ (_, value) ->
			scopeEventManager.registerAll(scope, value)
		}

		if (options.validate)
		{
			// Validate first before modifying global state
			synchronized(scopeEventManager.lock)
			{
				scopeEventManager.listeners.forEach()
				{ listener ->
					this.findMapping(listener.eventType.type) ?: throw UnsupportedOperationException("Unmapped event ${listener.eventType.type}")
				}
			}
		}

		synchronized(scopeEventManager.lock)
		{
			scopeEventManager.listeners.forEach { listener -> this.register(listener) }
		}

		this.scopes[scope] = scopeEventManager
	}

	override fun unregisterScope(scope: T)
	{
		val scopeEventManager: ScopedEventManager = this.scopes.remove(scope) ?: return

		synchronized(scopeEventManager.lock)
		{
			scopeEventManager.listeners.forEach { listener -> this.unregister(listener) }
		}
	}

	private fun register(listener: RegisteredListener<*>)
	{
		val mapping: (Event) -> T? = this.findMapping(listener.eventType.type) ?: return

		val listenerRegistration: ListenerRegistration = this.listenerRegistrations.computeIfAbsent(
			Triple(listener.eventType, listener.order, listener.isBeforeModifications))
		{ (eventType, order, isBeforeModifications) ->
			val listener = EventListener()
			{ e: Event ->
				val scope: T = mapping.invoke(e) ?: return@EventListener

				this.scopes[scope]?.post(e)
			}

			Sponge.game().eventManager().registerListener(
				EventListenerRegistration.builder(eventType.type)
					.plugin(this.plugin)
					.order(order)
					.beforeModifications(isBeforeModifications)
					.listener(listener)
					.build())

			return@computeIfAbsent ListenerRegistration(listener)
		}

		listenerRegistration.add(listener)
	}

	private fun findMapping(eventType: Class<*>): ((Event) -> T?)?
	{
		this.mappings[eventType]?.let { mapping -> return mapping }

		val hierarchy: PriorityQueue<Pair<Class<*>, Int>> = PriorityQueue(Comparator.comparingInt { (_, priority) -> priority })
		hierarchy.add(Pair(eventType, 0))

		while (hierarchy.isNotEmpty())
		{
			val (child: Class<*>, priority: Int) = hierarchy.poll()

			this.mappings[child]?.let { mapping -> return mapping }

			child.interfaces.forEach { i -> hierarchy.add(Pair(i, priority + 1)) }
		}

		return null
	}

	private fun unregister(listener: RegisteredListener<*>)
	{
		val listenerRegistration: ListenerRegistration =
			this.listenerRegistrations[Triple(listener.eventType, listener.order, listener.isBeforeModifications)] ?: return

		listenerRegistration.remove(listener)

		if (listenerRegistration.isEmpty())
		{
			Sponge.game().eventManager().unregisterListeners(listenerRegistration.listener)
		}
	}

	private class ListenerRegistration(val listener: EventListener<*>)
	{
		private val listeners: MutableSet<RegisteredListener<*>> = hashSetOf()

		fun add(listener: RegisteredListener<*>)
		{
			this.listeners.add(listener)
		}

		fun remove(listener: RegisteredListener<*>)
		{
			this.listeners.remove(listener)
		}

		fun isEmpty(): Boolean = this.listeners.isEmpty()
	}

	internal class Builder<T>(private val scopeClass: Class<T>, private val plugin: PluginContainer) : IHarmonyEventManager.IBuilder<T>
	{
		private val mappings: MutableMap<Class<*>, (Any) -> T?> = hashMapOf()
		private val listeners: MutableMap<Class<*>, MutableSet<IHarmonyEventListener<T>>> = hashMapOf()

		@Suppress("UNCHECKED_CAST")
		override fun <TEvent> mapping(eventClass: Class<in TEvent>, mapper: (TEvent) -> T?): Builder<T>
		{
			this.mappings.putIfAbsent(eventClass, mapper as (Any) -> T?)
			return this
		}

		override fun listener(plugin: Any, listener: (T) -> Any): IHarmonyEventManager.IBuilder<T>
		{
			return this.listener(IHarmonyEventListener.of(this.scopeClass, plugin, listener, null))
		}

		override fun listener(plugin: Any, listener: (T) -> Any, lookup: MethodHandles.Lookup): IHarmonyEventManager.IBuilder<T>
		{
			return this.listener(IHarmonyEventListener.of(this.scopeClass, plugin, listener, lookup))
		}

		@Suppress("UNCHECKED_CAST")
		override fun <TScope : T> listener(listener: IHarmonyEventListener<TScope>): IHarmonyEventManager.IBuilder<T>
		{
			this.listeners.computeIfAbsent(scopeClass) { hashSetOf() }.add(listener as IHarmonyEventListener<T>)
			return this
		}

		override fun build(): IHarmonyEventManager<T>
		{
			return SpongeHarmonyEventManager(this.scopeClass, this.plugin, this.mappings, this.listeners)
		}
	}
}
