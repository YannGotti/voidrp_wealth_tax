plugins {
    java
    id("com.gradleup.shadow") version "8.3.10"
}

group = "ru.voidrp"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    compileOnly("net.luckperms:api:5.4")
}

tasks {
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(mapOf("version" to project.version))
        }
    }
    shadowJar {
        archiveClassifier.set("all")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    build { dependsOn(shadowJar) }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
