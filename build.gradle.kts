plugins {
    `java-library`
    jacoco
    alias(libs.plugins.maven.publish)
}

group = "com.lemmingapex.trilateration"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Hipparchus is an implementation detail: no Hipparchus type appears in the public API.
    implementation(libs.hipparchus.core)
    implementation(libs.hipparchus.optim)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Java 17 is the minimum supported version. `--release` checks the APIs used, whichever newer
// JDK runs the build, and sets the Java version that Gradle's published metadata advertises.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Documentation figures: generated from real solver output by `./gradlew renderDocs`.
// A separate source set, so none of this code ships in the library jar. It lives in the
// library's package to use package-private hooks such as SolverTrace.
val docs = sourceSets.create("docs") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

dependencies {
    // Used only by the docs figures; the library itself depends on neither.
    docs.implementationConfigurationName(libs.hipparchus.filtering) // the tracking animation's Kalman filter
    docs.implementationConfigurationName(libs.jsvg) // renders the PNG copies of the figures
}

tasks.register<JavaExec>("renderDocs") {
    group = "documentation"
    description = "Regenerates the README figures in docs/images, as SVG and PNG, from real solver output."
    classpath = docs.runtimeClasspath
    mainClass = "com.lemmingapex.trilateration.DocsFigures"
    args(layout.projectDirectory.dir("docs/images").asFile.absolutePath)
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "com.lemmingapex.trilateration",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Xwerror", true)
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// Sign when a key is configured: in memory (signingInMemoryKey, as the release workflow does) or
// as a keyring file (signing.keyId, signing.password and signing.secretKeyRingFile, for example in
// ~/.gradle/gradle.properties). Without a key, builds and publishToMavenLocal still work, but
// publishing to Maven Central fails before anything is uploaded. A property counts only when it is
// non-blank: GitHub Actions passes a missing secret as an empty string. A lone signing.keyId, left
// in ~/.gradle/gradle.properties for another project, does not turn signing on.
fun isNonBlankProperty(name: String) = providers.gradleProperty(name).orNull?.isNotBlank() == true
val signingConfigured = isNonBlankProperty("signingInMemoryKey") ||
    listOf("signing.keyId", "signing.password", "signing.secretKeyRingFile").all(::isNonBlankProperty)

mavenPublishing {
    publishToMavenCentral()
    if (signingConfigured) {
        signAllPublications()
    }

    coordinates(group.toString(), "trilateration", version.toString())

    pom {
        name = "trilateration"
        description = "Trilateration and multilateration for Java: estimates a position from measured distances to anchors at known positions, in any number of dimensions or from latitude and longitude."
        inceptionYear = "2015"
        url = "https://github.com/lemmingapex/trilateration"
        licenses {
            license {
                name = "The MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "lemmingapex"
                name = "Scott Wiedemann"
                email = "lemmingapex@gmail.com"
                url = "https://github.com/lemmingapex"
            }
        }
        scm {
            url = "https://github.com/lemmingapex/trilateration"
            connection = "scm:git:git://github.com/lemmingapex/trilateration.git"
            developerConnection = "scm:git:ssh://git@github.com/lemmingapex/trilateration.git"
        }
    }
}

if (!signingConfigured) {
    // The action must not refer to anything declared in this script: the configuration cache
    // cannot store a reference to the script.
    tasks.matching { it.name == "prepareMavenCentralPublishing" || it.name.endsWith("ToMavenCentralRepository") }.configureEach {
        doFirst {
            throw GradleException(
                "Maven Central requires signed artifacts, but no signing key is configured. Set signingInMemoryKey " +
                    "and signingInMemoryKeyPassword, or signing.keyId, signing.password and signing.secretKeyRingFile.")
        }
    }
}
