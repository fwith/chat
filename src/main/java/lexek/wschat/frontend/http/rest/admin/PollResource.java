package lexek.wschat.frontend.http.rest.admin;

import lexek.wschat.chat.GlobalRole;
import lexek.wschat.chat.Room;
import lexek.wschat.chat.RoomManager;
import lexek.wschat.db.model.UserDto;
import lexek.wschat.db.model.form.PollForm;
import lexek.wschat.db.model.rest.ErrorModel;
import lexek.wschat.security.jersey.Auth;
import lexek.wschat.security.jersey.RequiredRole;
import lexek.wschat.services.PollService;
import lexek.wschat.services.PollState;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/rooms/{roomId}/polls")
@RequiredRole(GlobalRole.ADMIN)
public class PollResource {
    private final RoomManager roomManager;
    private final PollService pollService;

    public PollResource(RoomManager roomManager, PollService pollService) {
        this.roomManager = roomManager;
        this.pollService = pollService;
    }

    @Path("/current")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public PollState getCurrentPollForRoom(@PathParam("roomId") @Min(0) long roomId) {
        Room room = roomManager.getRoomInstance(roomId);
        if (room == null) {
            throw new WebApplicationException(Response.status(400).entity(new ErrorModel("Unknown room.")).build());
        }
        return pollService.getActivePoll(room);
    }

    @Path("/all")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PollState> getOldPolls(@PathParam("roomId") @Min(0) long roomId) {
        Room room = roomManager.getRoomInstance(roomId);
        if (room == null) {
            throw new WebApplicationException(Response.status(400).entity(new ErrorModel("Unknown room.")).build());
        }
        return pollService.getOldPolls(room);
    }

    @Path("/current")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public PollState createPoll(
        @PathParam("roomId") @Min(0) long roomId,
        @Valid PollForm form,
        @Auth UserDto admin
    ) {
        Room room = roomManager.getRoomInstance(roomId);
        if (room == null) {
            throw new WebApplicationException(Response.status(400).entity(new ErrorModel("Unknown room.")).build());
        }
        return pollService.createPoll(room, admin, form.getQuestion(), form.getOptions());
    }

    @Path("/current")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public PollState closeCurrentPoll(
        @PathParam("roomId") @Min(0) long roomId,
        @Auth UserDto admin
    ) {
        Room room = roomManager.getRoomInstance(roomId);
        if (room == null) {
            throw new WebApplicationException(Response.status(400).entity(new ErrorModel("Unknown room.")).build());
        }
        PollState closedPoll = pollService.getActivePoll(room);
        pollService.closePoll(room, admin);
        return closedPoll;
    }
}