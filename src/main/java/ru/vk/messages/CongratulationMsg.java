package ru.vk.messages;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class CongratulationMsg {
    private static final String postfix = ".message_";
    public final String address;

    public CongratulationMsg(String clanName, String userNameTo) {
        this.address = clanName + postfix + userNameTo;
    }

    public static void subscribed(String clanName,
                                  String userId,
                                  EventBus eventBus,
                                  Handler<Message<JsonObject>> handler) {
        eventBus.consumer(clanName + postfix + userId, handler);
    }

    public void send(EventBus eventBus, JsonObject object) {
        eventBus.send(address, object);
    }
}
