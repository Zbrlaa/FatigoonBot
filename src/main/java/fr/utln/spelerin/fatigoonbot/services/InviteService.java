package fr.utln.spelerin.fatigoonbot.services;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.Invite;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.InviteCreateSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@ApplicationScoped
public class InviteService {

	GatewayDiscordClient client;

	@Inject
	public InviteService(GatewayDiscordClient client){
		this.client = client;
	}
	

	/**
	 * Génère un lien d'invitation permanent (infini en temps et en utilisateurs).
	 */
	public Mono<String> createInviteCode(long guildId) {
		return client.getGuildById(Snowflake.of(guildId))
			.flatMap(guild -> guild.getChannels()
				.filter(TextChannel.class::isInstance)
				.next()
				.cast(TextChannel.class)
				.flatMap(channel -> channel.createInvite(InviteCreateSpec.builder()
					.maxAge(0)    // 0 = N'expire JAMAIS
					.maxUses(0)   // 0 = Utilisations ILLIMITÉES
					.temporary(false) // L'utilisateur ne sera pas expulsé s'il ne prend pas de rôle
					.unique(true)        // <--- FORCE la génération d'un nouveau code unique
					.reason("Invitation unique générée via le dashboard.")
					.build()))
			)
			.map(Invite::getCode);
	}
}