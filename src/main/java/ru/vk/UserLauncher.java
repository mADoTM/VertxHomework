package ru.vk;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import ru.vk.verticles.User;

public class UserLauncher {

    public static void main(String[] args) {

        Vertx.clusteredVertx(new VertxOptions(), vertxResult -> {
            final var options = new DeploymentOptions().setWorker(true);

            vertxResult.result().deployVerticle(new User(), options, deploymentId -> {
                if(deploymentId.succeeded()) {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> vertxResult.result().undeploy(deploymentId.result())));
                }
            });

        });
    }
}
