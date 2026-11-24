pluginManagement {

    repositories {

        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()

    }

}

rootProject.name = "ReactHologramAddon"

include(
    "common",
    "platform:bukkit",
    "platform:minestom",
    "platform:sponge",
    "platform:fabric"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
