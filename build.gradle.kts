plugins {
    id("java")
    id("jacoco")
    id("checkstyle")
    id("pmd")
    id("org.sonarqube") version "7.0.1.6134"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

repositories {
    mavenLocal()
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugin("com.intellij.java")
        pluginModule(implementation("io.github.sibmaks.jjtemplate:jjtemplate:0.8.1"))
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }
    }
}

val versionFromProperty = "${project.property("version")}"
val versionFromEnv: String? = System.getenv("VERSION")

version = versionFromEnv ?: versionFromProperty
group = "${project.property("group")}"

val targetJavaVersion = (project.property("jdk_version") as String).toInt()

checkstyle {
    configDirectory.set(layout.projectDirectory.dir("config/checkstyle"))
}

pmd {
    isConsoleOutput = true
    ruleSetFiles = files("config/pmd/ruleset.xml")
    ruleSets = emptyList()
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(targetJavaVersion)
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    withType<Checkstyle>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    withType<Pmd>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
