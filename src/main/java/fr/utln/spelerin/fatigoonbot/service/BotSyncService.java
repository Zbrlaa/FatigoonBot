package fr.utln.spelerin.fatigoonbot.service;

import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.GuildChannel;

import fr.utln.spelerin.fatigoonbot.client.FatigoonApiClient;
import fr.utln.spelerin.fatigoonbot.dto.createdto.ChannelCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.GuildCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.RoleCreateDTO;
import fr.utln.spelerin.fatigoonbot.dto.createdto.UserCreateDTO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BotSyncService {
    private static final Logger log = LoggerFactory.getLogger(BotSyncService.class);

    private final FatigoonApiClient apiClient;

    public BotSyncService(FatigoonApiClient apiClient) {
        this.apiClient = apiClient;
    }


    /**
     * Synchronise TOUTE une guilde (Guild + Roles + Channels + Members)
     * Parfait pour le GuildCreateEvent.
     */
    public Mono<Void> processFullGuildSync(Guild guild) {
        return syncGuild(guild)
                .then(Mono.when(
                        guild.getRoles().flatMap(this::syncRole).then(),
                        guild.getChannels().flatMap(this::syncChannel).then()
                ))
                .then(
                    guild.getMembers().flatMap(this::syncMember).then()
                )
                .doOnSuccess(v -> log.info("Full Sync terminée pour {} ", guild.getName()));
    }


    public Mono<Void> syncGuild(Guild guild) {
        GuildCreateDTO dto = new GuildCreateDTO(guild.getId().asLong(), guild.getName());
        return Mono.fromFuture(() -> apiClient.createGuild(dto));
    }


    public Mono<Void> syncRole(Role role) {
        RoleCreateDTO dto = new RoleCreateDTO(
                role.getId().asLong(),
                role.getName(),
                role.getPermissions().getRawValue(),
                role.getGuildId().asLong()
        );
        return Mono.fromFuture(() -> apiClient.createRole(dto));
    }


    public Mono<Void> syncChannel(GuildChannel channel) {
        // 1. On crée d'abord le channel
        int type = channel.getType().getValue();
        ChannelCreateDTO dto = new ChannelCreateDTO(
                channel.getId().asLong(),
                channel.getName(),
                type,
                channel.getGuildId().asLong()
        );

        return Mono.fromFuture(() -> apiClient.createChannel(dto))
                // 2. Ensuite, on synchronise les relations (Permission Overwrites) pour les rôles
                .then(
                    Flux.fromIterable(channel.getPermissionOverwrites()) // On récupère les surcharges Discord
                        .filter(overwrite -> overwrite.getType() == PermissionOverwrite.Type.ROLE) // On ne garde que les Rôles
                        .flatMap(overwrite -> {
                            long roleId = overwrite.getTargetId().asLong();
                            // On appelle l'API pour lier le rôle au channel
                            return Mono.fromFuture(() -> apiClient.addRoleToChannel(channel.getId().asLong(), roleId));
                        })
                        .then()
                );
    }

	
    public Mono<Void> syncMember(Member member) {
        // 1. Créer le User
        UserCreateDTO userDto = new UserCreateDTO(
                member.getId().asLong(),
                member.getUsername(),
                member.getDisplayName()
        );

        return Mono.fromFuture(() -> apiClient.createUser(userDto))
                // 2. Lier User -> Guild
                .then(Mono.fromFuture(() -> 
                    apiClient.addUserToGuild(member.getGuildId().asLong(), member.getId().asLong())
                ))
                // 3. Lier User -> Roles (NOUVEAU)
                .then(
                    Flux.fromIterable(member.getRoleIds()) // On récupère les IDs des rôles du membre
                        .flatMap(roleId -> 
                             // On appelle l'API pour lier chaque rôle à l'utilisateur
                             Mono.fromFuture(() -> apiClient.addRoleToUser(member.getId().asLong(), roleId.asLong()))
                        )
                        .then()
                );
    }
}