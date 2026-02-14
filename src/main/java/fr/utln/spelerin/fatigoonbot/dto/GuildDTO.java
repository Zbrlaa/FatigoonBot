package fr.utln.spelerin.fatigoonbot.dto;

import java.util.Set;


public record GuildDTO(
	long id,
	String name,
	Long ownerId,
	Set<Long> userIds,
	Set<Long> roleIds,
	Set<Long> channelIds,
	Set<Long> invitationIds
){}