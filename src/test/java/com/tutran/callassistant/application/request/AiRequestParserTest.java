package com.tutran.callassistant.application.request;

import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.ai.TimeLimitedAiCaller;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.testsupport.ChatServices;
import com.tutran.callassistant.sanitize.InputSanitizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class AiRequestParserTest {

    private static final String UUID = "DE7DD314-F432-45CB-BCB4-AE9103CC0919";

    /** AI giả trả một chuỗi JSON cho trước (hoặc ném lỗi) và ghi lại prompt nhận được. */
    private static final class ScriptedAi implements AiProvider {
        final List<AiPrompt> prompts = new ArrayList<>();
        final Function<AiPrompt, String> reply;

        ScriptedAi(Function<AiPrompt, String> reply) {
            this.reply = reply;
        }

        @Override
        public String modelName() {
            return "scripted";
        }

        @Override
        public AiResult analyze(AiPrompt prompt) {
            prompts.add(prompt);
            return new AiResult(reply.apply(prompt), "scripted", 1, 0, 0);
        }
    }

    private AiRequestParser parser(ScriptedAi ai) {
        return new AiRequestParser(new RuleRequestParser(), ai, new InputSanitizer(ChatServices.ENGINE),
                new TimeLimitedAiCaller(2000));
    }

    @Test
    void inScopeQuestionsKeepTheRuleResultAndNeverCallTheAi() {
        ScriptedAi ai = new ScriptedAi(p -> "{\"intent\":\"OUT_OF_SCOPE\",\"focus\":[]}");

        ParsedRequest parsed = parser(ai).parse("Vì sao bên nhận không nghe được?", true);

        assertThat(parsed.intent()).isEqualTo(Intent.ANALYZE_WITH_FOCUS);
        assertThat(parsed.focus()).containsExactly(CallFocus.AUDIO_QUALITY);
        assertThat(ai.prompts).isEmpty();
    }

    @Test
    void aiRescuesAnUnusualPhrasingTheKeywordsMiss() {
        ScriptedAi ai = new ScriptedAi(p -> "{\"intent\":\"ANALYZE_WITH_FOCUS\",\"focus\":[\"DISCONNECT\"]}");

        // Không có từ khoá nào trong danh sách của rule.
        ParsedRequest parsed = parser(ai).parse("Anh chị bên kia đang nói dở thì mất hút, sao vậy?", true);

        assertThat(parsed.intent()).isEqualTo(Intent.ANALYZE_WITH_FOCUS);
        assertThat(parsed.focus()).containsExactly(CallFocus.DISCONNECT);
        assertThat(ai.prompts).hasSize(1);
    }

    @Test
    void aiConfirmingOutOfScopeLeavesTheRefusal() {
        ScriptedAi ai = new ScriptedAi(p -> "{\"intent\":\"OUT_OF_SCOPE\",\"focus\":[]}");

        assertThat(parser(ai).parse("Viết giúp mình một email", false).isOutOfScope()).isTrue();
    }

    @Test
    void invalidAiAnswersFallBackToTheRuleResult() {
        for (String bad : List.of("không phải JSON", "{}", "{\"intent\":\"CHAT\",\"focus\":[]}",
                "{\"intent\":\"ANALYZE_WITH_FOCUS\",\"focus\":[\"CPU\"]}")) {
            ScriptedAi ai = new ScriptedAi(p -> bad);

            assertThat(parser(ai).parse("Kể chuyện cười đi", false).isOutOfScope()).as(bad).isTrue();
        }
    }

    @Test
    void aiFailuresFallBackToTheRuleResult() {
        AiProvider failing = new AiProvider() {
            @Override
            public String modelName() {
                return "failing";
            }

            @Override
            public AiResult analyze(AiPrompt prompt) {
                throw new AiProviderException(AiProviderException.Kind.TIMEOUT, "slow");
            }
        };
        AiRequestParser parser = new AiRequestParser(new RuleRequestParser(), failing,
                new InputSanitizer(ChatServices.ENGINE), new TimeLimitedAiCaller(2000));

        assertThat(parser.parse("Chuyện gì xảy ra thế nhỉ", false).isOutOfScope()).isTrue();
    }

    @Test
    void focusWithoutIntentFocusIsNormalised() {
        ScriptedAi noFocus = new ScriptedAi(p -> "{\"intent\":\"ANALYZE_WITH_FOCUS\",\"focus\":[]}");
        ScriptedAi extraFocus = new ScriptedAi(p -> "{\"intent\":\"ANALYZE_CALL\",\"focus\":[\"MEDIA\"]}");

        ParsedRequest a = parser(noFocus).parse("Hmm cái này thế nào", true);
        ParsedRequest b = parser(extraFocus).parse("Hmm cái này thế nào", true);

        assertThat(a.intent()).isEqualTo(Intent.ANALYZE_CALL);
        assertThat(b.intent()).isEqualTo(Intent.ANALYZE_CALL);
        assertThat(b.focus()).isEmpty();
    }

    @Test
    void callIdAlwaysComesFromCodeAndSecretsInTheQuestionNeverReachTheAi() {
        ScriptedAi ai = new ScriptedAi(p -> "{\"intent\":\"ANALYZE_CALL\",\"focus\":[],\"callId\":\"EVIL-ID\"}");

        // Chuỗi chỉ trả về lý do để rule coi là ngoài phạm vi: không từ khoá nào khớp.
        ParsedRequest parsed = parser(ai).parse("Hmm xem giúp 0987654321 với Authorization: Bearer abcdefgh12345678", false);

        assertThat(parsed.intent()).isEqualTo(Intent.ANALYZE_CALL);
        assertThat(ai.prompts).hasSize(1);
        assertThat(ai.prompts.get(0).userContent()).doesNotContain("0987654321").doesNotContain("abcdefgh12345678");
        assertThat(parsed.callIdInText()).isNull();
    }

    @Test
    void emptyQuestionsNeverCallTheAi() {
        ScriptedAi ai = new ScriptedAi(p -> "{\"intent\":\"ANALYZE_CALL\",\"focus\":[]}");

        assertThat(parser(ai).parse("  ", false).isOutOfScope()).isTrue();
        assertThat(parser(ai).parse(null, false).isOutOfScope()).isTrue();
        assertThat(ai.prompts).isEmpty();
    }

    @Test
    void withAiProviderRebindsTheParserToTheNewProvider() {
        ScriptedAi first = new ScriptedAi(p -> "{\"intent\":\"OUT_OF_SCOPE\",\"focus\":[]}");
        ScriptedAi second = new ScriptedAi(p -> "{\"intent\":\"ANALYZE_CALL\",\"focus\":[]}");

        RequestParser rebound = parser(first).withAiProvider(second);

        assertThat(rebound.parse("Hmm cái này thế nào", true).intent()).isEqualTo(Intent.ANALYZE_CALL);
        assertThat(first.prompts).isEmpty();
        assertThat(second.prompts).hasSize(1);
        assertThat(new RuleRequestParser().withAiProvider(second)).isInstanceOf(RuleRequestParser.class);
        assertThat(UUID).isNotBlank();
    }
}
