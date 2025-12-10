package fr.utln.spelerin.fatigoonbot.dto.createdto;


public record ChannelCreateDTO(
	Long id,
	String name,
	Integer type,
	Long guildId
){}