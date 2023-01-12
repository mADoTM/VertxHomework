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
        failDeployIfNoSpaceForNewModerator().onComplete(asyncResult -> {
            if (asyncResult.succeeded()) {
                CompositeFuture result = asyncResult.result();
                long maxCount = result.resultAt(0);
                long currentCount = result.resultAt(1);
                if (currentCount < maxCount) {
                    getStartCompositeFuture().onComplete(handler -> {
                        startPromise.complete();
                    });
                } else {
                    startPromise.fail("There is no space for moderators in clan " + clanName);
                }
            }
        });
    }

    @Override
    public void stop() {
        getCurrentModeratorsCounter().result().decrementAndGet();
    }

    private CompositeFuture getStartCompositeFuture() {
        return CompositeFuture.all(getCurrentModeratorsCounter().result().incrementAndGet(),
                Future.future(promise -> {
                    subscribeOnLeaveMsg();
                    subscribeSpaceMessage();

                    InviteClanMsg.subscribeInviteToModerator(clanName, eventBus, this::inviteHandler);

                    InactiveClanMsg.subscribe(clanName, eventBus, handler -> {
                        System.out.println("should undeploy");
                        System.exit(0);
                    });
                    promise.complete();
                }));
    }

    private void subscribeOnLeaveMsg() {
        eventBus.consumer(clanName + ".leave", handler -> {
            final var userId = handler.body().toString();
            System.out.println(clanName + " " + userId);
            removeUserFromClan(userId);
        });
    }

    private void inviteHandler(Message<String> handler) {
        CompositeFuture.all(getClanMaxUsersCount(), getCurrentUsersInClan())
                .onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        final var result = asyncResult.result();
                        long maxCount = result.resultAt(0);
                        int currentCount = result.resultAt(1);
                        String userId = handler.body();

                        if (currentCount < maxCount) {
                            inviteUserInClan(userId).onComplete(res -> {
                                sendInvitedMessage(userId);
                            });
                        } else {
                            System.out.println(userId + " wants to join us. But we have no space for him :(");
                        }
                    }
                });
    }

    private Future<List<String>> inviteUserInClan(String userId) {
        return getUsersInClan().onComplete(asyncResult -> {
            final var list = asyncResult.result();
            list.add(userId);
            putListInMap(list);
        });
    }

    private void sendInvitedMessage(String userId) {
        System.out.println("INVITING " + userId);
        new InvitedClanMsg(userId, clanName).send(eventBus);
    }

    private void subscribeSpaceMessage() {
        eventBus.consumer(clanName + ".moderators_space", handler -> {
            System.out.println(handler.body());
        });
    }

    private Future<CompositeFuture> failDeployIfNoSpaceForNewModerator() {
        return CompositeFuture.all(getMaxModeratorsFuture(), getCurrentModeratorsFuture()).map(Future::result);
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