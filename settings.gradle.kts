pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "Akis"
include(":app", ":core")
// Local instrumentation traffic source; never included in the distributed Akış APK.
include(":probe")
