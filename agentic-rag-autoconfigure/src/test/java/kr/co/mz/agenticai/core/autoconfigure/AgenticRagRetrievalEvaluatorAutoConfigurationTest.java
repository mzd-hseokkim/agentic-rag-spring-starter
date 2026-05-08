package kr.co.mz.agenticai.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator;
import kr.co.mz.agenticai.core.retrieval.evaluate.PassThroughRetrievalEvaluator;
import kr.co.mz.agenticai.core.retrieval.evaluate.ScoreThresholdEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T5: @ConditionalOnMissingBean — user-provided RetrievalEvaluator overrides default.
 */
class AgenticRagRetrievalEvaluatorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgenticRagCoreAutoConfiguration.class,
                    AgenticRagRetrievalAutoConfiguration.class));

    @Test
    void defaultEvaluatorIsPassThrough() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(RetrievalEvaluator.class);
            assertThat(ctx.getBean(RetrievalEvaluator.class))
                    .isInstanceOf(PassThroughRetrievalEvaluator.class);
        });
    }

    @Test
    void scoreThresholdEvaluatorWiredWhenStrategyAndEnabledSet() {
        runner.withPropertyValues(
                "agentic-rag.retrieval.evaluator.enabled=true",
                "agentic-rag.retrieval.evaluator.strategy=score-threshold",
                "agentic-rag.retrieval.evaluator.min-score=0.7"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(RetrievalEvaluator.class);
            assertThat(ctx.getBean(RetrievalEvaluator.class))
                    .isInstanceOf(ScoreThresholdEvaluator.class);
        });
    }

    @Test
    void userBeanOverridesDefault() {
        runner.withUserConfiguration(WithCustomEvaluator.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(RetrievalEvaluator.class);
            assertThat(ctx.getBean(RetrievalEvaluator.class))
                    .isInstanceOf(CustomEvaluator.class);
        });
    }

    @Configuration
    static class WithCustomEvaluator {
        @Bean
        RetrievalEvaluator customEvaluator() {
            return new CustomEvaluator();
        }
    }

    static class CustomEvaluator implements RetrievalEvaluator {
        @Override
        public Decision evaluate(kr.co.mz.agenticai.core.common.spi.RetrieverRouter.Query query,
                java.util.List<org.springframework.ai.document.Document> candidates) {
            return Decision.of(Action.ACCEPT, 1.0);
        }
    }
}
