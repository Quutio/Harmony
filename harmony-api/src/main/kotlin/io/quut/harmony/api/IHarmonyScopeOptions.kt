package io.quut.harmony.api

/**
 * Additional options for scope registration.
 *
 * @param T The scope type.
 */
interface IHarmonyScopeOptions<in T>
{
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
		fun <T> validate(): IHarmonyScopeOptions<T> = this.validate as IHarmonyScopeOptions<T>

		/**
		 * Gets a [IHarmonyScopeOptions] which has its [IHarmonyScopeOptions.validate]
		 * set to `false`.
		 *
		 * @return The [IHarmonyScopeOptions].
		 */
		@Suppress("UNCHECKED_CAST")
		@JvmStatic
		fun <T> skipValidation(): IHarmonyScopeOptions<T> = this.skipValidation as IHarmonyScopeOptions<T>

		/**
		 * Creates a [IHarmonyScopeOptions].
		 *
		 * @param T The scope type.
		 * @param listeners The additional listeners to register as part of the scope registration.
		 * @param validate If `true` runs validation as part of the scope registration.
		 */
		@JvmStatic
		@JvmOverloads
		fun <T> of(vararg listeners: IHarmonyEventListener<T>, validate: Boolean = true): IHarmonyScopeOptions<T> =
			Impl(listeners.toList(), validate)
	}

	private class Impl<in T>(
		override val listeners: Collection<IHarmonyEventListener<T>>,
		override val validate: Boolean
	) : IHarmonyScopeOptions<T>
}
