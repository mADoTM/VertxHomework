package ru.vk.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import org.jetbrains.annotations.Nullable;
import ru.vk.messages.CongratulationMsg;
import ru.vk.messages.InviteClanMsg;
import ru.vk.messages.InvitedClanMsg;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class User extends AbstractVerticle {
    private @Nullable String id;
    private @Nullable String clan;

    private EventBus eventBus;

    @Override
    public void start() {
        id = deploymentID();
        this.eventBus = vertx.eventBus();
        sendRandomClanInvite();


        eventBus.consumer(clan + ".inactive", result -> {
            clan = null;
            leaveClan();
            sendRandomClanInvite();
        });
    }

    @Override
    public void stop() {
        leaveClan();
    }

    private void leaveClan() {
        eventBus.send(clan + ".leave", id);
    }

    private void sendRandomClanInvite() {
        long inviteSender = vertx.setPeriodic(1000, timer -> getAllClans().onComplete(handler -> {
            var set = handler.result();
            final var random = ThreadLocalRandom.current();
            if(set.size() > 0) {
                final var randomClanId = random.nextInt(set.size());

                final var clanName = set.toArray()[randomClanId].toString();
                System.out.println("My id is " + id);
                new InviteClanMsg(clanName, id).send(eventBus);
            }
        }));

        InvitedClanMsg.subscribe(id, eventBus, handler -> {
            clan = handler.body();
            vertx.cancelTimer(inviteSender);
            vertx.setPeriodic(1000, timer -> sendCongratulations());
            receiveCongratulations();
        });
    }

    private void sendCongratulations() {
        getUsersInClan(clan).onComplete(asyncResult -> {
            final var users = asyncResult.result();
            final var random = ThreadLocalRandom.current();
            final var randomUserIndex = random.nextInt(users.size());
            final var randomUserId = users.get(randomUserIndex);

            JsonObject object = new JsonObject();
            object.put("from", id);
            object.put("message", "I am the best player in this clan!!!!");

            new CongratulationMsg(clan, randomUserId).send(eventBus, object);
        });
    }

    private void receiveCongratulations() {
        CongratulationMsg.subscribed(clan, id, eventBus, handler -> {
            final var jsonObject = (JsonObject) handler.body();
            final var from = jsonObject.getString("from");

            if (from.equals(id)) {
                System.out.println("[USER-" + id + "]: YEEEEAH I AM THE BEST!!!!!");
            } else {
                final var message = jsonObject.getString("message");
                System.out.println("[USER-" + from + "]->[USER-" + id + "]: " + message);
            }
        });
    }

    private Future<AsyncMap<String, List<String>>> getClansMap() {
        return vertx
                .sharedData()
                .<String, List<String>>getClusterWideMap("clans");
    }

    private Future<Set<String>> getAllClans() {
        return getClansMap().flatMap(AsyncMap::keys);
    }

    private  Future<List<String>> getUsersInClan(String clan) {
        return getClansMap()
                .flatMap(map ->  map.get(clan));
    }
}
