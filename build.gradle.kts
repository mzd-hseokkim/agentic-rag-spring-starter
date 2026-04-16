import java.io.FileInputStream
import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.sonarqube)
}

// Load gitignored SonarQube credentials (sonar.login / sonar.token) into
// system properties so the sonarqube plugin picks them up.
file("sonar-tokens.properties").let { tokensFile ->
    if (tokensFile.exists()) {
        val props = Properties()
        FileInputStream(tokensFile).use { props.load(it) }
        props.forEach { key, value ->
            System.setProperty(key.toString(), value.toString())
        }
    }
}

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    // Runnable example apps configure themselves (Spring Boot plugin, no publishing).
    if (path.startsWith(":examples:")) {
        apply(plugin = "io.spring.dependency-management")
        apply(plugin = "java")
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.ADOPTIUM)
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(21)
        }
        return@subprojects
    }

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }
        withJavadocJar()
        withSourcesJar()
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${rootProject.libs.versions.springBoot.get()}")
            mavenBom("org.springframework.ai:spring-ai-bom:${rootProject.libs.versions.springAi.get()}")
            mavenBom("org.junit:junit-bom:${rootProject.libs.versions.junit.get()}")
            mavenBom("org.testcontainers:testcontainers-bom:${rootProject.libs.versions.testcontainers.get()}")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(false)
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }

    dependencies {
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testImplementation"(rootProject.libs.assertj.core)
        "testImplementation"(rootProject.libs.mockito.core)
        "testImplementation"(rootProject.libs.mockito.junit.jupiter)
        "testImplementation"(rootProject.libs.reactor.test)
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set("Agentic RAG Spring Starter — ${project.name}")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                }
            }
        }
        repositories {
            mavenLocal()
        }
    }
}

sonar {
    properties {
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.java.source", "21")
        property("sonar.coverage.jacoco.xmlReportPaths",
                subprojects.joinToString(",") { "${it.layout.buildDirectory.get().asFile}/reports/jacoco/test/jacocoTestReport.xml" })
        // Exclude generated / boilerplate from coverage metrics.
        property("sonar.coverage.exclusions",
                "**/package-info.java,**/*AutoConfiguration.java,**/*Properties.java")
    }
}
