dependencies {
    api(project(":agentic-rag-core"))
    api(project(":agentic-rag-ingestion"))
    api(project(":agentic-rag-retrieval"))
    api(project(":agentic-rag-agents"))
    api(project(":agentic-rag-factcheck"))

    api(rootProject.libs.spring.boot.autoconfigure)
    api(rootProject.libs.micrometer.core)
    annotationProcessor(rootProject.libs.spring.boot.configuration.processor)

    testImplementation(rootProject.libs.spring.boot.starter.test)
    testImplementation("org.springframework.ai:spring-ai-ollama")
    testImplementation("org.springframework.ai:spring-ai-vector-store")
}
