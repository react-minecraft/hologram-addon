import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.g4vrk.hologram.gradle.ExpandAddonDescriptor
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.SourceSetContainer

repositories {
    maven("https://repo.spongepowered.org/maven/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

val commonResourceFiles = rootProject.fileTree("common/src/main/resources").matching {
    exclude("react-addon.yml")
}
val addonDescriptorProperties = mapOf(
    "version" to project.version.toString(),
    "description" to project.description.orEmpty(),
    "name" to rootProject.name
)

dependencies {
    compileOnly(project(":common"))
    compileOnly(libs.react.api)
    compileOnly(libs.actions)
    compileOnly(libs.cloud.core)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.schedula.common)
    compileOnly(libs.sponge.api)
    compileOnly(libs.packetevents.sponge)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configurations.configureEach {
    if (name == "compileClasspath" || name.endsWith("CompileClasspath")) {
        attributes.attribute(
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            21
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.processResources {
    from(commonResourceFiles)
    filesMatching("react-addon.yml", ExpandAddonDescriptor(addonDescriptorProperties))
}

tasks.named<ShadowJar>("shadowJar") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    val commonClasses = project(":common").extensions.getByType<SourceSetContainer>()
        .named("main")
        .map { it.output.classesDirs }
    from(commonClasses)
}
