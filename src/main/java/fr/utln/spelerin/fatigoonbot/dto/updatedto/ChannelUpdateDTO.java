package fr.utln.spelerin.fatigoonbot.dto.updatedto;


public record ChannelUpdateDTO(
	String name,
	Integer type,
	Long guildId
){ }