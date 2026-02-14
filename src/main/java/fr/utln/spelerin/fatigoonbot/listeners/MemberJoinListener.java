package fr.utln.spelerin.fatigoonbot.listeners;

import discord4j.core.event.domain.guild.MemberJoinEvent;
import fr.utln.spelerin.fatigoonbot.services.BotSyncService;
import fr.utln.spelerin.fatigoonbot.services.InviteTrackingService;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Listener pour détecter l'arrivée de nouveaux membres et leur assigner
 * les rôles correspondant à l'invitation utilisée
 */
@ApplicationScoped
public class MemberJoinListener {
	private static final Logger log = LoggerFactory.getLogger(MemberJoinListener.class);

	private final BotSyncService botSyncService;
	private final InviteTrackingService inviteTrackingService;

	public MemberJoinListener(BotSyncService botSyncService, InviteTrackingService inviteTrackingService) {
		this.botSyncService = botSyncService;
		this.inviteTrackingService = inviteTrackingService;
	}

	public Mono<Void> handle(MemberJoinEvent event) {
		long guildId = event.getGuildId().asLong();
		long userId = event.getMember().getId().asLong();
		String username = event.getMember().getUsername();

		log.info("Nouveau membre : {} ({}) a rejoint la guild {}", username, userId, guildId);

		// 1. Synchroniser le membre dans l'API (sans les rôles de l'invitation pour l'instant)
		return botSyncService.syncMember(event.getMember())
				// 2. Détecter l'invitation utilisée et assigner les rôles automatiquement
				.then(inviteTrackingService.detectAndAssignRoles(guildId, userId))
				.doOnSuccess(v -> log.info("Traitement terminé pour le nouveau membre {} dans la guild {}", username, guildId))
				.doOnError(e -> log.error("Erreur lors du traitement du nouveau membre {} dans la guild {}", username, guildId, e))
				.onErrorResume(e -> Mono.empty()); // Continue même en cas d'erreur
	}
}
