package io.quut.harmony.api

/**
 * Additional options for scope registration.
 *
 * @param T The scope type.
 */
interface IHarmonyScopeOptions<in T : Any>
{
	/**
	 * The child [event manager][IHarmonyEventManager] which
	 * is processed after the parent has called its own listeners.
	 *
	 * The child is able to do further processing based on the
	 * context the parent chose using the [IHarmonyEventManager.IBuilder.parentMapping].
	 */
	val child: IHarmonyEventManager<*>?

	/**
	 * **Additional** listeners to register for this scope.
	 */
	val listeners: Collection<IHarmonyEventListener<T>>

	/**
	 * If `true` validates that all the event
	 * registrations have their mapping.
	 */
	val validate: Boolean

	companion object
	{
		private val validate: IHarmonyScopeOptions<*> = of<Any>(validate = true)
		private val skipValidation: IHarmonyScopeOptions<*> = of<Any>(validate = false)

		/**
		 * Gets a [IHarmonyScopeOptions] which has its [IHarmonyScopeOptions.validate]
		 * set to `true`.
		 *
		 * @return The [IHarmonyScopeOptions].
		 */
		@Suppress("UNCHECKED_CAST")
		@JvmStatic
		fun <T : Any> validate(): IHarmonyScopeOptions<T> = this.validate as IHarmonyScopeOptions<T>

		/**
		 * Gets a [IHarmonyScopeOptions] which has its [IHarmonyScopeOptions.validate]
		 * set to `false`.
		 *
		 * @return The [IHarmonyScopeOptions].
		 */
		@Suppress("UNCHECKED_CAST")
		@JvmStatic
		fun <T : Any> skipValidation(): IHarmonyScopeOptions<T> = this.skipValidation as IHarmonyScopeOptions<T>

		/**
		 * Creates a [IHarmonyScopeOptions].
		 *
		 * @param T The scope type.
		 * @param listeners The additional listeners to register as part of the scope registration.
		 * @param child The child event manager.
		 * @param validate If `true` runs validation as part of the scope registration.
		 */
		@JvmStatic
		@JvmOverloads
		fun <T : Any> of(vararg listeners: IHarmonyEventListener<T>, child: IHarmonyEventManager<*>? = null, validate: Boolean = true): IHarmonyScopeOptions<T> =
			Impl(child, listeners.toList(), validate)
	}

	private class Impl<in T : Any>(
		override val child: IHarmonyEventManager<*>?,
		override val listeners: Collection<IHarmonyEventListener<T>>,
		override val validate: Boolean
	) : IHarmonyScopeOptions<T>
}
