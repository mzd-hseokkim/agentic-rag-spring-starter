package kr.co.mz.agenticai.core.common.guardrail;

import java.util.List;
import java.util.regex.Pattern;
import kr.co.mz.agenticai.core.common.spi.Guardrail;

/**
 * Heuristic prompt-injection detector. Blocks the request when the input
 * contains common override phrases ("이전 지시 무시", "ignore previous
 * instructions", system-prompt extraction attempts, ...). Production
 * deployments should layer an ML-based detector on top — this is a cheap
 * first line of defense, not an exhaustive filter.
 */
public final class PromptInjectionGuardrail implements Guardrail {

    // CANON_EQ + UNICODE_CASE handle NFC/NFD-decomposed Hangul and non-ASCII case folding.
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ;

    private static final List<Pattern> DEFAULT_PATTERNS = List.of(
            Pattern.compile("이전\\s*(지시|명령|규칙|프롬프트)[을를]?\\s*(무시|잊)", FLAGS),
            Pattern.compile("(시스템|system)\\s*(프롬프트|prompt)[을를]?\\s*(보여|출력|공개|reveal|show)", FLAGS),
            Pattern.compile("ignore\\s+(?:all|previous|prior)\\s+instructions?", FLAGS),
            Pattern.compile("disregard\\s+(?:the\\s+)?(?:above|previous)", FLAGS),
            Pattern.compile("you\\s+are\\s+now\\s+(?:a|an)\\s+", FLAGS));

    private final int order;
    private final List<Pattern> patterns;

    public PromptInjectionGuardrail() {
        this(0, DEFAULT_PATTERNS);
    }

    public PromptInjectionGuardrail(int order, List<Pattern> patterns) {
        this.order = order;
        this.patterns = List.copyOf(patterns);
    }

    /** Always operates on input — output filtering is the PII guardrail's job. */
    @Override
    public Stage stage() {
        return Stage.INPUT;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public Result apply(String text) {
        if (text == null) {
            return Result.pass(null);
        }
        for (Pattern p : patterns) {
            if (p.matcher(text).find()) {
                return Result.block("Prompt injection pattern detected: " + p.pattern());
            }
        }
        return Result.pass(text);
    }
}
