plugins {
    id("java")
    id("application")
}

group = "com.garden_bot"
version = "1.0-SNAPSHOT"


java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.garden_bot.Main") // 👈 ajusta al paquete que uses
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("net.dv8tion:JDA:5.2.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
    implementation("ch.qos.logback:logback-classic:1.5.6")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.garden_bot.Main"
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}