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

internal class SpongeHarmonyEventManager<T : Any>(
	private val scopeClass: Class<T>,
	private val plugin: PluginContainer,
	private val mappings: MutableMap<Class<*>, (Any) -> T?>,
	private val parentMappings: MutableMap<Class<*>, ParentMappingData<T>>,
	private val listeners: MutableMap<Class<in T>, MutableSet<IHarmonyEventListener<T>>>) : IHarmonyEventManager<T>
{
	private val listenerRegistrations: MutableMap<Triple<EventType<*>, Order, Boolean>, ListenerRegistration> = hashMapOf()
	private val unmappedRegisteredListener: MutableSet<RegisteredListener<*>> = mutableSetOf()

	private val scopes: MutableMap<T, ScopeData> = hashMapOf()

	override fun <TScope : T> registerScope(scope: TScope, options: IHarmonyScopeOptions<TScope>)
	{
		val childMappings: Map<Class<*>, (Any, Any) -> Any?>
		if (options.child != null)
		{
			childMappings = this.computeChildMappings(scope.javaClass, options.child as SpongeHarmonyEventManager<*>)

			// Validate first before modifying global state
			if (options.validate)
			{
				childMappings.forEach()
				{ (eventType) ->
					this.findMapping(eventType) ?: throw UnsupportedOperationException("Unmapped event $eventType")
				}
			}
		}
		else
		{
			childMappings = emptyMap()
		}

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
					this.findMapping(listener.eventType.type) ?: run()
					{
						SpongeHarmonyEventManager.walkHierarchy(listener.eventType.type)
						{ child ->
							this.parentMappings.values.forEach()
							{ map ->
								if (map.defaultMapping != null)
								{
									return@run
								}

								map.mappings[child]?.let { return@run }
							}
						}

						throw UnsupportedOperationException("Unmapped event ${listener.eventType.type}")
					}
				}
			}
		}

		synchronized(scopeEventManager.lock)
		{
			scopeEventManager.listeners.forEach(this::register)

			if (options.child != null)
			{
				val child = options.child as SpongeHarmonyEventManager<*>

				child.unmappedRegisteredListener.forEach(this::register)
			}
		}

		this.scopes[scope] = ScopeData(scopeEventManager, options.child as? SpongeHarmonyEventManager<*>, childMappings)
	}

	override fun unregisterScope(scope: T)
	{
		val scopeData: ScopeData = this.scopes.remove(scope) ?: return

		scopeData.unregister(this::unregister)
	}

	private fun register(listener: RegisteredListener<*>)
	{
		val mapping: (Any) -> T? = this.findMapping(listener.eventType.type) ?: run()
		{
			this.unmappedRegisteredListener.add(listener)
			return
		}

		val listenerRegistration: ListenerRegistration = this.listenerRegistrations.computeIfAbsent(
			Triple(listener.eventType, listener.order, listener.isBeforeModifications))
		{ (eventType, order, isBeforeModifications) ->
			val listener = EventListener()
			{ e: Event ->
				val scope: T = mapping(e) ?: return@EventListener
				val scopeData: ScopeData = this.scopes[scope] ?: return@EventListener

				scopeData.handleEvent(scope, e)
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

	private fun findMapping(eventType: Class<*>): ((Any) -> T?)?
	{
		this.mappings[eventType]?.let { value -> return value }

		SpongeHarmonyEventManager.walkHierarchy(eventType) { child -> this.mappings[child]?.let { value -> return value } }

		return null
	}

	private fun computeChildMappings(scopeType: Class<*>, manager: SpongeHarmonyEventManager<*>): Map<Class<*>, (Any, Any) -> Any?>
	{
		val mappings: MutableMap<Class<*>, (Any, Any) -> Any?> = hashMapOf()
		var defaultMapping: ((Any) -> Any?)? = null

		SpongeHarmonyEventManager.walkHierarchy(scopeType)
		{ child ->
			manager.parentMappings[child]?.let()
			{ mapping ->
				mapping.mappings.forEach(mappings::putIfAbsent)

				if (defaultMapping == null)
				{
					defaultMapping = mapping.defaultMapping
				}
			}
		}

		if (defaultMapping != null)
		{
			this.mappings.keys.forEach { key -> mappings.putIfAbsent(key) { scope, _ -> defaultMapping!!(scope) } }
		}

		return mappings
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

	internal class ParentMappingData<T>(val mappings: MutableMap<Class<*>, (Any, Any) -> T?> = hashMapOf(), var defaultMapping: ((Any) -> T?)? = null)

	private class ScopeData(
		private val eventManager: ScopedEventManager,
		private val child: SpongeHarmonyEventManager<*>?,
		private val childMappings: Map<Class<*>, (Any, Any) -> Any?>)
	{
		private val mappingCache: MutableMap<Class<*>, ((Any, Any) -> Any?)?> = hashMapOf()

		fun handleEvent(scope: Any, event: Event)
		{
			this.eventManager.post(event)

			val mapping: (Any, Any) -> Any? = synchronized(this.mappingCache)
			{
				this.mappingCache.computeIfAbsent(event.javaClass)
				{ key ->
					SpongeHarmonyEventManager.walkHierarchy(key) { child -> this.childMappings[child]?.let { return@computeIfAbsent it } }

					return@computeIfAbsent null
				}
			} ?: return

			val childScope: Any = mapping(scope, event) ?: return
			val scopeData: ScopeData = this.child!!.scopes[childScope] ?: return

			scopeData.eventManager.post(event)
		}

		fun unregister(unregisterCallback: (RegisteredListener<*>) -> Unit)
		{
			synchronized(this.eventManager.lock)
			{
				this.eventManager.listeners.forEach(unregisterCallback)

				if (this.child != null)
				{
					this.child.unmappedRegisteredListener.forEach(unregisterCallback)
				}
			}
		}
	}

	internal class Builder<T : Any>(private val scopeClass: Class<T>, private val plugin: PluginContainer) : IHarmonyEventManager.IBuilder<T>
	{
		private val mappings: MutableMap<Class<*>, (Any) -> T?> = hashMapOf()
		private val parentMappings: MutableMap<Class<*>, ParentMappingData<T>> = hashMapOf()
		private val listeners: MutableMap<Class<in T>, MutableSet<IHarmonyEventListener<T>>> = hashMapOf()

		@Suppress("UNCHECKED_CAST")
		override fun <TEvent> mapping(eventClass: Class<in TEvent>, mapper: (TEvent) -> T?): Builder<T>
		{
			this.mappings.putIfAbsent(eventClass, mapper as (Any) -> T?)
			return this
		}

		@Suppress("UNCHECKED_CAST")
		override fun <TParentScope> parentMapping(parentScopeClass: Class<in TParentScope>, mapper: (TParentScope) -> T?): IHarmonyEventManager.IBuilder<T>
		{
			this.parentMappings.computeIfAbsent(parentScopeClass) { ParentMappingData() }.defaultMapping = mapper as (Any) -> T?
			return this
		}

		@Suppress("UNCHECKED_CAST")
		override fun <TParentScope, TEvent> parentMapping(
			parentScopeClass: Class<in TParentScope>,
			eventClass: Class<in TEvent>,
			mapper: (TParentScope, TEvent) -> T?): IHarmonyEventManager.IBuilder<T>
		{
			this.parentMappings.computeIfAbsent(parentScopeClass) { ParentMappingData() }.mappings.putIfAbsent(eventClass, mapper as (Any, Any) -> T?)
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
			return SpongeHarmonyEventManager(this.scopeClass, this.plugin, this.mappings, this.parentMappings, this.listeners)
		}
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
	}
}
