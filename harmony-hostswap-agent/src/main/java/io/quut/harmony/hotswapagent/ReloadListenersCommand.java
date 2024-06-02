package io.quut.harmony.hotswapagent;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

final class ReloadListenersCommand implements Command
{
	private static final AgentLogger LOGGER = AgentLogger.getLogger(ReloadListenersCommand.class);

	private final ClassLoader appClassLoader;
	private final Set<Object> eventManagers;
	private final Class<?> listenerClass;

	ReloadListenersCommand(final ClassLoader appClassLoader, final Set<Object> eventManagers, final Class<?> listenerClass)
	{
		this.appClassLoader = appClassLoader;
		this.eventManagers = eventManagers;
		this.listenerClass = listenerClass;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void executeCommand()
	{
		try
		{
			final Class<?> harmonyEventManagerClass = Class.forName("io.quut.harmony.sponge.SpongeHarmonyEventManager", true, this.appClassLoader);
			final Class<?> registeredListenerClass = Class.forName("org.spongepowered.common.event.manager.RegisteredListener", true, this.appClassLoader);
			final Class<?> scopedEventManagerClass = Class.forName("io.quut.harmony.sponge.ScopedEventManager", true, this.appClassLoader);

			final Field scopesField = harmonyEventManagerClass.getDeclaredField("scopes");
			scopesField.setAccessible(true);

			final Method registerMethod = harmonyEventManagerClass.getDeclaredMethod("register", registeredListenerClass);
			registerMethod.setAccessible(true);

			final Method getHandleMethod = registeredListenerClass.getMethod("getHandle");

			final Field lockField = scopedEventManagerClass.getDeclaredField("lock");
			lockField.setAccessible(true);

			final Field listenersField = scopedEventManagerClass.getDeclaredField("listeners");
			listenersField.setAccessible(true);

			boolean found = false;
			synchronized (this.eventManagers)
			{
				for (final Object eventManager : this.eventManagers)
				{
					final Map<?, ?> scopes = (Map<?, ?>) scopesField.get(eventManager);
					for (final Object scopedEventManager : scopes.values())
					{
						synchronized (lockField.get(scopedEventManager))
						{
							final Collection<Object> listeners = (Collection<Object>) listenersField.get(scopedEventManager);
							for (final Object listener : listeners)
							{
								final Object handle = getHandleMethod.invoke(listener);
								if (!handle.getClass().equals(this.listenerClass))
								{
									continue;
								}

								registerMethod.invoke(eventManager, listener);
								found = true;
							}
						}
					}
				}
			}

			if (found)
			{
				ReloadListenersCommand.LOGGER.info("Successfully refreshed listeners for {}", this.listenerClass);
			}
		}
		catch (final Throwable e)
		{
			ReloadListenersCommand.LOGGER.error("Error refreshing listeners for {}", e, this.listenerClass);
		}
	}
}
