dependencies {
    api(project(":agentic-rag-core"))
    api(rootProject.libs.micrometer.observation)
    implementation(rootProject.libs.micrometer.tracing)
    api(rootProject.libs.spring.ai.rag)
    api(rootProject.libs.spring.ai.advisors.vector.store)
    api(rootProject.libs.lucene.core)
    api(rootProject.libs.lucene.analysis.common)
    api(rootProject.libs.lucene.analysis.nori)
    api(rootProject.libs.lucene.queryparser)
}
