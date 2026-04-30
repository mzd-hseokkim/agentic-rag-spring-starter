plugins {
    id("org.springframework.boot") version "3.4.1"
}

the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${rootProject.libs.versions.springBoot.get()}")
        mavenBom("org.springframework.ai:spring-ai-bom:${rootProject.libs.versions.springAi.get()}")
    }
}

dependencies {
    implementation(project(":agentic-rag-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-vector-store")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Forward -Pprofile=xxx as --spring.profiles.active=xxx
    if (project.hasProperty("profile")) {
        args("--spring.profiles.active=${project.property("profile")}")
    }
}
