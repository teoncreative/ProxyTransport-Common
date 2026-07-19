plugins {
    `java-library`
    `maven-publish`
}

group = "org.nethergames.proxytransport"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenLocal()
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
    mavenCentral()
}

val nettyVersion = "4.2.7.Final"
val protocolVersion = "3.0.0.Beta12-SNAPSHOT"

// Everything is compileOnly: this library declares what it compiles against and bundles nothing. Each plugin
// decides what to shade into its own jar.
dependencies {
    compileOnly("io.netty:netty-handler:$nettyVersion")
    compileOnly("io.netty:netty-codec:$nettyVersion")
    compileOnly("io.netty:netty-transport:$nettyVersion")
    compileOnly("io.netty:netty-transport-classes-epoll:$nettyVersion")
    compileOnly("io.netty:netty-transport-classes-kqueue:$nettyVersion")
    compileOnly("org.cloudburstmc.protocol:bedrock-codec:$protocolVersion")
    compileOnly("org.cloudburstmc.protocol:bedrock-connection:$protocolVersion")
    compileOnly("com.github.luben:zstd-jni:1.5.5-4")
    compileOnly("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    compileOnly("io.netty:netty-codec-classes-quic:$nettyVersion") { isTransitive = false }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "proxytransport-common"
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "teoncreative"
            url = uri(
                if (version.toString().endsWith("SNAPSHOT")) {
                    "https://repo.teon.llc/repository/maven-snapshots/"
                } else {
                    "https://repo.teon.llc/repository/maven-releases/"
                }
            )
            credentials {
                username = System.getenv("REPO_USERNAME") ?: providers.gradleProperty("teonUsername").orNull
                password = System.getenv("REPO_PASSWORD") ?: providers.gradleProperty("teonPassword").orNull
            }
        }
    }
}