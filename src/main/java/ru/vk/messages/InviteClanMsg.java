package ru.vk.messages;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public final class InviteClanMsg extends JsonObject {
    private static final String postfix = ".invite";
    private final String address;
    private final String userName;

    public InviteClanMsg(String clanName, String userName) {
        this.address = clanName + postfix;
        this.userName = userName;
    }

    public static void subscribeInviteToModerator(String clanName,
                                                  EventBus eventBus,
                                                  Handler<Message<String>> handler) {
        eventBus.consumer(clanName + postfix, handler);
    }

    public void send(EventBus eventBus) {
        eventBus.send(address, userName);
    }
}
