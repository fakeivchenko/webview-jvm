plugins {
    id("java-library")
    id("java-test-fixtures")
    id("maven-publish")
    id("io.freefair.lombok")
}

description = "The webview-jvm API, page bridge, backend discovery and FFM helpers."

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
    // Test fixtures (the test source set inherits them)
    testFixturesApi(platform("org.junit:junit-bom:6.0.0"))
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesApi("com.fasterxml.jackson.core:jackson-databind:2.22.2")

    // JUnit
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
