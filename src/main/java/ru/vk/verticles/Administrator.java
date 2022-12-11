package ru.vk.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.shareddata.Counter;
import org.jetbrains.annotations.NotNull;
import ru.vk.messages.InactiveClanMsg;

import java.util.ArrayList;
import java.util.List;

public class Administrator extends AbstractVerticle {
    private final @NotNull String clanName;

    private final long usersCount;

    private final long moderatorsCount;

    private EventBus eventBus;

    public Administrator(@NotNull String clanName,
                         long usersCount,
                         long moderatorsCount) {
        this.clanName = clanName;
        this.usersCount = usersCount;
        this.moderatorsCount = moderatorsCount;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        failIfNotUniqueAdmin(startPromise);
        this.eventBus = vertx.eventBus();

        vertx.sharedData().<String, List<String>>getClusterWideMap("clans", res -> {
            if (res.succeeded()) {
                final var map = res.result();
                map.put(clanName, new ArrayList<>());
            }
        });

        vertx.eventBus().consumer(clanName + ".users_count", res -> res.reply(usersCount));

        vertx.eventBus().consumer(clanName + ".moderators_count", res -> res.reply(moderatorsCount));
    }

    @Override
    public void stop() {
        new InactiveClanMsg(clanName).send(eventBus);
        System.out.println("Undeploy admin for " + clanName);
        getAdminsCounter().onComplete(handler -> handler.result().decrementAndGet());
    }

    private void failIfNotUniqueAdmin(Promise<Void> startPromise) {
        getAdminsCounter().onComplete(handler -> handler.result().get(asyncResult -> {
            if (asyncResult.succeeded()) {
                if (asyncResult.result() == 1) {
                    startPromise.fail("There is already an admin for this clan - " + clanName);
                } else {
                    handler.result().incrementAndGet();
                    startPromise.complete();
                }
            }
        }));
    }

    private Future<Counter> getAdminsCounter() {
        return vertx.sharedData().getCounter(clanName);
    }
}
