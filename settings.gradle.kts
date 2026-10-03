pluginManagement {
    buildscript {
        repositories { google(); mavenCentral() }
        dependencies {
            // Kotlin 2.4 metadata requires R8 >= 9.1.29, including release shrinking.
            classpath("com.android.tools:r8:9.4.14")
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "aircall-ai"
include(":app")
