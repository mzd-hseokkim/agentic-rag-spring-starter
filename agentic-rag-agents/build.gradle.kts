dependencies {
    api(project(":agentic-rag-core"))
    api(rootProject.libs.spring.ai.starter.mcp.client)
    implementation(rootProject.libs.micrometer.tracing)
}
