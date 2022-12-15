package ru.vk.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import org.jetbrains.annotations.NotNull;
import ru.vk.messages.InactiveClanMsg;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Administrator extends AbstractVerticle {
    private final @NotNull String clanName;

    private long usersCount;

    private long moderatorsCount;

    private EventBus eventBus;

    private final long CHANGE_MAX_USERS_AND_MODERATORS_DELAY = 5000;

    private final long CHECK_DELAY = 1000;

    public Administrator(@NotNull String clanName,
                         long usersCount,
                         long moderatorsCount) {
        this.clanName = clanName;
        this.usersCount = usersCount;
        this.moderatorsCount = moderatorsCount;
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        this.eventBus = vertx.eventBus();

        failIfNotUniqueAdmin().onComplete(handler -> {
            if (handler.result()) {
                getStartCompositeFuture().onComplete(result -> {
                    changeMaxModeratorsCount();
                    sendWarningIfNoSpaceForModeratorsOrUsers();
                    startPromise.complete();
                });
            } else {
                startPromise.fail("There is already an admin for this clan - " + clanName);
            }
        });
    }

    private CompositeFuture getStartCompositeFuture() {
        return CompositeFuture.all(getAdminsCounter().result().incrementAndGet(),
                Future.future(handler -> {
                    vertx.eventBus().consumer(clanName + ".users_count", res -> res.reply(usersCount));
                    vertx.eventBus().consumer(clanName + ".moderators_count", res -> res.reply(moderatorsCount));
                    handler.complete();
                }),
                Future.future(handler -> {
                    getMap().onComplete(res -> {
                        final var map = res.result();
                        map.put(clanName, new ArrayList<>(), result -> {
                            if (result.succeeded()) {
                                handler.complete();
                            }
                        });
                    });
                }));
    }

    @Override
    public void stop() {
        new InactiveClanMsg(clanName).send(eventBus);
        System.out.println("Undeploy admin for " + clanName);
        getAdminsCounter().onComplete(handler -> handler.result().decrementAndGet());
    }

    private Future<Boolean> failIfNotUniqueAdmin() {
        return getAdminsCounter().result().get().map(result -> result < 1);
    }

    private Future<Counter> getAdminsCounter() {
        return vertx.sharedData().getCounter(clanName);
    }

    private void changeMaxModeratorsCount() {
        vertx.setPeriodic(CHANGE_MAX_USERS_AND_MODERATORS_DELAY, handler -> {
            final var random = ThreadLocalRandom.current();
            moderatorsCount += random.nextLong(moderatorsCount - moderatorsCount / 2,
                    moderatorsCount / 2 + moderatorsCount + 1) - moderatorsCount;
            if(moderatorsCount < 0) {
                moderatorsCount = 0;
            }

            usersCount += random.nextLong(usersCount - usersCount / 2,
                    usersCount / 2 + usersCount + 1) - usersCount;
            if(usersCount < 0) {
                usersCount = 0;
            }

            System.out.println("Now maximum moderators count is - " + moderatorsCount);
            System.out.println("Now maximum users count is - " + usersCount);
        });
    }

    private void sendWarningIfNoSpaceForModeratorsOrUsers() {
        vertx.setPeriodic(CHECK_DELAY, handler -> {
            getCurrentModeratorsFuture().onComplete(currentModerators -> {
                if (currentModerators.result() > moderatorsCount) {
                    eventBus.publish(clanName + ".moderators_space", "You should leave the clan. No space");
                }
            });

            getCurrentUsersCountInClan().onComplete(currentUsers -> {
                if(currentUsers.result() > usersCount) {
                    eventBus.publish(clanName + ".users_space", "You should leave the clan. No space");
                }
            });
        });
    }

    private Future<Long> getCurrentModeratorsFuture() {
        return getCurrentModeratorsCounter()
                .flatMap(Counter::get);
    }

    private Future<Counter> getCurrentModeratorsCounter() {
        return vertx.sharedData().getCounter(clanName + ".moderators");
    }

    private Future<Integer> getCurrentUsersCountInClan() {
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
}
