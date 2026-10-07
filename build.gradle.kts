plugins {
    `java-library`
    `maven-publish`
    id("org.openjfx.javafxplugin") version "0.1.0"
}
group = "megalodonte"
version = "0.1.0"
repositories { mavenLocal(); mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }; withSourcesJar() }
javafx { version = "25.0.1"; modules("javafx.controls", "javafx.graphics") }
dependencies {
    api("megalodonte:megalodonte-base:1.0.0-beta")
    api("megalodonte:megalodonte-components:1.0.0-beta")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("megalodonte:megalodonte-theme:1.0.0-beta")
}
tasks.test { useJUnitPlatform(); jvmArgs("--enable-native-access=ALL-UNNAMED") }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.processResources { from("schema") { into("megalodonte/jsonc/schema") } }
publishing { publications { create<MavenPublication>("mavenJava") { from(components["java"]) } } }
