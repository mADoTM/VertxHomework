plugins {
    id("java")
}

group = "ru.vk"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val vertxVersion = "4.3.5"

dependencies {
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-hazelcast:$vertxVersion")

    implementation("org.jetbrains:annotations:23.0.0")


    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}