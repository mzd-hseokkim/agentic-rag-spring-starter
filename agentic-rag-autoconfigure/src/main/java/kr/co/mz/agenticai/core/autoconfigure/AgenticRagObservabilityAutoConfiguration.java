package kr.co.mz.agenticai.core.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import kr.co.mz.agenticai.core.autoconfigure.observability.MetricsRagEventListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// afterName (String) is used because spring-boot-actuator-autoconfigure is
// an optional classpath dependency — we still want this config to run after
// Actuator's MeterRegistry is registered when it is present.
@AutoConfiguration(
        after = AgenticRagCoreAutoConfiguration.class,
        afterName = {
                "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
                "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
                "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration",
                "org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration"
        })
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "agentic-rag.observability", name = "enabled", matchIfMissing = true)
public class AgenticRagObservabilityAutoConfiguration {

    /**
     * Relies on constructor injection to surface a clear error if no
     * {@link MeterRegistry} bean exists (Actuator must be on the classpath
     * for one to be auto-registered).
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsRagEventListener metricsRagEventListener(MeterRegistry registry) {
        return new MetricsRagEventListener(registry);
    }

    /**
     * Activated only when the Micrometer Tracing bridge (OTel or Brave) is on
     * the classpath. Registers a no-op {@link ObservationRegistry} fallback so
     * downstream beans can always inject it without null-checking.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class TracingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ObservationRegistry ragObservationRegistry() {
            return ObservationRegistry.create();
        }
    }
}
