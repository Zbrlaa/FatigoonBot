package fr.utln.spelerin.fatigoonbot.dto.createdto;


public record RoleCreateDTO(
	Long id,
	String name,
	Long permissions,
	Long guildId
){}