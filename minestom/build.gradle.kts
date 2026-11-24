import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.SourceSetContainer

dependencies {
    compileOnly(project(":common"))
    compileOnly(libs.react.api)
    compileOnly(libs.actions)
    compileOnly(libs.cloud.core)
    compileOnly(libs.minestom)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.schedula.common)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

configurations.configureEach {
    if (name == "compileClasspath" || name.endsWith("CompileClasspath")) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
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
    archiveFileName.set("hologram-addon-minestom-${project.version}.jar")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    val commonClasses = project(":common").extensions.getByType<SourceSetContainer>()
        .named("main")
        .map { it.output.classesDirs }
    from(commonClasses)
}
