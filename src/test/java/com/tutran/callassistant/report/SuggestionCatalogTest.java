package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.verdict.IssueCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionCatalogTest {

    private final SuggestionCatalog catalog = new SuggestionCatalog();

    @Test
    void everyIssueCategoryHasAtLeastTwoStandardSuggestions() {
        for (IssueCategory category : IssueCategory.values()) {
            if (category != IssueCategory.NONE) {
                assertThat(catalog.suggestionsFor(category)).as(category.name()).hasSizeBetween(2, 4);
            }
        }
        assertThat(catalog.suggestionsFor(IssueCategory.NONE)).isEmpty();
    }

    @Test
    void missingFilesTurnIntoConcreteDataRequests() {
        List<String> out = catalog.forDataLimitations(List.of("Thiếu callee_endcall.log", "Thiếu caller_webrtc.log",
                "Không có dữ liệu signaling cho cuộc gọi này", "Tên file x gợi ý y"));

        assertThat(out).hasSize(3);
        assertThat(out.get(0)).startsWith("Bổ sung callee_endcall.log").contains("call summary");
        assertThat(out.get(1)).startsWith("Bổ sung caller_webrtc.log").contains("ICE");
        assertThat(out.get(2)).contains("Elasticsearch");
    }
}
