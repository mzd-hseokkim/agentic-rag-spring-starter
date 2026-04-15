dependencies {
    api(rootProject.libs.slf4j.api)
    api(rootProject.libs.reactor.core)
    api(rootProject.libs.jackson.databind)
    api(rootProject.libs.spring.ai.client.chat)
    api(rootProject.libs.spring.ai.vector.store)
    implementation(rootProject.libs.micrometer.core)
}
