plugins {
    java
    `maven-publish`
    signing
    checkstyle
    pmd
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.spotless)
}

group = "dev.mintychochip"
val requestedReleaseVersion = providers.gradleProperty("releaseVersion")
val releaseVersion = requestedReleaseVersion.orElse("0.0.0-local")
version = releaseVersion.get()

gradle.taskGraph.whenReady {
    val publicationRequested = gradle.startParameter.taskNames.any { taskName ->
        taskName.substringAfterLast(':').startsWith("publish")
    }
    if (publicationRequested && requestedReleaseVersion.orNull.isNullOrBlank()) {
        throw GradleException(
            "Publishing requires an explicit -PreleaseVersion to avoid reusing an immutable Central version.",
        )
    }
}

val mainSourceSet = sourceSets.getByName("main")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

pmd {
    toolVersion = "7.16.0"
    ruleSetFiles = files(rootProject.file("config/pmd/pmd.xml"))
    isConsoleOutput = true
    isIgnoreFailures = false
}

spotless {
    java {
        googleJavaFormat("1.28.0")
        targetExclude("build/**")
    }
}
spotbugs {
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.HIGH)
    ignoreFailures.set(false)
}

val apiJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("apiJar") {
    archiveBaseName.set("extras-api")
    archiveClassifier.set("")
    dependsOn(tasks.classes)
    from(mainSourceSet.output) {
        include("dev/mintychochip/api/**")
    }
}

val apiSourcesJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("apiSourcesJar") {
    archiveBaseName.set("extras-api")
    archiveClassifier.set("sources")
    from(mainSourceSet.allJava) {
        include("dev/mintychochip/api/**")
    }
}

val apiJavadoc = tasks.register<org.gradle.api.tasks.javadoc.Javadoc>("apiJavadoc") {
    dependsOn(tasks.classes)
    source(mainSourceSet.allJava.matching {
        include("dev/mintychochip/api/**")
    })
    classpath = mainSourceSet.compileClasspath
    destinationDir = layout.buildDirectory.dir("docs/api-javadoc").get().asFile
    options.encoding = "UTF-8"
}

val apiJavadocJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("apiJavadocJar") {
    archiveBaseName.set("extras-api")
    archiveClassifier.set("javadoc")
    dependsOn(apiJavadoc)
    from(apiJavadoc)
}

val paperSourcesJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("paperSourcesJar") {
    archiveBaseName.set("extras-paper")
    archiveClassifier.set("sources")
    from(mainSourceSet.allJava)
}

val paperJavadocJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("paperJavadocJar") {
    archiveBaseName.set("extras-paper")
    archiveClassifier.set("javadoc")
    dependsOn(tasks.javadoc)
    from(tasks.javadoc)
}

fun org.gradle.api.publish.maven.MavenPom.configureExtrasPom(
    displayName: String,
    artifactDescription: String,
) {
    name.set(displayName)
    description.set(artifactDescription)
    url.set("https://github.com/aincraft-org/modular-extras")

    licenses {
        license {
            name.set("MIT License")
            url.set("https://opensource.org/license/mit/")
            distribution.set("repo")
        }
    }

    developers {
        developer {
            id.set("jlo")
            name.set("jlo")
            url.set("https://github.com/aincraft-org")
        }
    }

    scm {
        connection.set("scm:git:https://github.com/aincraft-org/modular-extras.git")
        developerConnection.set("scm:git:ssh://git@github.com/aincraft-org/modular-extras.git")
        url.set("https://github.com/aincraft-org/modular-extras")
    }
}

publishing {
    publications {
        create<org.gradle.api.publish.maven.MavenPublication>("extrasApi") {
            artifactId = "extras-api"
            artifact(apiJar)
            artifact(apiSourcesJar)
            artifact(apiJavadocJar)

            pom {
                configureExtrasPom(
                    displayName = "Extras API",
                    artifactDescription = "Bukkit-free API for parties, friendships, titles, and player mailboxes.",
                )
            }
        }

        create<org.gradle.api.publish.maven.MavenPublication>("extrasPaper") {
            artifactId = "extras-paper"
            artifact(tasks.shadowJar)
            artifact(paperSourcesJar)
            artifact(paperJavadocJar)

            pom {
                configureExtrasPom(
                    displayName = "Extras Paper",
                    artifactDescription = "Standalone Paper plugin for parties, friendships, titles, and player mailboxes.",
                )
            }
        }
    }
}

signing {
    val signingKey = providers.gradleProperty("signingKey")
        .orElse(providers.environmentVariable("SIGNING_KEY"))
    val signingPassword = providers.gradleProperty("signingPassword")
        .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
    useInMemoryPgpKeys(signingKey.orNull, signingPassword.orNull)
    isRequired = !providers.gradleProperty("skipSigning").isPresent
    sign(publishing.publications["extrasApi"])
    sign(publishing.publications["extrasPaper"])
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("extras-paper")
    archiveClassifier.set("")
    mustRunAfter(tasks.jar)
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}

tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
    dependsOn(tasks.jar)
    dependsOn(tasks.shadowJar)
    dependsOn(apiJar)
    dependsOn(apiSourcesJar)
    dependsOn(apiJavadocJar)
    dependsOn(paperSourcesJar)
    dependsOn(paperJavadocJar)
}

tasks.build {
    dependsOn(tasks.shadowJar)
    dependsOn(apiJar)
    dependsOn(apiSourcesJar)
    dependsOn(apiJavadocJar)
    dependsOn(paperSourcesJar)
    dependsOn(paperJavadocJar)
}

tasks.runServer {
    minecraftVersion("1.21.11")
    runDirectory.set(layout.projectDirectory.dir("run"))
}
