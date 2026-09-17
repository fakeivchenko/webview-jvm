plugins {
    id("java-library")
    id("maven-publish")
    id("io.freefair.lombok")
}

description = "EXPERIMENTAL: fallback backend for webview-jvm driving an installed Chrome, Chromium or Edge over the DevTools protocol."

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withJavadocJar()
    withSourcesJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

dependencies {
    // webview-jvm
    api(project(":webview-jvm-core"))

    // Jakarta JSON Processing: the API only, the application picks an implementation (Parsson, Johnzon, ...)
    api("jakarta.json:jakarta.json-api:2.1.3")
    testRuntimeOnly("org.eclipse.parsson:parsson:1.1.9")

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
    systemProperty("webview.backend", "chromium")
    // Proxy auto-detection on a fresh profile can stall the first request for half a minute on a CI runner.
    systemProperty("webview.chrome.args", "--no-proxy-server")
    configureWebviewTest()
}

val displayTest = tasks.register<Test>("displayTest") {
    description = "Runs the tests that open a real window."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("display")
        excludeTags("network")
    }
    systemProperty("webview.backend", "chromium")
    // Proxy auto-detection on a fresh profile can stall the first request for half a minute on a CI runner.
    systemProperty("webview.chrome.args", "--no-proxy-server")
    systemProperty("webview.requireDisplay", System.getProperty("webview.requireDisplay", "false"))
    systemProperty("webview.screenshots", System.getProperty("webview.screenshots", "false"))
    systemProperty("webview.screenshotsDir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    // A window test proves the machine it runs on, so a cached result from another machine proves nothing.
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
    configureWebviewTest()
    shouldRunAfter(tasks.test)
}

val networkTest = tasks.register<Test>("networkTest") {
    description = "Runs the tests that load pages from the public internet."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("network")
    }
    systemProperty("webview.backend", "chromium")
    // Proxy auto-detection on a fresh profile can stall the first request for half a minute on a CI runner.
    systemProperty("webview.chrome.args", "--no-proxy-server")
    systemProperty("webview.requireDisplay", System.getProperty("webview.requireDisplay", "false"))
    systemProperty("webview.screenshots", System.getProperty("webview.screenshots", "false"))
    systemProperty("webview.screenshotsDir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    // A window test proves the machine it runs on, so a cached result from another machine proves nothing.
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
    configureWebviewTest()
    shouldRunAfter(displayTest)
}

tasks.check {
    dependsOn(displayTest)
}

fun Test.configureWebviewTest() {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
