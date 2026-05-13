plugins {
    id("org.springframework.boot") version "3.4.1"
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    version.set("20.18.0")
    download.set(true)
    nodeProjectDir.set(file("src/main/frontend"))
}

val frontendBuild = tasks.register<com.github.gradle.node.npm.task.NpmTask>("frontendBuild") {
    dependsOn(tasks.named("npmInstall"))
    workingDir.set(file("src/main/frontend"))
    args.set(listOf("run", "build"))
    inputs.dir("src/main/frontend/src")
    inputs.file("src/main/frontend/index.html")
    inputs.file("src/main/frontend/package.json")
    inputs.file("src/main/frontend/vite.config.ts")
    outputs.dir("src/main/resources/static")
}

tasks.named("processResources") {
    dependsOn(frontendBuild)
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("frontendDev") {
    group = "application"
    description = "Run Vite dev server (http://localhost:5173, proxies API to 8080)."
    dependsOn(tasks.named("npmInstall"))
    workingDir.set(file("src/main/frontend"))
    args.set(listOf("run", "dev"))
}

tasks.named<Delete>("clean") {
    delete("src/main/resources/static")
}

repositories {
    mavenCentral()
    maven("https://repo.spring.io/milestone")
    maven("https://repo.spring.io/snapshot")
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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
