package kr.co.mz.agenticai.demo;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Sample {@code @Tool} methods exposed to the LLM through {@link DemoConfig}'s
 * {@link org.springframework.ai.tool.method.MethodToolCallbackProvider}. The
 * starter's {@code CatalogToolProvider} aggregates every
 * {@link org.springframework.ai.tool.ToolCallbackProvider} bean it can find,
 * so registering the bean is all the wiring an end user needs.
 */
@Component
public class DemoTools {

    private static final Logger log = LoggerFactory.getLogger(DemoTools.class);

    @Tool(description = "현재 한국 시각(KST)을 'yyyy-MM-dd HH:mm:ss' 형식으로 반환한다. "
            + "현재 시간이 필요한 질문에 사용한다.")
    public String currentTimeKst() {
        String now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("[tool] currentTimeKst() = {}", now);
        return now;
    }
}
