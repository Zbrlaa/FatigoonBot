package fr.utln.spelerin.fatigoonbot.listeners;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import fr.utln.spelerin.fatigoonbot.client.FatigoonApiClient;
import fr.utln.spelerin.fatigoonbot.dto.llm.LLMResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class SlashCommandListener {
	private static final Logger log = LoggerFactory.getLogger(SlashCommandListener.class);
	private static final int DISCORD_MAX_MESSAGE_LENGTH = 2000;

	private final FatigoonApiClient apiClient;

	public SlashCommandListener(FatigoonApiClient apiClient) {
		this.apiClient = apiClient;
	}

	public Mono<Void> handle(ChatInputInteractionEvent event) {
		String commandName = event.getCommandName();
		
		log.info("Commande /{} reçue de {}", commandName, event.getInteraction().getUser().getTag());
		
		// Récupération du message depuis l'option de la commande
		String message = event.getOption("message")
				.flatMap(ApplicationCommandInteractionOption::getValue)
				.map(ApplicationCommandInteractionOptionValue::asString)
				.orElse("");

		if (message.isEmpty()) {
			log.warn("Aucun message fourni pour la commande /{}", commandName);
			return event.reply("Erreur : aucun message fourni.")
					.withEphemeral(true)
					.then();
		}

		log.info("Traitement de la commande /{} ({} caractères)", commandName, message.length());

		// Defer la réponse pour avoir plus de temps
		return event.deferReply()
				.then(Mono.fromFuture(processCommand(commandName, message)))
				.flatMap(response -> {
					String answer = response.answer();
					
					// Tronquer si la réponse dépasse la limite Discord
					if (answer.length() > DISCORD_MAX_MESSAGE_LENGTH) {
						log.warn("Réponse de l'API trop longue ({} caractères), troncature à {}", 
							answer.length(), DISCORD_MAX_MESSAGE_LENGTH);
						answer = answer.substring(0, DISCORD_MAX_MESSAGE_LENGTH - 4) + "...";
					}
					
					log.info("Réponse de l'API pour /{}: {} caractères", commandName, answer.length());
					return event.editReply(answer);
				})
				.onErrorResume(error -> {
					log.error("Erreur lors du traitement de la commande {}", commandName, error);
					return event.editReply("Une erreur est survenue lors du traitement de votre demande.");
				})
				.then();
	}

	private CompletableFuture<LLMResponse> processCommand(String commandName, String message) {
		return switch (commandName) {
			case "summarize" -> apiClient.summarize(message);
			case "teach" -> apiClient.teach(message);
			case "translate" -> apiClient.translate(message);
			default -> {
				log.warn("Commande inconnue : {}", commandName);
				yield CompletableFuture.completedFuture(new LLMResponse("Commande inconnue."));
			}
		};
	}
}
