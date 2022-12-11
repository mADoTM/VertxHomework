package ru.vk;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.jetbrains.annotations.NotNull;
import ru.vk.verticles.Moderator;

import java.util.Scanner;

public class ModeratorLauncher {
    public static void main(String[] args) {

        final var clanName = readClanName();

        Vertx.clusteredVertx(new VertxOptions(), vertxResult -> {
            final var options = new DeploymentOptions().setWorker(true);

            vertxResult.result().deployVerticle(new Moderator(clanName), options, res -> {
                if (res.succeeded()) {
                    System.out.println(clanName + " moderator successful works");
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> vertxResult.result().undeploy(res.result())));
                } else {
                    System.out.println("Moderator didn't deploy");
                    System.out.println(res.cause().getMessage());
                }
            });
        });
    }

    private static @NotNull String readClanName() {
        System.out.print("Enter a clan name - ");
        return new Scanner(System.in).nextLine();
    }
}
