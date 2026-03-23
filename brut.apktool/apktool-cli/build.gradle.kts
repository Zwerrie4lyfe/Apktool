val gitRevision: String by rootProject.extra
val apktoolVersion: String by rootProject.extra

plugins {
    application
}

dependencies {
    implementation(project(":brut.apktool:apktool-lib"))
    implementation(libs.commons.cli)
}

application {
    mainClass.set("brut.apktool.Main")

    tasks.run.get().workingDir = file(System.getProperty("user.dir"))
}

tasks {
    processResources {
        from("src/main/resources") {
            include("apktool.properties")
            expand("version" to apktoolVersion, "gitrev" to gitRevision)
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        includeEmptyDirs = false
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register<Delete>("cleanOutputDirectory") {
    delete(fileTree("build/libs") {
        exclude("apktool-cli-sources.jar")
        exclude("apktool-cli-javadoc.jar")
        exclude("apktool-cli-all.jar")
    })
}

val shadowJar = tasks.register("shadowJar", Jar::class) {
    dependsOn("build")
    dependsOn("cleanOutputDirectory")

    group = "build"
    description = "Creates a single executable JAR with all dependencies"
    manifest.attributes["Main-Class"] = "brut.apktool.Main"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree)

    from(dependencies)
    with(tasks.jar.get())
}

tasks.register<Copy>("proguard") {
    dependsOn("shadowJar")
    val originalJar = shadowJar.map { it.outputs.files.singleFile }

    group = "build"
    description = "Copies the runnable JAR to the release artifact name"

    inputs.file(originalJar)
    outputs.file("build/libs/apktool-$apktoolVersion.jar")

    from(originalJar)
    rename { "apktool-$apktoolVersion.jar" }
    into(layout.buildDirectory.dir("libs"))
}

tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository> {
    dependsOn(tasks.named("shadowJar"))
}

tasks.withType<org.gradle.plugins.signing.Sign> {
    dependsOn(tasks.named("shadowJar"))
}

tasks.withType<org.gradle.api.publish.tasks.GenerateModuleMetadata> {
    dependsOn(tasks.named("shadowJar"))
}
