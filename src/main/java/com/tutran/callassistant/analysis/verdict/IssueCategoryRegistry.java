package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.IssueCategoryDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Taxonomy verdict &amp; issue category (T5, mục 4.2): mỗi category nghĩa là gì, biểu hiện ra
 * sao, evidence nào chứng minh được, điều kiện phát hiện xác định nào rule engine đang kiểm
 * tra, và nó dễ bị nhầm với cái gì.
 *
 * <p>Điều kiện phát hiện được dựng từ chính {@link QualityThresholds} mà rule engine dùng, nên
 * tài liệu taxonomy không thể nói một ngưỡng trong khi code kiểm tra một ngưỡng khác. Được
 * mirror ở dạng đọc-hiểu-được trong {@code docs/verdict-issue-taxonomy.md}.
 */
@Component
public class IssueCategoryRegistry {

    private final Map<IssueCategory, IssueCategoryDefinition> definitions;

    public IssueCategoryRegistry(QualityThresholds thresholds) {
        this.definitions = buildDefinitions(thresholds);
    }

    /** Registry với ngưỡng baseline, dùng cho test và chỗ nào không có Spring container. */
    public static IssueCategoryRegistry withDefaults() {
        return new IssueCategoryRegistry(QualityThresholds.defaults());
    }

    public IssueCategoryDefinition definitionOf(IssueCategory category) {
        return definitions.get(category);
    }

    public Map<IssueCategory, IssueCategoryDefinition> all() {
        return definitions;
    }

    private static Map<IssueCategory, IssueCategoryDefinition> buildDefinitions(QualityThresholds thresholds) {
        return Map.of(
                IssueCategory.NETWORK_PACKET_LOSS, new IssueCategoryDefinition(
                        IssueCategory.NETWORK_PACKET_LOSS,
                        "Call connects and stays connected, but audio/video quality degrades because packets are "
                                + "lost in transit - not a connection-setup failure.",
                        List.of("High packetLostPercent on audio and/or video", "Low MOS score"),
                        List.of("End-call log call summary / periodic stats (audio.packetLostPercent, audio.audioMos)"),
                        "audio.packetLostPercent > " + thresholds.packetLossPercent() + "% OR audio.audioMos < "
                                + thresholds.mos() + " for either leg",
                        "High packet loss and high jitter/RTT often co-occur; without jitter/RTT in the log we "
                                + "cannot always tell which is the primary cause."
                ),
                IssueCategory.NETWORK_DELAY_JITTER, new IssueCategoryDefinition(
                        IssueCategory.NETWORK_DELAY_JITTER,
                        "Call connects and stays connected, but audio/video quality degrades because of network "
                                + "delay or jitter rather than outright packet loss.",
                        List.of("High audio.jitter", "High transport.currentRttMs"),
                        List.of("End-call log periodic stats (audio.jitter, transport.currentRttMs)"),
                        "audio.jitter > " + thresholds.jitterMs() + "ms OR transport.currentRttMs > "
                                + thresholds.rttMs() + "ms",
                        "RTT/jitter fields are not always present in the uploaded log (device/version dependent); "
                                + "when absent this category cannot be distinguished from NETWORK_PACKET_LOSS and the "
                                + "report must say so explicitly rather than guessing."
                ),
                IssueCategory.ICE_FAILURE, new IssueCategoryDefinition(
                        IssueCategory.ICE_FAILURE,
                        "Media connection could not be established because ICE connectivity checks failed "
                                + "(gathering/checking/connecting never reached a connected state).",
                        List.of("WebRTC log ICE connection state moves to FAILED/DISCONNECTED",
                                "End-call state timeline never reaches CONFIRMED after INVITE/OK"),
                        List.of("WebRTC log (ICE_CONNECTION_STATE_CHANGE, ICE_ERROR events)",
                                "End-call log state timeline"),
                        "A WebRTC ICE_CONNECTION_STATE_CHANGE message mentions FAILED/DISCONNECTED, "
                                + "or the end-call state timeline never reaches CONFIRMED",
                        "Hard to separate from TURN_FAILURE without per-candidate-pair detail; TURN_FAILURE is "
                                + "preferred when a TURN-specific error is present in the same window."
                ),
                IssueCategory.TURN_FAILURE, new IssueCategoryDefinition(
                        IssueCategory.TURN_FAILURE,
                        "Media connection could not be established (or degraded) because of a TURN "
                                + "allocation/relay error on the client side.",
                        List.of("WebRTC log reports a TURN allocation/relay/socket error"),
                        List.of("WebRTC log (TURN_ERROR events)"),
                        "Not auto-detected by the Sprint 1 rule engine: free-text keyword matching for TURN errors "
                                + "was tried and dropped after it flagged routine, self-recovering per-candidate TURN "
                                + "protocol responses (e.g. the standard long-term-credential challenge) as failures "
                                + "on clean SUCCESS calls. Currently folds into ICE_FAILURE/SIGNALING_FAILURE instead; "
                                + "a reliable detector is Known Limitations work for a later sprint.",
                        "Out of scope per PROJECT_SPEC.md section 9 to diagnose TURN server congestion or capacity "
                                + "even once client-observed TURN errors are detected reliably."
                ),
                IssueCategory.SIGNALING_FAILURE, new IssueCategoryDefinition(
                        IssueCategory.SIGNALING_FAILURE,
                        "Call could not be established because of a problem at the signaling layer: timeouts, "
                                + "excessive retransmits, or the callee session not being found.",
                        List.of("Repeated INVITE/BYE retransmits", "\"No sessions found\" in end-call log",
                                "Non-zero callErrorCode with the call ending before CONFIRMED"),
                        List.of("Signaling log (retries, timeouts)", "End-call log state timeline / log_detail"),
                        "INVITE (or BYE) retransmit count > " + thresholds.signalingRetransmitCount()
                                + ", or the call terminates with a callErrorCode before reaching CONFIRMED",
                        "A non-zero callErrorCode can also reflect a legitimate user action (e.g. busy, declined) "
                                + "rather than a system failure; evidence must be read in context, not just presence "
                                + "of an error code."
                ),
                IssueCategory.NONE, new IssueCategoryDefinition(
                        IssueCategory.NONE,
                        "The call was checked against every detection condition below and none of them "
                                + "matched: the call connected, ended normally, and no quality metric "
                                + "breached its threshold.",
                        List.of(),
                        List.of("Signaling reached OK_ACK_OK then BYE",
                                "All available quality metrics within thresholds"),
                        "No other category matched AND the verdict is SUCCESS without a quality flag",
                        "Absence of evidence is not evidence of absence: a leg with no end-call log "
                                + "contributes no quality metric, so NONE means nothing was found in the "
                                + "data that was actually available - see the data limitations section."
                ),
                IssueCategory.UNKNOWN, new IssueCategoryDefinition(
                        IssueCategory.UNKNOWN,
                        "Not enough evidence was available to attribute the issue to a specific category.",
                        List.of(),
                        List.of(),
                        "No other detection condition was met, or required source files were missing",
                        "N/A"
                )
        );
    }
}
