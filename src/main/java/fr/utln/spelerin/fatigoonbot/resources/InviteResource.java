package fr.utln.spelerin.fatigoonbot.resources;

import fr.utln.spelerin.fatigoonbot.services.InviteService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.converters.uni.UniReactorConverters;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/invitations")
public class InviteResource {

	InviteService inviteService;

	@Inject
	public InviteResource(InviteService inviteService){
		this.inviteService = inviteService;
	}

	@GET
	@Path("/{guildId}")
	@Produces(MediaType.TEXT_PLAIN)
	public Uni<Response> getInvite(@PathParam("guildId") long guildId) {
		// inviteService.createInviteCode renvoie un Mono<String>
		return Uni.createFrom().converter(UniReactorConverters.fromMono(), 
				inviteService.createInviteCode(guildId)
			)
			.map(code -> Response.ok(code).build())
			// Si le Mono était vide (switchIfEmpty côté Reactor), l'Uni sera null ici
			.onItem().ifNull().continueWith(() -> 
				Response.status(Response.Status.NOT_FOUND)
						.entity("Guilde introuvable ou erreur Discord")
						.build()
			);
	}
}