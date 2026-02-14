package fr.utln.spelerin.fatigoonbot.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.utln.spelerin.fatigoonbot.dto.GuildDTO;
import fr.utln.spelerin.fatigoonbot.dto.InvitationDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.ChannelCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.GuildCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.RoleCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.UserCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.llm.LLMRequest;
import fr.utln.spelerin.fatigoonbot.dto.llm.LLMResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ApplicationScoped
public class FatigoonApiClient {
	private static final Logger log = LoggerFactory.getLogger(FatigoonApiClient.class);
	
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String baseUrl;

	public FatigoonApiClient(@ConfigProperty(name = "api.url", defaultValue = "http://localhost:8080/v1") String baseUrl) {
		this.baseUrl = baseUrl;
		this.objectMapper = new ObjectMapper();
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.build();
	}


	// Méthode générique pour POST
	private CompletableFuture<Void> post(String endpoint, Object dto) {
		try {
			String jsonBody = objectMapper.writeValueAsString(dto);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + endpoint))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
					.timeout(Duration.ofSeconds(10))
					.build();

			return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
					.thenAccept(response -> {
						if (response.statusCode() >= 400) {
							log.error("[API ERROR] {} : {} - {}", endpoint, response.statusCode(), response.body());
						}
					});
		} catch (JsonProcessingException e) {
			return CompletableFuture.failedFuture(e);
		}
	}


	// Méthode générique pour PUT
	private CompletableFuture<Void> put(String endpoint) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + endpoint))
				.PUT(HttpRequest.BodyPublishers.noBody())
				.timeout(Duration.ofSeconds(10))
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> {
					if (response.statusCode() >= 400) {
						log.error("API Error [{}]: {} - {}", endpoint, response.statusCode(), response.body());
					}
				});
	}


	// Méthode générique pour GET obtenant un objet
	private <T> CompletableFuture<T> get(String endpoint, Class<T> responseType) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + endpoint))
				.GET()
				.timeout(Duration.ofSeconds(10))
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() >= 400) {
						log.error("[API ERROR] GET {} : {} - {}", endpoint, response.statusCode(), response.body());
						throw new RuntimeException("API Error: " + response.statusCode());
					}
					try {
						return objectMapper.readValue(response.body(), responseType);
					} catch (JsonProcessingException e) {
						log.error("Erreur de parsing JSON pour {}", endpoint, e);
						throw new RuntimeException(e);
					}
				});
	}


	// --- Guilds ---
	public CompletableFuture<Void> createGuild(GuildCreateDTO dto) {
		return post("/guilds", dto);
	}

	public CompletableFuture<GuildDTO> getGuild(long guildId) {
		return get("/guilds/" + guildId, GuildDTO.class);
	}

	public CompletableFuture<Void> addUserToGuild(long guildId, long userId) {
		return put("/guilds/" + guildId + "/users/" + userId);
	}


	// --- Invitations ---
	public CompletableFuture<InvitationDTO> getInvitation(long invitationId) {
		return get("/invitations/" + invitationId, InvitationDTO.class);
	}


	// --- Roles ---
	public CompletableFuture<Void> createRole(RoleCreateDTO dto) {
		return post("/roles", dto);
	}


	// --- Channels ---
	public CompletableFuture<Void> createChannel(ChannelCreateDTO dto) {
		return post("/channels", dto);
	}

	public CompletableFuture<Void> addRoleToChannel(long channelId, long roleId) {
		return put("/channels/" + channelId + "/roles/" + roleId); //
	}


	// --- Users ---
	public CompletableFuture<Void> createUser(UserCreateDTO dto) {
		return post("/users", dto);
	}

	public CompletableFuture<Void> addRoleToUser(long userId, long roleId) {
		return put("/users/" + userId + "/roles/" + roleId); //
	}


	// --- LLM ---
	private CompletableFuture<LLMResponse> postLLM(String endpoint, LLMRequest request) {
		try {
			String jsonBody = objectMapper.writeValueAsString(request);
			HttpRequest httpRequest = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + endpoint))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
					.timeout(Duration.ofSeconds(30))
					.build();

			return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
					.thenApply(response -> {
						if (response.statusCode() >= 400) {
							log.error("[LLM API ERROR] {} : {} - {}", endpoint, response.statusCode(), response.body());
							return new LLMResponse("Erreur lors de l'appel à l'API.");
						}
						try {
							return objectMapper.readValue(response.body(), LLMResponse.class);
						} catch (JsonProcessingException e) {
							log.error("Erreur de parsing de la réponse LLM", e);
							return new LLMResponse("Erreur lors du traitement de la réponse.");
						}
					});
		} catch (JsonProcessingException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	public CompletableFuture<LLMResponse> summarize(String message) {
		return postLLM("/llm/summarize", new LLMRequest(message));
	}

	public CompletableFuture<LLMResponse> teach(String message) {
		return postLLM("/llm/teach", new LLMRequest(message));
	}

	public CompletableFuture<LLMResponse> translate(String message) {
		return postLLM("/llm/translate", new LLMRequest(message));
	}
}