rootProject.name = "ReactHologramAddon"

include(
    "common"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
