package ru.vk.messages;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public class InactiveClanMsg {
    private final String address;

    private static final String postfix = ".inactive";

    public InactiveClanMsg(String clanName) {
        this.address = clanName + postfix;
    }

    public static void subscribe(String clanName,
                                 EventBus eventBus,
                                 Handler<Message<Object>> handler) {
        eventBus.consumer(clanName + postfix, handler);
    }

    public void send(EventBus eventBus) {
        eventBus.send(address, null);
    }
}
