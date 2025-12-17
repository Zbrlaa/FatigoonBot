package fr.utln.spelerin.fatigoonbot;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import fr.utln.spelerin.fatigoonbot.client.FatigoonApiClient;
import fr.utln.spelerin.fatigoonbot.listeners.GuildCreateListener;
import fr.utln.spelerin.fatigoonbot.service.BotSyncService;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
	private static final Logger log = LoggerFactory.getLogger(App.class);
	public static void main(String[] args) {
		log.info("Lancement du bot");

		// 1. Chargement de la config
		Dotenv dotenv = Dotenv.load();
		String token = dotenv.get("DISCORD_TOKEN");
		String apiUrl = dotenv.get("API_URL", "http://localhost:8080/v1");

		// 2. Initialisation des services (Dependency Injection manuelle)
		FatigoonApiClient apiClient = new FatigoonApiClient(apiUrl);
		BotSyncService botSyncService = new BotSyncService(apiClient);
		GuildCreateListener guildCreateListener = new GuildCreateListener(botSyncService);

		GatewayDiscordClient client = DiscordClientBuilder.create(token)
				.build()
				.gateway() // On passe en configuration Gateway
				.setEnabledIntents(IntentSet.nonPrivileged().or(IntentSet.of(Intent.GUILD_MEMBERS))) // <--- L'ajout CRITIQUE
				.login()
				.block();

		if (client == null) {
			log.error("Erreur critique: Impossible de se connecter à Discord.");
			return;
		}

		// 4. Enregistrement des Listeners
		client.getEventDispatcher().on(GuildCreateEvent.class)
				.flatMap(guildCreateListener::handle) // Délégation propre à la classe Listener
				.onErrorContinue((error, obj) -> log.error("Erreur lors du traitement de l'événement", error))
				.subscribe();

		// 5. Maintien en vie
		client.onDisconnect().block();
	}
}