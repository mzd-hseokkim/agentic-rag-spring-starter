dependencies {
    api(project(":agentic-rag-core"))
    api(project(":agentic-rag-ingestion"))
    api(project(":agentic-rag-retrieval"))
    api(project(":agentic-rag-agents"))
    api(project(":agentic-rag-factcheck"))

    api(rootProject.libs.spring.boot.autoconfigure)
    api(rootProject.libs.micrometer.core)
    api(rootProject.libs.micrometer.observation)
    compileOnly(rootProject.libs.micrometer.tracing.bridge.otel)
    annotationProcessor(rootProject.libs.spring.boot.configuration.processor)

    compileOnly(rootProject.libs.spring.boot.starter.data.redis)

    testImplementation(rootProject.libs.spring.boot.starter.test)
    testImplementation(rootProject.libs.spring.boot.starter.data.redis)
    testImplementation(platform(rootProject.libs.testcontainers.bom))
    testImplementation(rootProject.libs.testcontainers.junit)
    testImplementation("org.springframework.ai:spring-ai-ollama")
    testImplementation("org.springframework.ai:spring-ai-vector-store")
}
