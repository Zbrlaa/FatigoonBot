package fr.utln.spelerin.fatigoonbot.listeners;

import discord4j.core.event.domain.guild.GuildCreateEvent;
import fr.utln.spelerin.fatigoonbot.services.BotSyncService;
import jakarta.enterprise.context.ApplicationScoped;
import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ApplicationScoped
public class GuildCreateListener {
	private static final Logger log = LoggerFactory.getLogger(GuildCreateListener.class);

	private final BotSyncService botSyncService;

	public GuildCreateListener(BotSyncService botSyncService) {
		this.botSyncService = botSyncService;
	}

	public Mono<Void> handle(GuildCreateEvent event) {
		log.info("Event reçu : GuildCreate pour {}", event.getGuild().getName());
		return botSyncService.processFullGuildSync(event.getGuild());
	}
}