pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "UtilitiesGitHubPackages"
            url = uri("https://maven.pkg.github.com/mintychochip/Utilities")
            credentials {
                username =
                    providers.gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .getOrElse("")
                password =
                    providers.gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                        .getOrElse("")
            }
        }
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "extras"
