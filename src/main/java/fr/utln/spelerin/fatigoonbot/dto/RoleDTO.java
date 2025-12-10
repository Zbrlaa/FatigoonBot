package fr.utln.spelerin.fatigoonbot.dto;

import java.util.Set;


public record RoleDTO(
	long id,
	String name,
	long permissions,
	long guildId,
	Set<Long> userIds,
	Set<Long> accessibleChannelIds
){}