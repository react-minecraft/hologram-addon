import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.SourceSetContainer

plugins {
    alias(libs.plugins.fabric.loom)
}

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://jitpack.io")
}

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.fabric.minecraft.get()}")
    compileOnly(project(":common"))
    compileOnly(libs.fabric.loader)
    compileOnly(libs.fabric.api)
    compileOnly(libs.react.api)
    compileOnly(libs.actions)
    compileOnly(libs.cloud.core)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.schedula.common)
    compileOnly(libs.schedula.fabric)
    compileOnly(libs.fabric.adventure.platform)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.packetevents.api.fabric)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

configurations.configureEach {
    if (name == "compileClasspath" || name.endsWith("CompileClasspath")) {
        attributes.attribute(
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            25
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

tasks.processResources {
    from(project(":common").file("src/main/resources")) {
        exclude("react-addon.yml")
    }
    filesMatching("react-addon.yml") {
        expand(
            "version" to project.version,
            "description" to (project.description ?: ""),
            "name" to rootProject.name
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("hologram-addon-fabric-${project.version}.jar")
    configurations = emptyList()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    val commonClasses = project(":common").extensions.getByType<SourceSetContainer>()
        .named("main")
        .map { it.output.classesDirs }
    from(commonClasses)
}
