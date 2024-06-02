package io.quut.harmony.api

import java.lang.invoke.MethodHandles
import java.util.ServiceLoader

/**
 * Manages the registration of scopes and the creation
 * of associated event listeners.
 *
 * @param T The scope type.
 */
interface IHarmonyEventManager<T>
{
	/**
	 * Registers a new scope and creates the associated
	 * event listeners.
	 *
	 * Validation is run to ensure that all the registered
	 * events have their mapping.
	 *
	 * @param scope The scope to register.
	 * @exception UnsupportedOperationException If the validation fails.
	 */
	fun registerScope(scope: T) = this.registerScope(scope, IHarmonyScopeOptions.validate())

	/**
	 * Registers a new scope and creates the associated
	 * event listeners.
	 *
	 * When [IHarmonyScopeOptions.validate] is `true`, validation is
	 * run to ensure that all the registered events have their mapping.
	 * Otherwise, the events are simply ignored.
	 *
	 * The supplies listeners from options are appended.
	 *
	 * @param scope The scope to register.
	 * @param options The options for this scope.
	 * @exception UnsupportedOperationException If the validation fails.
	 */
	fun <TScope : T> registerScope(scope: TScope, options: IHarmonyScopeOptions<TScope>)

	/**
	 * Un-registers the scope and its associated
	 * event listeners.
	 *
	 * @param scope The scope to un-register.
	 */
	fun unregisterScope(scope: T)

	/**
	 * Represents a builder to create [IHarmonyEventManager] instances.
	 *
	 * @param T The scope type.
	 */
	interface IBuilder<T>
	{
		/**
		 * Adds an event mapper that finds the associated
		 * scope to redirect this event to.
		 *
		 * @param TEvent The event type to map.
		 * @param eventClass The event class to map.
		 * @param mapper The event mapper.
		 * @return This builder, for chaining.
		 */
		fun <TEvent> mapping(eventClass: Class<in TEvent>, mapper: (TEvent) -> T?): IBuilder<T>

		/**
		 * Adds a default listener for the associated scope
		 * that is automatically instantiated when the scope
		 * is registered.
		 *
		 * @param TScope The scope type.
		 * @param scopeClass The scope class.
		 * @param plugin The underlying platform plugin which is associated to the listener object.
		 * @param listener The factory which instantiates the listener object for the associated scope.
		 * @return This builder, for chaining.
		 */
		fun <TScope : T> listener(scopeClass: Class<in TScope>, plugin: Any, listener: (TScope) -> Any): IBuilder<T> =
			this.listener(IHarmonyEventListener.of(scopeClass, plugin, listener, null))

		/**
		 * Adds a default listener for the associated scope
		 * that is automatically instantiated when the scope
		 * is registered.
		 *
		 * @param TScope The scope type.
		 * @param scopeClass The scope class.
		 * @param plugin The underlying platform plugin which is associated to the listener object.
		 * @param listener The factory which instantiates the listener object for the associated scope.
		 * @param lookup The lookup with which to access the listener object.
		 * @return This builder, for chaining.
		 */
		fun <TScope : T> listener(scopeClass: Class<in TScope>, plugin: Any, listener: (TScope) -> Any, lookup: MethodHandles.Lookup): IBuilder<T> =
			this.listener(IHarmonyEventListener.of(scopeClass, plugin, listener, lookup))

		/**
		 * Adds a default listener for the associated scope
		 * that is automatically instantiated when the scope
		 * is registered.
		 *
		 * @param plugin The underlying platform plugin which is associated to the listener object.
		 * @param listener The factory which instantiates the listener object for the associated scope.
		 * @return This builder, for chaining.
		 */
		fun listener(plugin: Any, listener: (T) -> Any): IBuilder<T>

		/**
		 * Adds a default listener for the associated scope
		 * that is automatically instantiated when the scope
		 * is registered.
		 *
		 * @param plugin The underlying platform plugin which is associated to the listener object.
		 * @param listener The factory which instantiates the listener object for the associated scope.
		 * @param lookup The lookup with which to access the listener object.
		 * @return This builder, for chaining.
		 */
		fun listener(plugin: Any, listener: (T) -> Any, lookup: MethodHandles.Lookup): IBuilder<T>

		/**
		 * Adds a default listener for the associated scope
		 * that is automatically instantiated when the scope
		 * is registered.
		 *
		 * @param TScope The scope type.
		 * @param listener The listener.
		 * @return This builder, for chaining.
		 */
		fun <TScope : T> listener(listener: IHarmonyEventListener<TScope>): IBuilder<T>

		/**
		 * Creates a [IHarmonyEventManager] based on this builder.
		 * @return A new [IHarmonyEventManager].
		 */
		fun build(): IHarmonyEventManager<T>

		companion object
		{
			/**
			 * Adds an event mapper that finds the associated
			 * scope to redirect this event to.
			 *
			 * @param TScope The scope type.
			 * @param TEvent The event type to map.
			 * @param mapper The event mapper.
			 * @return This builder, for chaining.
			 */
			inline fun <TScope, reified TEvent> IBuilder<TScope>.mapping(noinline mapper: (TEvent) -> TScope?): IBuilder<TScope> =
				this.mapping(TEvent::class.java, mapper)
		}
	}

	/**
	 * A factory that creates [IHarmonyEventManager]'s builder.
	 *
	 * @param T The scope type.
	 */
	interface IFactory<T>
	{
		fun builder(scopeClass: Class<T>, plugin: Any): IBuilder<T>
	}

	companion object
	{
		/**
		 * Creates a [IBuilder] to get [IHarmonyEventManager]'s.
		 *
		 * @param T The scope type.
		 * @param plugin The underlying platform plugin which is associated to the event manager.
		 * @return A new [IBuilder].
		 */
		inline fun <reified T> builder(plugin: Any) = this.builder(T::class.java, plugin)

		/**
		 * Creates a [IBuilder] to get [IHarmonyEventManager]'s.
		 *
		 * @param T The scope type.
		 * @param scopeClass The scope class.
		 * @param plugin The underlying platform plugin which is associated to the event manager.
		 * @return A new [IBuilder].
		 */
		@Suppress("UNCHECKED_CAST")
		@JvmStatic
		fun <T> builder(scopeClass: Class<T>, plugin: Any): IBuilder<T>
		{
			val factory: IFactory<T> = ServiceLoader.load(IFactory::class.java).findFirst().orElseThrow() as IFactory<T>
			return factory.builder(scopeClass, plugin)
		}
	}
}
