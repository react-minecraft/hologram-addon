import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.g4vrk.hologram.gradle.ExpandAddonDescriptor
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.SourceSetContainer

repositories {
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly(project(":common"))
    compileOnly(libs.react.api)
    compileOnly(libs.react.common)
    compileOnly(libs.actions)
    compileOnly(libs.cloud.paper)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.schedula.common)
    compileOnly(libs.paper.api)
    compileOnly(libs.packetevents)
    compileOnly(libs.adventure.api)
    compileOnly(libs.fastutil)
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

val commonResourceFiles = rootProject.fileTree("common/src/main/resources").matching {
    exclude("react-addon.yml")
}
val addonDescriptorProperties = mapOf(
    "version" to project.version.toString(),
    "description" to project.description.orEmpty(),
    "name" to rootProject.name
)

configurations.configureEach {
    if (name == "compileClasspath" || name.endsWith("CompileClasspath")) {
        attributes.attribute(
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            17
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(16)
}

tasks.processResources {
    from(commonResourceFiles)
    filesMatching("react-addon.yml", ExpandAddonDescriptor(addonDescriptorProperties))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("${rootProject.name}-${project.name}-${project.version}.jar")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    val commonClasses = project(":common").extensions.getByType<SourceSetContainer>()
        .named("main")
        .map { it.output.classesDirs }
    from(commonClasses)
}
