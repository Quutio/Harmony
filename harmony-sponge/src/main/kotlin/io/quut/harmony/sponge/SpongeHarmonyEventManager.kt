package io.quut.harmony.sponge

import io.quut.harmony.api.IHarmonyEventManager
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal class SpongeHarmonyEventManager<T : Any>(
	private val scopeClass: Class<T>,
	private val plugin: PluginContainer,
	private val mappings: Map<Class<*>, (Any) -> T?>) : IHarmonyEventManager<T>
{
	private val listenerRegistrations: ConcurrentMap<Triple<EventType<*>, Order, Boolean>, ListenerRegistration> = ConcurrentHashMap()

	private val scopes: ConcurrentMap<T, ScopeData> = ConcurrentHashMap()

	override fun registerListeners(scope: T, plugin: Any, listener: Any, lookup: MethodHandles.Lookup?)
	{
		val collection = ScopedEventManager()
		collection.register(plugin as PluginContainer, listener, lookup)

		val scopeData: ScopeData = this.scopes.computeIfAbsent(scope) { ScopeData(this) }
		scopeData.register(plugin, listener, collection)
	}

	override fun unregisterListeners(scope: T)
	{
		this.scopes.remove(scope)?.unregister()
	}

	override fun unregisterListeners(scope: T, plugin: Any)
	{
		this.scopes[scope]?.unregister(plugin)
	}

	override fun unregisterListeners(scope: T, plugin: Any, listener: Any)
	{
		this.scopes[scope]?.unregister(plugin, listener)
	}

	private fun register(listener: RegisteredListener<*>)
	{
		val mapping: (Any) -> T? = this.findMapping(listener.eventType.type) ?: return

		val key = listenerKey(listener)

		while (true)
		{
			this.listenerRegistrations[key]?.let()
			{ registration ->
				if (registration.add(listener))
				{
					return
				}

				this.listenerRegistrations.remove(key, registration)
			}

			val registration = ListenerRegistration { registration -> this.createEventListener(registration, mapping, key.second, key.third) }
			registration.add(listener)

			this.listenerRegistrations.putIfAbsent(key, registration)?.let()
			{ registration ->
				if (registration.add(listener))
				{
					return
				}

				this.listenerRegistrations.remove(key, registration)

				continue
			}

			registration.init(listener.eventType.type, this.plugin, listener.order, listener.isBeforeModifications)
		}
	}

	private fun createEventListener(registration: ListenerRegistration, mapping: (Any) -> T?, order: Order, isBeforeModifications: Boolean): EventListener<Event> =
		EventListener()
		{ e ->
			if (!registration.closed)
			{
				val scope: T = mapping(e) ?: return@EventListener
				val scopeData: ScopeData = this.scopes[scope] ?: return@EventListener

				scopeData.handleEvent(scope, e, order, isBeforeModifications)
			}
		}

	private fun findMapping(eventType: Class<*>): ((Any) -> T?)?
	{
		this.mappings[eventType]?.let { value -> return value }

		walkHierarchy(eventType) { child -> this.mappings[child]?.let { value -> return value } }

		return null
	}

	private fun unregister(listener: RegisteredListener<*>)
	{
		val listenerRegistration: ListenerRegistration = this.listenerRegistrations[listenerKey(listener)] ?: return

		if (listenerRegistration.remove(listener))
		{
			this.listenerRegistrations.remove(listenerKey(listener), listenerRegistration)
		}
	}

	private class ListenerRegistration(listener: (ListenerRegistration) -> EventListener<Event>)
	{
		private val listener: EventListener<Event> = listener(this)
		private val listeners: MutableSet<RegisteredListener<*>> = hashSetOf()

		@Volatile
		var closed: Boolean = false
			private set

		fun init(eventType: Class<out Event>, plugin: PluginContainer, order: Order, isBeforeModifications: Boolean)
		{
			synchronized(this)
			{
				if (this.closed)
				{
					return
				}

				Sponge.game().eventManager().registerListener(
					EventListenerRegistration.builder(eventType)
						.plugin(plugin)
						.order(order)
						.beforeModifications(isBeforeModifications)
						.listener(this.listener)
						.build())
			}
		}

		fun add(listener: RegisteredListener<*>): Boolean
		{
			synchronized(this)
			{
				if (this.closed)
				{
					return false
				}

				this.listeners.add(listener)

				return true
			}
		}

		fun remove(listener: RegisteredListener<*>): Boolean
		{
			synchronized(this)
			{
				if (this.listeners.remove(listener) &&
					this.listeners.isEmpty())
				{
					this.closed = true

					Sponge.game().eventManager().unregisterListeners(this.listener)

					return true
				}

				return false
			}
		}
	}

	private class ScopeData(private val root: SpongeHarmonyEventManager<*>)
	{
		private val eventManagers: Array<ScopedEventManager> = createEventManagers()

		private val registrations: MutableList<ListenerRegistration> = mutableListOf()

		fun handleEvent(scope: Any, event: Event, order: Order, isBeforeModifications: Boolean)
		{
			this.eventManagers[orderId(order, isBeforeModifications)].post(event)
		}

		fun register(plugin: Any, listener: Any, collection: ScopedEventManager)
		{
			synchronized(this)
			{
				this.registrations.add(ListenerRegistration(plugin, listener, collection))

				collection.listeners.forEach(this::register)
			}
		}

		private fun register(listener: RegisteredListener<*>)
		{
			this.eventManagers[orderId(listener)].register(listener)

			this.root.register(listener)
		}

		fun unregister(plugin: Any) = this.unregister { i -> i.plugin == plugin }

		fun unregister(plugin: Any, listener: Any) = this.unregister { i -> i.plugin == plugin && i.listener == listener }

		private fun unregister(filter: (ListenerRegistration) -> Boolean)
		{
			synchronized(this)
			{
				val iterator: MutableIterator<ListenerRegistration> = this.registrations.iterator()
				while (iterator.hasNext())
				{
					val registration: ListenerRegistration = iterator.next()
					if (filter(registration))
					{
						registration.collection.listeners.forEach(this::unregister)

						iterator.remove()
					}
				}
			}
		}

		private fun unregister(listener: RegisteredListener<*>)
		{
			this.eventManagers[orderId(listener)].unregister(listener)

			this.root.unregister(listener)
		}

		fun unregister()
		{
			synchronized(this)
			{
				this.eventManagers.forEach { eventManager -> eventManager.listeners.forEach(this.root::unregister) }
			}
		}

		private class ListenerRegistration(val plugin: Any, val listener: Any, val collection: ScopedEventManager)

		companion object
		{
			@JvmStatic
			private val MAX_ORDER: Order = Order.entries.maxBy { it.ordinal }

			private fun orderId(listener: RegisteredListener<*>) =
				this.orderId(listener.order, listener.isBeforeModifications)

			private fun orderId(order: Order, isBeforeModifications: Boolean) =
				order.ordinal + if (isBeforeModifications) this.MAX_ORDER.ordinal + 1 else 0

			private fun createEventManagers(): Array<ScopedEventManager> =
				Array(this.orderId(this.MAX_ORDER, true) + 1) { ScopedEventManager() }
		}
	}

	internal class Builder<T : Any>(private val scopeClass: Class<T>, private val plugin: PluginContainer) : IHarmonyEventManager.IBuilder<T>
	{
		private val mappings: MutableMap<Class<*>, (Any) -> T?> = hashMapOf()

		@Suppress("UNCHECKED_CAST")
		override fun <TEvent> mapping(eventClass: Class<in TEvent>, mapper: (TEvent) -> T?): Builder<T>
		{
			this.mappings.putIfAbsent(eventClass, mapper as (Any) -> T?)

			return this
		}

		override fun build(): IHarmonyEventManager<T> =
			SpongeHarmonyEventManager(this.scopeClass, this.plugin, this.mappings.toMap())
	}

	companion object
	{
		internal inline fun walkHierarchy(clazz: Class<*>, consumer: (Class<*>) -> Unit)
		{
			val hierarchy: PriorityQueue<Pair<Class<*>, Int>> = PriorityQueue(Comparator.comparingInt { (_, priority) -> priority })
			hierarchy.add(Pair(clazz, 0))

			while (hierarchy.isNotEmpty())
			{
				val (child: Class<*>, priority: Int) = hierarchy.poll()

				consumer(child)

				child.superclass?.let { i -> hierarchy.add(Pair(i, priority + 1)) }
				child.interfaces.forEach { i -> hierarchy.add(Pair(i, priority + 2)) }
			}
		}

		private fun listenerKey(listener: RegisteredListener<*>): Triple<EventType<*>, Order, Boolean> =
			Triple(listener.eventType, listener.order, listener.isBeforeModifications)
	}
}
