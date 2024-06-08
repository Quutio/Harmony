package io.quut.harmony.api

import java.lang.invoke.MethodHandles

/**
 * Represents the composition of a listener factory for
 * particular scope type. The underlying platform mechanism
 * is used to register the listener object.
 *
 * @param T The scope type.
 */
interface IHarmonyEventListener<in T : Any>
{
	/**
	 * The scope type this listener can handle.
	 */
	val scopeClass: Class<in T>

	/**
	 * The underlying platform plugin that is associated
	 * to the listener object after its creation.
	 */
	val plugin: Any

	/**
	 * The factory that instantiates the listener object for
	 * the associated scope.
	 */
	val listener: (T) -> Any

	/**
	 * The lookup used for privileged access to the listener object.
	 */
	val lookup: MethodHandles.Lookup?

	companion object
	{
		/**
		 * Creates a [IHarmonyEventListener].
		 *
		 * @param scopeClass The scope type this listener can handle.
		 * @param plugin The underlying platform plugin.
		 * @param listener The listener factory.
		 * @param lookup The lookup with which to access the listener object.
		 * @return A new [IHarmonyEventListener].
		 */
		@JvmStatic
		@JvmOverloads
		fun <T : Any> of(scopeClass: Class<in T>, plugin: Any, listener: (T) -> Any, lookup: MethodHandles.Lookup? = null): IHarmonyEventListener<T> =
			Impl(scopeClass, plugin, listener, lookup)

		/**
		 * Creates a [IHarmonyEventListener].
		 *
		 * @param T The scope type this listener can handle.
		 * @param plugin The underlying platform plugin.
		 * @param listener The listener factory.
		 * @param lookup The lookup with which to access the listener object.
		 * @return A new [IHarmonyEventListener].
		 */
		inline fun <reified T : Any> of(plugin: Any, noinline listener: (T) -> Any, lookup: MethodHandles.Lookup? = null): IHarmonyEventListener<T> =
			this.of(T::class.java, plugin, listener, lookup)
	}

	private class Impl<in T : Any>(
		override val scopeClass: Class<in T>,
		override val plugin: Any,
		override val listener: (T) -> Any,
		override val lookup: MethodHandles.Lookup?) : IHarmonyEventListener<T>
}
