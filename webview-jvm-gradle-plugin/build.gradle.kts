plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("io.freefair.lombok") version "9.5.0"
}

group = "dev.ivchenko.webview"
version = providers.gradleProperty("projectVersion").getOrElse("0.1.0-SNAPSHOT")
description = "Gradle plugin configuring GraalVM native-image builds of webview-jvm applications: size, heap, Windows icon and version resources, macOS Info.plist."

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withJavadocJar()
    withSourcesJar()
}

lombok {
    version = "1.18.48"
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

dependencies {
    // GraalVM Native Build Tools: applied and configured by this plugin
    implementation("org.graalvm.buildtools:native-gradle-plugin:1.1.12")

    // JUnit
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("webview") {
            id = "dev.ivchenko.webview"
            implementationClass = "dev.ivchenko.webview.gradle.WebviewPlugin"
            displayName = "webview-jvm"
            description = project.description
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            url.set("https://github.com/fakeivchenko/webview-jvm")
            developers {
                developer {
                    id.set("fakeivchenko")
                    name.set("Anton Ivchenko")
                }
            }
            scm {
                url.set("https://github.com/fakeivchenko/webview-jvm")
                connection.set("scm:git:https://github.com/fakeivchenko/webview-jvm.git")
            }
        }
    }
    repositories {
        maven {
            name = "reposilite"
            url = uri("https://repo.ivchenko.dev/releases")
            credentials {
                username = System.getenv("REPOSILITE_USERNAME")
                password = System.getenv("REPOSILITE_PASSWORD")
            }
        }
    }
}
