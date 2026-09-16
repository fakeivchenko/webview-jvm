plugins {
    base
    id("io.freefair.lombok") version "9.5.0" apply false
}

group = "dev.ivchenko.webview"
version = providers.gradleProperty("projectVersion").getOrElse("0.0.0-dev")

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("io.freefair.lombok") {
        configure<io.freefair.gradle.plugins.lombok.LombokExtension> {
            version = "1.18.48"
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
        options.encoding = "UTF-8"
    }

    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {
                        name.set(project.name)
                        description.set(project.description)
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
    }
}
