package fr.utln.spelerin.fatigoonbot.dto;

import java.util.Set;


public record ChannelDTO(
	long id,
	String name,
	int type,
	long guildId,
	Set<Long> rolesWithAccessIds
){}