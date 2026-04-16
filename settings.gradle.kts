pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
        maven("https://repo.spring.io/snapshot")
    }
}

rootProject.name = "agentic-rag-spring-starter"

include(
    "agentic-rag-core",
    "agentic-rag-ingestion",
    "agentic-rag-retrieval",
    "agentic-rag-agents",
    "agentic-rag-factcheck",
    "agentic-rag-autoconfigure",
    "agentic-rag-spring-boot-starter",
)

// Runnable demo application — see examples/agentic-rag-demo/README.md.
include(":examples:agentic-rag-demo")
project(":examples:agentic-rag-demo").projectDir = file("examples/agentic-rag-demo")
