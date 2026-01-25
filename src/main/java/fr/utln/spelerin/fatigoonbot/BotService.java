package fr.utln.spelerin.fatigoonbot;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import fr.utln.spelerin.fatigoonbot.listeners.GuildCreateListener;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BotService{
	private static final Logger log = LoggerFactory.getLogger(BotService.class);

	@ConfigProperty(name = "DISCORD_TOKEN")
	String token;

	@Inject
	GuildCreateListener guildCreateListener;

	private GatewayDiscordClient client;

	void onStart(@Observes StartupEvent ev) {
		log.info("Lancement du bot avec Quarkus...");

		client = DiscordClientBuilder.create(token)
				.build()
				.gateway()
				.setEnabledIntents(IntentSet.nonPrivileged().or(IntentSet.of(Intent.GUILD_MEMBERS)))
				.login()
				.block();

		if (client == null) {
			log.error("Erreur critique: Impossible de se connecter à Discord.");
			return;
		}

		// Enregistrement des Listeners
		client.getEventDispatcher().on(GuildCreateEvent.class)
				.flatMap(guildCreateListener::handle)
				.onErrorContinue((error, obj) -> log.error("Erreur lors du traitement de l'événement", error))
				.subscribe();

		log.info("Bot connecté et listeners enregistrés.");
	}

	/**
	 * Proprement déconnecter le bot quand l'application s'arrête.
	 */
	void onStop(@Observes ShutdownEvent ev) {
		if (client != null) {
			log.info("Déconnexion du bot...");
			client.logout().block();
		}
	}

	@jakarta.enterprise.inject.Produces
	@ApplicationScoped
	public GatewayDiscordClient getDiscordClient() {
		return client;
	}
}