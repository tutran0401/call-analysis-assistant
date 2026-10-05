package com.tutran.callassistant.application.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleRequestParserTest {

    private final RuleRequestParser parser = new RuleRequestParser();

    @ParameterizedTest
    @ValueSource(strings = {
            "Phân tích cuộc gọi này giúp mình",
            "Cuộc gọi này có lỗi gì không?",
            "Kiểm tra giúp mình log cuộc gọi",
            "phan tich cuoc goi nay",
            "Cuộc gọi này toàn bộ ổn không?"})
    void plainAnalysisQuestionsAreAnalyzeCall(String question) {
        ParsedRequest parsed = parser.parse(question, true);

        assertThat(parsed.intent()).isEqualTo(Intent.ANALYZE_CALL);
        assertThat(parsed.focus()).isEmpty();
    }

    @Test
    void whyCalleeCannotHearIsAudioQualityFocus() {
        ParsedRequest parsed = parser.parse("Vì sao bên nhận không nghe được?", true);

        assertThat(parsed.intent()).isEqualTo(Intent.ANALYZE_WITH_FOCUS);
        assertThat(parsed.focus()).containsExactly(CallFocus.AUDIO_QUALITY);
    }

    @Test
    void crackleQuestionIsAudioQualityFocus() {
        assertThat(parser.parse("Vì sao bên nhận nghe bị rè?", true).focus())
                .contains(CallFocus.AUDIO_QUALITY);
    }

    @Test
    void setupAndDisconnectAndMediaFocusesAreDetected() {
        assertThat(parser.parse("Tại sao cuộc gọi không đổ chuông?", true).focus()).contains(CallFocus.SETUP);
        assertThat(parser.parse("Cuộc gọi bị rớt giữa chừng, ai cúp máy?", true).focus())
                .contains(CallFocus.DISCONNECT);
        assertThat(parser.parse("Kiểm tra lỗi ICE và TURN giúp mình", true).focus()).contains(CallFocus.MEDIA);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Viết giúp mình một email",
            "Hôm nay thời tiết thế nào?",
            "Dịch câu này sang tiếng Anh",
            "Kể cho mình một câu chuyện cười",
            "Bạn là ai?",
            "Viết email xin lỗi khách hàng",
            "Giá vàng hôm nay bao nhiêu",
            "Viết code Python sắp xếp mảng"})
    void outOfScopeQuestionsAreRefused(String question) {
        assertThat(parser.parse(question, false).intent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(parser.parse(question, true).intent()).isEqualTo(Intent.OUT_OF_SCOPE);
    }

    @Test
    void emptyQuestionWithAttachmentsIsAnalyzeCallButWithoutAnythingIsRefused() {
        assertThat(parser.parse("", true).intent()).isEqualTo(Intent.ANALYZE_CALL);
        assertThat(parser.parse("   ", true).intent()).isEqualTo(Intent.ANALYZE_CALL);
        assertThat(parser.parse(null, true).intent()).isEqualTo(Intent.ANALYZE_CALL);
        assertThat(parser.parse("", false).intent()).isEqualTo(Intent.OUT_OF_SCOPE);
    }

    @Test
    void callIdInTextIsExtractedAndUpperCased() {
        ParsedRequest parsed = parser.parse("phân tích call de7dd314-f432-45cb-bcb4-ae9103cc0919 giúp mình", false);

        assertThat(parsed.callIdInText()).isEqualTo("DE7DD314-F432-45CB-BCB4-AE9103CC0919");
        assertThat(parsed.intent()).isEqualTo(Intent.ANALYZE_CALL);
    }

    @Test
    void callIdAloneKeepsRequestInScope() {
        assertThat(parser.parse("DE7DD314-F432-45CB-BCB4-AE9103CC0919", false).intent())
                .isEqualTo(Intent.ANALYZE_CALL);
    }

    @Test
    void refusalTemplateIsFixed() {
        assertThat(ParsedRequest.REFUSAL_MESSAGE).startsWith("Mình chỉ hỗ trợ phân tích cuộc gọi");
    }
}
