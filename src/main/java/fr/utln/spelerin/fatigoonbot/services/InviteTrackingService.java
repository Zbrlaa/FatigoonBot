package fr.utln.spelerin.fatigoonbot.services;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.Invite;
import discord4j.core.object.entity.Guild;
import fr.utln.spelerin.fatigoonbot.client.FatigoonApiClient;
import fr.utln.spelerin.fatigoonbot.dto.InvitationDTO;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service pour tracker les invitations et assigner les rôles automatiquement
 * lorsqu'un membre rejoint le serveur
 */
@ApplicationScoped
public class InviteTrackingService {
	private static final Logger log = LoggerFactory.getLogger(InviteTrackingService.class);

	private final GatewayDiscordClient client;
	private final FatigoonApiClient apiClient;

	// Cache des uses d'invitations par guild : guildId -> (inviteCode -> uses)
	private final Map<Long, Map<String, Integer>> inviteCache = new ConcurrentHashMap<>();

	public InviteTrackingService(GatewayDiscordClient client, FatigoonApiClient apiClient) {
		this.client = client;
		this.apiClient = apiClient;
	}

	/**
	 * Met à jour le cache des invitations pour une guild donnée
	 */
	public Mono<Void> refreshInviteCache(long guildId) {
		return client.getGuildById(Snowflake.of(guildId))
				.flatMapMany(Guild::getInvites)
				.collectMap(
						Invite::getCode,
						discord4j.core.object.ExtendedInvite::getUses
				)
				.doOnNext(inviteMap -> {
					inviteCache.put(guildId, new HashMap<>(inviteMap));
					log.debug("Cache des invitations mis à jour pour la guild {} : {} invitations", guildId, inviteMap.size());
				})
				.then();
	}

	/**
	 * Détecte quelle invitation a été utilisée et assigne les rôles correspondants au nouveau membre.
	 * 
	 * @param guildId ID de la guild
	 * @param userId ID du nouveau membre
	 * @return Mono<Void> qui complète une fois les rôles assignés
	 */
	public Mono<Void> detectAndAssignRoles(long guildId, long userId) {
		Map<String, Integer> oldInvites = inviteCache.get(guildId);
		if (oldInvites == null) {
			log.warn("Aucun cache d'invitations trouvé pour la guild {}, initialisation...", guildId);
			return refreshInviteCache(guildId);
		}

		// Récupère les nouvelles invitations et compare
		return client.getGuildById(Snowflake.of(guildId))
				.flatMapMany(Guild::getInvites)
				.collectList()
				.flatMap(newInvites -> {
					// Cherche quelle invitation a augmenté en uses
					String usedInviteCode = null;
					for (var invite : newInvites) {
						String code = invite.getCode();
						int newUses = invite.getUses();
						int oldUses = oldInvites.getOrDefault(code, 0);

						if (newUses > oldUses) {
							usedInviteCode = code;
							log.info("Invitation détectée : {} (uses: {} -> {})", code, oldUses, newUses);
							break;
						}
					}

					if (usedInviteCode == null) {
						log.warn("Impossible de détecter l'invitation utilisée pour le membre {} dans la guild {}", userId, guildId);
						// On met à jour le cache quand même
						return refreshInviteCache(guildId);
					}

					final String detectedInviteCode = usedInviteCode;

					// Met à jour le cache
					return refreshInviteCache(guildId)
							.then(Mono.fromFuture(() -> apiClient.getGuild(guildId)))
							.flatMap(guild -> 
								// Trouve l'ID de l'invitation dans l'API en comparant le discordCode
								Flux.fromIterable(guild.invitationIds())
										.flatMap(invitationId -> 
											Mono.fromFuture(() -> apiClient.getInvitation(invitationId))
													.filter(invitation -> detectedInviteCode.equals(invitation.discordCode()))
										)
										.next()
										.flatMap(invitation -> {
											log.info("Invitation {} correspond à l'ID API {} avec {} rôles", 
													detectedInviteCode, invitation.id(), invitation.roleIds().size());
											
											// Assigne tous les rôles de l'invitation au nouveau membre
											return assignRolesToMember(guildId, userId, invitation);
										})
										.switchIfEmpty(Mono.fromRunnable(() -> 
											log.warn("Invitation {} non trouvée dans l'API pour la guild {}", detectedInviteCode, guildId)
										))
							);
				})
				.onErrorResume(e -> {
					log.error("Erreur lors de la détection d'invitation pour le membre {} dans la guild {}", userId, guildId, e);
					return Mono.empty();
				});
	}

	/**
	 * Assigne les rôles d'une invitation à un membre Discord
	 */
	private Mono<Void> assignRolesToMember(long guildId, long userId, InvitationDTO invitation) {
		if (invitation.roleIds() == null || invitation.roleIds().isEmpty()) {
			log.info("Aucun rôle à assigner pour l'invitation {}", invitation.id());
			return Mono.empty();
		}

		return client.getGuildById(Snowflake.of(guildId))
				.flatMap(guild -> guild.getMemberById(Snowflake.of(userId)))
				.flatMapMany(member -> 
					Flux.fromIterable(invitation.roleIds())
						.flatMap(roleId -> {
							log.info("Assignation du rôle {} au membre {} via l'invitation {}", 
									roleId, userId, invitation.discordCode());
							
							// Assigne le rôle sur Discord
							return member.addRole(Snowflake.of(roleId))
									.doOnSuccess(v -> log.info("Rôle {} assigné avec succès", roleId))
									.doOnError(e -> log.error("Erreur lors de l'assignation du rôle {}", roleId, e))
									// Puis met à jour l'API
									.then(Mono.fromFuture(() -> apiClient.addRoleToUser(userId, roleId)));
						})
				)
				.then()
				.doOnSuccess(v -> log.info("{} rôles assignés au membre {} via l'invitation {}", 
						invitation.roleIds().size(), userId, invitation.discordCode()));
	}
}
