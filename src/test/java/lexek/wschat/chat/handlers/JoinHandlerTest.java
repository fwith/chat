package lexek.wschat.chat.handlers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lexek.wschat.chat.*;
import lexek.wschat.db.model.UserDto;
import lexek.wschat.services.RoomJoinNotificationService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class JoinHandlerTest {
    @Test
    public void shouldHaveJoinType() {
        JoinHandler handler = new JoinHandler(null, null);
        assertEquals(handler.getType(), MessageType.JOIN);
    }

    @Test
    public void shouldBeAvailableForAllRoles() {
        JoinHandler handler = new JoinHandler(null, null);
        assertEquals(handler.getRole(), LocalRole.GUEST);
    }

    @Test
    public void shouldHaveRequiredProperties() throws Exception {
        JoinHandler handler = new JoinHandler(null, null);
        assertEquals(
            handler.requiredProperties(),
            ImmutableSet.of(MessageProperty.ROOM)
        );
    }

    @Test
    public void should() {
        RoomJoinNotificationService roomJoinNotificationService = mock(RoomJoinNotificationService.class);
        Room room = mock(Room.class);
        MessageBroadcaster messageBroadcaster = mock(MessageBroadcaster.class);
        JoinHandler handler = new JoinHandler(roomJoinNotificationService, messageBroadcaster);

        UserDto userDto = new UserDto(0L, "user", GlobalRole.USER, "#ffffff", false, false, null, false);
        User user = new User(userDto);
        Connection connection = spy(new TestConnection(user));
        Chatter chatter = new Chatter(0L, LocalRole.USER, false, null, user);

        when(room.inRoom(connection)).thenReturn(false);
        when(room.join(connection)).thenReturn(chatter);
        when(room.getName()).thenReturn("#main");
        when(room.inRoom(user)).thenReturn(false);

        handler.handle(connection, user, room, chatter, new Message(ImmutableMap.of(
            MessageProperty.TYPE, MessageType.JOIN,
            MessageProperty.ROOM, "#main"
        )));

        verify(room).join(connection);
        verify(messageBroadcaster).submitMessage(eq(Message.joinMessage("#main", userDto)), eq(connection), eq(room.FILTER));
        verify(connection).send(eq(Message.selfJoinMessage("#main", chatter)));
        verify(roomJoinNotificationService).joinedRoom(eq(connection), eq(chatter), eq(room));
    }

    @Test
    public void shouldNotBroadcastMessageWhenOtherSessionActive() {
        RoomJoinNotificationService roomJoinNotificationService = mock(RoomJoinNotificationService.class);
        Room room = mock(Room.class);
        MessageBroadcaster messageBroadcaster = mock(MessageBroadcaster.class);
        JoinHandler handler = new JoinHandler(roomJoinNotificationService, messageBroadcaster);

        UserDto userDto = new UserDto(0L, "user", GlobalRole.USER, "#ffffff", false, false, null, false);
        User user = new User(userDto);
        Connection connection = spy(new TestConnection(user));
        Chatter chatter = new Chatter(0L, LocalRole.USER, false, null, user);

        when(room.inRoom(connection)).thenReturn(false);
        when(room.join(connection)).thenReturn(chatter);
        when(room.getName()).thenReturn("#main");
        when(room.inRoom(user)).thenReturn(true);

        handler.handle(connection, user, room, chatter, new Message(ImmutableMap.of(
            MessageProperty.TYPE, MessageType.JOIN,
            MessageProperty.ROOM, "#main"
        )));

        verify(room).join(connection);
        verify(messageBroadcaster, never()).submitMessage(any(Message.class), any(Connection.class), any(BroadcastFilter.class));
        verify(connection).send(eq(Message.selfJoinMessage("#main", chatter)));
        verify(roomJoinNotificationService).joinedRoom(eq(connection), eq(chatter), eq(room));
    }

    @Test
    public void shouldNotBroadcastMessageWhenRoleLowerThanUser() {
        RoomJoinNotificationService roomJoinNotificationService = mock(RoomJoinNotificationService.class);
        Room room = mock(Room.class);
        MessageBroadcaster messageBroadcaster = mock(MessageBroadcaster.class);
        JoinHandler handler = new JoinHandler(roomJoinNotificationService, messageBroadcaster);

        UserDto userDto = new UserDto(0L, "user", GlobalRole.UNAUTHENTICATED, "#ffffff", false, false, null, false);
        User user = new User(userDto);
        Connection connection = spy(new TestConnection(user));
        Chatter chatter = new Chatter(0L, LocalRole.GUEST, false, null, user);

        when(room.inRoom(connection)).thenReturn(false);
        when(room.join(connection)).thenReturn(chatter);
        when(room.getName()).thenReturn("#main");
        when(room.inRoom(user)).thenReturn(false);

        handler.handle(connection, user, room, chatter, new Message(ImmutableMap.of(
            MessageProperty.TYPE, MessageType.JOIN,
            MessageProperty.ROOM, "#main"
        )));

        verify(room).join(connection);
        verify(messageBroadcaster, never()).submitMessage(any(Message.class), any(Connection.class), any(BroadcastFilter.class));
        verify(connection).send(eq(Message.selfJoinMessage("#main", chatter)));
        verify(roomJoinNotificationService).joinedRoom(eq(connection), eq(chatter), eq(room));
    }

    @Test
    public void shouldReturnErrorIfAlreadyInRoom() {
        RoomJoinNotificationService roomJoinNotificationService = mock(RoomJoinNotificationService.class);
        Room room = mock(Room.class);
        MessageBroadcaster messageBroadcaster = mock(MessageBroadcaster.class);
        JoinHandler handler = new JoinHandler(roomJoinNotificationService, messageBroadcaster);

        UserDto userDto = new UserDto(0L, "user", GlobalRole.USER, "#ffffff", false, false, null, false);
        User user = new User(userDto);
        Connection connection = spy(new TestConnection(user));
        Chatter chatter = new Chatter(0L, LocalRole.USER, false, null, user);

        when(room.inRoom(connection)).thenReturn(true);
        when(room.join(connection)).thenReturn(chatter);
        when(room.getName()).thenReturn("#main");
        when(room.inRoom(user)).thenReturn(true);

        handler.handle(connection, user, room, chatter, new Message(ImmutableMap.of(
            MessageProperty.TYPE, MessageType.JOIN,
            MessageProperty.ROOM, "#main"
        )));

        verify(room, never()).join(connection);
        verify(messageBroadcaster, never()).submitMessage(any(Message.class), any(Connection.class), any(BroadcastFilter.class));
        verify(connection, never()).send(eq(Message.selfJoinMessage("#main", chatter)));
        verify(roomJoinNotificationService, never()).joinedRoom(any(Connection.class), any(Chatter.class), any(Room.class));
        verify(connection).send(eq(Message.errorMessage("ROOM_ALREADY_JOINED")));
    }
}