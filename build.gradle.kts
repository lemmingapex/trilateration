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
    // The tracking animation's Kalman filter. The library itself does not depend on it.
    docs.implementationConfigurationName(libs.hipparchus.filtering)
}

tasks.register<JavaExec>("renderDocs") {
    group = "documentation"
    description = "Regenerates the README figures in docs/images from real solver output."
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

mavenPublishing {
    publishToMavenCentral()
    // Sign only when a key is provided (CI release job), so local builds and
    // publishToMavenLocal work without any credentials.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
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
