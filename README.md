# Harmony
An event manager for scoped environments. Maps global events to one particular instance
from multiple options.

## Samples
### Sponge
For example one might want to map each world related event to their own scope.

```kotlin
@Plugin("harmonytestplugin")
class HarmonyTestPlugin @Inject constructor(private val pluginContainer: PluginContainer)
{
	private val eventManager: IHarmonyEventManager<ServerWorld> =
		IHarmonyEventManager.builder<ServerWorld>(this.pluginContainer)
			.mapping { e: ChangeBlockEvent.All -> e.world() }
			.listener(this.pluginContainer, ::Listeners) //The listener is instantiated on scope registration.
			.build()

	@Listener
	private fun onLoadWorld(event: LoadWorldEvent)
	{
		this.eventManager.registerScope(event.world())
	}

	@Listener
	private fun onUnloadWorld(event: UnloadWorldEvent)
	{
		this.eventManager.unregisterScope(event.world())
	}

	private class Listeners(private val world: ServerWorld)
	{
		//Add scope related variables

		@Listener
		private fun onBlockChange(event: ChangeBlockEvent.All, @First player: ServerPlayer)
		{
			player.sendMessage(Component.text("You made a block change!"))
		}
	}
}
```
