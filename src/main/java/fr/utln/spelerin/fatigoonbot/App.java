package fr.utln.spelerin.fatigoonbot;

import io.github.cdimascio.dotenv.Dotenv;
import reactor.core.publisher.Mono;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;

public class App {
	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.load();
		String token = dotenv.get("DISCORD_TOKEN");

		GatewayDiscordClient client = DiscordClientBuilder.create(token)
				.build()
				.login()
				.block();

		client.getEventDispatcher().on(MessageCreateEvent.class)
				.flatMap(event -> {
					Message message = event.getMessage();
					User author = message.getAuthor().orElse(null);

					if (author != null && !author.isBot()) {
						String content = message.getContent();
						if (content != null && !content.isBlank()) {
							return message.getChannel()
									.flatMap(channel -> channel.createMessage(content));
						}
					}

					return Mono.empty();
				})
				.onErrorContinue((e, obj) -> System.err.println("Erreur : " + e.getMessage()))
				.subscribe();

		client.onDisconnect().block();
	}
}