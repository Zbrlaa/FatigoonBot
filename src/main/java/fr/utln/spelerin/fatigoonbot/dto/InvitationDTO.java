package fr.utln.spelerin.fatigoonbot.dto;

import java.util.List;

public record InvitationDTO(
	long id,
	String discordCode,
	long guildId,
	List<Long> roleIds
) {}
