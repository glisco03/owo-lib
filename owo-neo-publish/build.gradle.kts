import org.gradle.jvm.tasks.Jar;

plugins {
    // Apply the plugin. You can find the latest version at https://projects.neoforged.net/neoforged/ModDevGradle
    id("net.neoforged.moddev") version "2.0.42-beta"
    id("maven-publish")
}

apply(plugin = "maven-publish")

version = "${rootProject.property("mod_version")}+${rootProject.property("minecraft_base_version")}"
group = rootProject.property("maven_group")!!

val ENV = System.getenv()

repositories {
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.shedaniel.me/")
    maven {
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven("https://maven.nucleoid.xyz/")
    maven("https://maven.wispforest.io/releases")
    maven("https://maven.su5ed.dev/releases")
    mavenLocal()
}

dependencies {
//    implementation(rootProject)
    api("io.wispforest:endec:0.1.8")
    api("io.wispforest.endec:netty:0.1.4")
    api("io.wispforest.endec:gson:0.1.5")
    api("io.wispforest.endec:jankson:0.1.5")

    api("blue.endless:jankson:${project.property("jankson_version")}")

    api("org.sinytra:forgified-fabric-loader:${project.property("loader_version")}:full")
    api("org.sinytra.forgified-fabric-api:fabric-api-base:0.4.42+d1308dedd1") { exclude(group = "fabric-api")  }
    api("org.sinytra.forgified-fabric-api:fabric-networking-api-v1:4.2.2+a92978fd19") { exclude(group = "fabric-api") }
    api("org.sinytra.forgified-fabric-api:fabric-screen-api-v1:2.0.24+79a4c2b0d1") { exclude(group = "fabric-api") }
}

neoForge {
    // We currently only support NeoForge versions later than 21.0.x
    // See https://projects.neoforged.net/neoforged/neoforge for the latest updates
    version = rootProject.property("neoforge_version").toString()

    // Validate AT files and raise errors when they have invalid targets
    // This option is false by default, but turning it on is recommended
    validateAccessTransformers = true

    accessTransformers {
        from(rootProject.file("src/main/resources/META-INF/owo.accesstransformer.cfg"))
        publish(rootProject.file("src/main/resources/META-INF/owo.accesstransformer.cfg"))
    }

    interfaceInjectionData {
        from(rootProject.file("src/main/resources/interfaces.json"))
        publish(rootProject.file("src/main/resources/interfaces.json"))
    }
}

val targetJavaVersion = 21
tasks.withType<JavaCompile>().configureEach {
    this.options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible()) {
        this.options.release = targetJavaVersion
    }
}

//tasks.getByName("build") {
//    onlyIf("test") {
//        false;
//    }
//}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
    base.archivesName.set(rootProject.property("archives_base_name").toString())
    withSourcesJar()

    val data: MutableMap<String, Set<PublishArtifact>> = mutableMapOf();

    for (cfg in rootProject.configurations) {
        if (cfg.name.equals("runtimeElements")) continue;

        with(cfg.artifacts) {
            val publishArtifact = this.filter {
                    publishArtifact -> return@filter publishArtifact.file.name.contains("owo-lib");
            }.toSet()

            data[cfg.name] = publishArtifact

            if (!publishArtifact.isEmpty()) this.removeAll(publishArtifact);
        }
    }

    for (cfg in project.configurations) {
        with(cfg.artifacts) {
            this.filter {
                publishArtifact -> return@filter publishArtifact.file.name.contains("owo-lib");
            }.toSet().let {
                publishArtifact -> this.removeAll(publishArtifact)
            }

            data[cfg.name]?.let { this.addAll(it) }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenCommon") {
            this.from(components["java"])

            artifactId = rootProject.property("archives_base_name").toString()
        }
    }
    repositories {
        maven {
            url = uri(ENV["MAVEN_URL"]!!)
            credentials {
                username = ENV["MAVEN_USER"]
                password = ENV["MAVEN_PASSWORD"]
            }
        }
    }
}
