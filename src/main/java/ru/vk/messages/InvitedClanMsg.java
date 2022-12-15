package ru.vk.messages;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public class InvitedClanMsg {
    private static final String postfix = ".invited";
    public final String address;

    public final String clanName;

    public InvitedClanMsg(String userName, String clanName) {
        this.address = userName + postfix;
        this.clanName = clanName;
    }

    public static void subscribe(String userId,
                                 EventBus eventBus,
                                 Handler<Message<String>> handler) {
        eventBus.consumer(userId + postfix, handler);
    }

    public void send(EventBus eventBus) {
        eventBus.send(address, clanName);
    }
}
