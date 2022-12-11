package ru.vk.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import org.jetbrains.annotations.NotNull;
import ru.vk.messages.InactiveClanMsg;
import ru.vk.messages.InviteClanMsg;
import ru.vk.messages.InvitedClanMsg;

import java.util.List;

public class Moderator extends AbstractVerticle {
    private final @NotNull String clanName;
    private EventBus eventBus;

    public Moderator(@NotNull String clanName) {
        this.clanName = clanName;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        eventBus = vertx.eventBus();
        failDeployIfNoSpaceForNewModerator(startPromise);

        kickUser();

        InviteClanMsg.subscribeInviteToModerator(clanName, eventBus, this::inviteHandler);

        InactiveClanMsg.subscribe(clanName, eventBus, handler -> {
            System.out.println("should undeploy");
            System.exit(0);
        });
    }

    private void kickUser() {
        eventBus.consumer(clanName + ".leave", handler -> {
            String userId = handler.body().toString();
            System.out.println(clanName + " " + userId);
            removeUserFromClan(userId);
        });
    }

    @Override
    public void stop() {
        getCurrentModeratorsCounter().result().decrementAndGet();
    }

    private void inviteHandler(Message<String> handler) {
        CompositeFuture.all(getClanMaxUsersCount(), getCurrentUsersInClan())
                .onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        CompositeFuture result = asyncResult.result();
                        long maxCount = result.resultAt(0);
                        int currentCount = result.resultAt(1);
                        String userId = handler.body();

                        if (currentCount < maxCount) {
                            inviteUserInClan(userId);
                            sendInvitedMessage(userId);
                        } else {
                            System.out.println(userId + " wants to join us. But we have no space for him :(");
                        }
                    }
                });
    }

    private void inviteUserInClan(String userId) {
        getUsersInClan().onComplete(asyncResult -> {
            var list = asyncResult.result();
            list.add(userId);
            putListInMap(list);
        });
    }

    private void sendInvitedMessage(String userId) {
        System.out.println("INVITING " + userId);
        new InvitedClanMsg(userId, clanName).send(eventBus);
    }

    private void failDeployIfNoSpaceForNewModerator(Promise<Void> startPromise) {
        CompositeFuture.all(getMaxModeratorsFuture(), getCurrentModeratorsFuture()).onComplete(asyncResult -> {
            if (asyncResult.succeeded()) {
                CompositeFuture result = asyncResult.result();
                long maxCount = result.resultAt(0);
                long currentCount = result.resultAt(1);
                if (currentCount < maxCount) {
                    startPromise.complete();
                    getCurrentModeratorsCounter().result().incrementAndGet();

                } else {
                    startPromise.fail("There is no space for moderators in clan " + clanName);
                }
            }
        });
    }

    private Future<Long> getCurrentModeratorsFuture() {
        return getCurrentModeratorsCounter()
                .flatMap(Counter::get);
    }

    private Future<Counter> getCurrentModeratorsCounter() {
        return vertx.sharedData().getCounter(clanName + ".moderators");
    }

    private Future<Long> getMaxModeratorsFuture() {
        return eventBus
                .request(clanName + ".moderators_count", null)
                .map(mapper -> Long.parseLong(mapper.body().toString()));
    }

    private Future<Long> getClanMaxUsersCount() {
        return eventBus
                .request(clanName + ".users_count", null)
                .map(mapper -> Long.parseLong(mapper.body().toString()));
    }

    private Future<Integer> getCurrentUsersInClan() {
        return getUsersInClan().map(List::size);
    }

    private Future<AsyncMap<String, List<String>>> getMap() {
        return vertx
                .sharedData()
                .<String, List<String>>getClusterWideMap("clans");
    }

    private Future<List<String>> getUsersInClan() {
        return getMap()
                .flatMap(map -> map.get(clanName));
    }

    private void removeUserFromClan(String userId) {
        getUsersInClan().onComplete(list -> {
            final var listResult = list.result();
            listResult.remove(userId);
            System.out.println(userId + " kicked from clan");
            putListInMap(listResult);
        });
    }

    private void putListInMap(@NotNull List<String> list) {
        getMap().onComplete(handler -> handler.result().put(clanName, list));
    }
}