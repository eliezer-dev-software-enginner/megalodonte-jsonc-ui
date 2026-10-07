rootProject.name = "megalodonte-jsonc-ui"

// Use current ecosystem sources locally; consumers use the published artifact.
val libs = file("../../megalodonte-ecossystem/megalodonte-libs")
if (libs.isDirectory) {
    listOf("core", "base", "components").forEach { includeBuild(File(libs, "megalodonte-$it")) }
}
