allprojects {
    repositories {
        google()
        mavenCentral()
        // The Atunwa Media SDK, and the AdsWizz SDK it uses. Public, no credentials.
        maven("https://raw.githubusercontent.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/maven") {
            content {
                includeGroup("com.atunwadigital")
                includeGroup("com.adswizz")
            }
        }
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
