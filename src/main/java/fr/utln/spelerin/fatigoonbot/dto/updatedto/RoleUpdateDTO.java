package fr.utln.spelerin.fatigoonbot.dto.updatedto;


public record RoleUpdateDTO(
	String name,
	Long permissions,
	Long guildId
){}