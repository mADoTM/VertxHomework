package ru.vk;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.jetbrains.annotations.NotNull;
import ru.vk.verticles.Administrator;

import java.util.Scanner;

public class AdministratorLauncher {
    private static final @NotNull Scanner scn = new Scanner(System.in);
    public static void main(String[] args) {

        final var clanName = readClanName();
        final var clanUsersCount = readLong("users");
        final var clanModeratorsCount = readLong("moderators");

        Vertx.clusteredVertx(new VertxOptions(), vertxResult -> {
            final var options = new DeploymentOptions().setWorker(true);

            vertxResult.result().deployVerticle(new Administrator(clanName,
                    clanUsersCount,
                    clanModeratorsCount), options, res -> {
                if(res.succeeded()) {
                    System.out.println(clanName + " successful works");
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> vertxResult.result().undeploy(res.result())));
                }
                else {
                    System.out.println("Administrator didn't deploy");
                    System.out.println(res.cause().getMessage());
                }
            });
        });
    }

    private static long readLong(String message) {
        System.out.print("Enter " + message + " count: ");
        return scn.nextLong();
    }

    private static @NotNull String readClanName() {
        System.out.print("Enter a clan name - " );
        return new Scanner(System.in).nextLine();
    }
}
