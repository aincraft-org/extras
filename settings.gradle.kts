pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    // Gradle does not allow version-catalog plugin aliases in settings files.
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "extras"

nmcpSettings {
    centralPortal {
        username.set(providers.gradleProperty("centralPortalUsername"))
        password.set(providers.gradleProperty("centralPortalPassword"))
        publishingType = "AUTOMATIC"
    }
}
