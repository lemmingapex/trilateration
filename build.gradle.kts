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

// The library targets Java 8 for the widest compatibility.
tasks.compileJava {
    options.release = 8
    options.encoding = "UTF-8"
    // "-options" silences the "source value 8 is obsolete" notice from newer JDKs.
    options.compilerArgs.addAll(listOf("-Xlint:all,-options", "-Werror"))
}

// Tests only run on the build JDK, so they can use a modern language level (JUnit 6 needs 17).
tasks.compileTestJava {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Documentation figures: generated from real solver output by `./gradlew renderDocs`.
// A separate source set, so none of this code ships in the library jar. It lives in the
// library's package to use package-private hooks such as SolverTrace.
val docs by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

tasks.named<JavaCompile>(docs.compileJavaTaskName) {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
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
        description = "Solves n-dimensional trilateration (multilateration) problems with a nonlinear least squares optimizer."
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
