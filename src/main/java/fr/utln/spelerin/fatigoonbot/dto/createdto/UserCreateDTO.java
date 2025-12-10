package fr.utln.spelerin.fatigoonbot.dto.createdto;


public record UserCreateDTO(
	Long id,
	String username,
	String displayName
){}