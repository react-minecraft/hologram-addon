rootProject.name = "ReactHologramAddon"

include(
    "common",
    "minestom"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
