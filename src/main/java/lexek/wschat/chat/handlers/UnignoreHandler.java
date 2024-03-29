package lexek.wschat.chat.handlers;

import com.google.common.collect.ImmutableSet;
import lexek.wschat.chat.Connection;
import lexek.wschat.chat.e.EntityNotFoundException;
import lexek.wschat.chat.e.InvalidInputException;
import lexek.wschat.chat.model.*;
import lexek.wschat.chat.processing.AbstractGlobalMessageHandler;
import lexek.wschat.services.IgnoreService;

public class UnignoreHandler extends AbstractGlobalMessageHandler {
    private final IgnoreService ignoreService;

    public UnignoreHandler(IgnoreService ignoreService) {
        super(ImmutableSet.of(MessageProperty.NAME), MessageType.UNIGNORE, GlobalRole.USER, true);
        this.ignoreService = ignoreService;
    }

    @Override
    public void handle(Connection connection, User user, Message message) {
        try {
            String name = message.get(MessageProperty.NAME);
            ignoreService.unignore(user, name);
            connection.send(Message.ignoredMessage(ignoreService.getIgnoredNames(user)));
            connection.send(Message.ignoreMessage(MessageType.UNIGNORE, name));
        } catch (EntityNotFoundException e) {
            if (e.getMessage().equals("user")) {
                connection.send(Message.errorMessage("UNKNOWN_USER"));
            }
        } catch (InvalidInputException e) {
            connection.send(Message.errorMessage(e.message()));
        }
    }

}
