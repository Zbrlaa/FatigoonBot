package fr.utln.spelerin.fatigoonbot.dto;

import java.util.Set;


public record UserDTO(
	long id,
	String username,
	String displayName,
	Set<Long> guildIds,
	Set<Long> roleIds
){}