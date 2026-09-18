plugins {
    id("application")
    id("io.freefair.lombok")
    id("dev.ivchenko.webview")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

application {
    mainClass = "dev.ivchenko.webview.example.WebviewExampleApplication"
}

webview {
    icon = file("src/main/icons/app.png")
    windows {
        productName = "webview-jvm"
    }
    macos {
        bundleIdentifier = "dev.ivchenko.webview.example"
    }
}

dependencies {
    // webview-jvm
    implementation(project(":webview-jvm-core"))
    runtimeOnly(project(":webview-jvm-gtk"))
    runtimeOnly(project(":webview-jvm-windows"))
    runtimeOnly(project(":webview-jvm-macos"))
    runtimeOnly(project(":webview-jvm-chrome"))
    runtimeOnly("org.eclipse.parsson:parsson:1.1.9")

    // Logging: slf4j-simple has no XML configuration, so no JAXP ends up in the native image
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    // webview-jvm test fixtures
    testImplementation(testFixtures(project(":webview-jvm-core")))

    // JUnit
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("display", "network")
    }
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val displayTest = tasks.register<Test>("displayTest") {
    description = "Opens the example window and photographs it."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("display")
    }
    systemProperty("webview.requireDisplay", System.getProperty("webview.requireDisplay", "false"))
    systemProperty("webview.screenshots", System.getProperty("webview.screenshots", "false"))
    systemProperty("webview.screenshotsDir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(displayTest)
}
