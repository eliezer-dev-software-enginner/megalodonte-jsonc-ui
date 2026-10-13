rootProject.name = "megalodonte-jsonc-ui"

// Use current ecosystem sources locally; consumers use the published artifact.
val libs = file("../../libraries")
if (providers.gradleProperty("useLocalSources").orNull != "false" && libs.isDirectory) {
    listOf("core", "base", "components", "theme").forEach { includeBuild(File(libs, "megalodonte-$it")) }
}
