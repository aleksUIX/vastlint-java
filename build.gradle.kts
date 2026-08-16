import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    `maven-publish`
    id("com.google.protobuf") version "0.9.5"
}

group = "io.openadtech"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

val grpcVersion = "1.69.1"
val protobufVersion = "3.25.5"

dependencies {
    api("io.grpc:grpc-protobuf:$grpcVersion")
    api("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("io.grpc:grpc-inprocess:$grpcVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("vastlint")
                description.set("gRPC client for VASTlint. Same IAB VAST catalog as the CLI, Go, and Erlang bindings.")
                url.set("https://github.com/aleksUIX/vastlint-java")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("aleksUIX")
                        name.set("Aleksander Sekowski")
                    }
                }
                scm {
                    url.set("https://github.com/aleksUIX/vastlint-java")
                    connection.set("scm:git:https://github.com/aleksUIX/vastlint-java.git")
                    developerConnection.set("scm:git:ssh://git@github.com/aleksUIX/vastlint-java.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aleksUIX/vastlint-java")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
