package fr.utln.spelerin.fatigoonbot;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.RestClient;
import fr.utln.spelerin.fatigoonbot.listeners.GuildCreateListener;
import fr.utln.spelerin.fatigoonbot.listeners.MemberJoinListener;
import fr.utln.spelerin.fatigoonbot.listeners.SlashCommandListener;
import fr.utln.spelerin.fatigoonbot.services.InviteTrackingService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BotService{
	private static final Logger log = LoggerFactory.getLogger(BotService.class);
	private static final String MESSAGE_OPTION = "message";

	private final String token;
	private final GuildCreateListener guildCreateListener;
	private final SlashCommandListener slashCommandListener;
	private final MemberJoinListener memberJoinListener;
	private final InviteTrackingService inviteTrackingService;

	private GatewayDiscordClient client;
	private final java.util.Set<Long> registeredGuilds = new java.util.concurrent.ConcurrentHashMap<Long, Boolean>().keySet(true);

	public BotService(
			@ConfigProperty(name = "DISCORD_TOKEN") String token,
			GuildCreateListener guildCreateListener,
			SlashCommandListener slashCommandListener,
			MemberJoinListener memberJoinListener,
			InviteTrackingService inviteTrackingService) {
		this.token = token;
		this.guildCreateListener = guildCreateListener;
		this.slashCommandListener = slashCommandListener;
		this.memberJoinListener = memberJoinListener;
		this.inviteTrackingService = inviteTrackingService;
	}

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
				.flatMap(event -> {
					long guildId = event.getGuild().getId().asLong();
					// Enregistrer les commandes pour ce serveur uniquement si pas déjà fait
					if (registeredGuilds.add(guildId)) {
						registerSlashCommandsForGuild(client.getRestClient(), guildId);
					} else {
						log.debug("Commandes déjà enregistrées pour le serveur {}", guildId);
					}
					// Initialiser le cache des invitations pour cette guild
					return inviteTrackingService.refreshInviteCache(guildId)
							.then(guildCreateListener.handle(event));
				})
				.onErrorContinue((error, obj) -> log.error("Erreur lors du traitement de l'événement", error))
				.subscribe();

		client.getEventDispatcher().on(MemberJoinEvent.class)
				.flatMap(memberJoinListener::handle)
				.onErrorContinue((error, obj) -> log.error("Erreur lors du traitement de MemberJoin", error))
				.subscribe();

		client.getEventDispatcher().on(ChatInputInteractionEvent.class)
				.flatMap(slashCommandListener::handle)
				.onErrorContinue((error, obj) -> log.error("Erreur lors du traitement de la commande", error))
				.subscribe();

		log.info("Bot connecté et listeners enregistrés.");
	}

	/**
	 * Enregistre les commandes slash pour un serveur spécifique (apparaissent instantanément)
	 */
	private void registerSlashCommandsForGuild(RestClient restClient, long guildId) {
		Long applicationId = restClient.getApplicationId().block();
		if (applicationId == null) {
			log.error("Impossible de récupérer l'ID de l'application");
			return;
		}

		log.info("Enregistrement des commandes slash pour le serveur {}", guildId);

		// Commande /summarize
		ApplicationCommandRequest summarizeCommand = ApplicationCommandRequest.builder()
				.name("summarize")
				.description("Résume un texte")
				.addOption(ApplicationCommandOptionData.builder()
						.name(MESSAGE_OPTION)
						.description("Le texte à résumer")
						.type(ApplicationCommandOption.Type.STRING.getValue())
						.required(true)
						.build())
				.build();

		// Commande /teach
		ApplicationCommandRequest teachCommand = ApplicationCommandRequest.builder()
				.name("teach")
				.description("Explique un concept")
				.addOption(ApplicationCommandOptionData.builder()
						.name(MESSAGE_OPTION)
						.description("Le concept à expliquer")
						.type(ApplicationCommandOption.Type.STRING.getValue())
						.required(true)
						.build())
				.build();

		// Commande /translate
		ApplicationCommandRequest translateCommand = ApplicationCommandRequest.builder()
				.name("translate")
				.description("Traduit un texte")
				.addOption(ApplicationCommandOptionData.builder()
						.name(MESSAGE_OPTION)
						.description("Le texte à traduire")
						.type(ApplicationCommandOption.Type.STRING.getValue())
						.required(true)
						.build())
				.build();

		// Enregistrement par serveur (instantané)
		restClient.getApplicationService()
				.bulkOverwriteGuildApplicationCommand(applicationId, guildId,
					java.util.List.of(summarizeCommand, teachCommand, translateCommand))
				.doOnNext(cmd -> log.info("✓ Commande '{}' enregistrée pour le serveur {}", cmd.name(), guildId))
				.doOnError(error -> log.error("Erreur lors de l'enregistrement des commandes pour le serveur {}", guildId, error))
				.subscribe();
	}

	/**
	 * Proprement déconnecter le bot quand l'application s'arrête.
	 */
	void onStop(@Observes ShutdownEvent ev) {
		if (client != null) {
			log.info("Déconnexion du bot...");
			client.logout().block();
		}
		registeredGuilds.clear();
	}

	@jakarta.enterprise.inject.Produces
	@ApplicationScoped
	public GatewayDiscordClient getDiscordClient() {
		return client;
	}
}