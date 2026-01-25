package fr.utln.spelerin.fatigoonbot.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.utln.spelerin.fatigoonbot.dto.createdto.ChannelCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.GuildCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.RoleCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.UserCreateDTO;
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


	// --- Guilds ---
	public CompletableFuture<Void> createGuild(GuildCreateDTO dto) {
		return post("/guilds", dto);
	}

	public CompletableFuture<Void> addUserToGuild(long guildId, long userId) {
		return put("/guilds/" + guildId + "/users/" + userId);
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
}