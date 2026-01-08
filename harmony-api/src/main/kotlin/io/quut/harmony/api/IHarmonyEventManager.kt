package io.quut.harmony.api

import java.lang.invoke.MethodHandles
import java.util.ServiceLoader

/**
 * Manages the registration of scopes and the creation
 * of associated event listeners.
 *
 * @param T The scope type.
 */
interface IHarmonyEventManager<T : Any>
{
	fun registerListeners(scope: T, plugin: Any, listener: Any, lookup: MethodHandles.Lookup? = null)

	/**
	 * Un-registers the scope and its associated
	 * event listeners.
	 *
	 * @param scope The scope to un-register.
	 */
	fun unregisterListeners(scope: T)

	fun unregisterListeners(scope: T, plugin: Any)

	fun unregisterListeners(scope: T, plugin: Any, listener: Any)

	/**
	 * Represents a builder to create [IHarmonyEventManager] instances.
	 *
	 * @param T The scope type.
	 */
	interface IBuilder<T : Any>
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
			inline fun <TScope : Any, reified TEvent> IBuilder<TScope>.mapping(noinline mapper: (TEvent) -> TScope?): IBuilder<TScope> =
				this.mapping(TEvent::class.java, mapper)
		}
	}

	/**
	 * A factory that creates [IHarmonyEventManager]'s builder.
	 *
	 * @param T The scope type.
	 */
	interface IFactory<T : Any>
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
		inline fun <reified T : Any> builder(plugin: Any) = this.builder(T::class.java, plugin)

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
		fun <T : Any> builder(scopeClass: Class<T>, plugin: Any): IBuilder<T>
		{
			val factory: IFactory<T> = ServiceLoader.load(IFactory::class.java).findFirst().orElseThrow() as IFactory<T>
			return factory.builder(scopeClass, plugin)
		}
	}
}
