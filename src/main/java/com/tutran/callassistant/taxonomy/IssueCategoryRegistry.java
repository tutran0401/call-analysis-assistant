package com.tutran.callassistant.taxonomy;

import java.util.List;
import java.util.Map;

/**
 * Registry tĩnh chứa taxonomy trong PROJECT_SPEC.md mục 4.2. Các ngưỡng phát hiện ở đây
 * là baseline của Sprint 1, đã ghi rõ trong tài liệu (data mẫu không kèm ground truth để
 * hiệu chỉnh) - việc siết chặt ngưỡng bằng dữ liệu accuracy thật là công việc tường minh
 * của Sprint 3 (mục 7.1 T1 "Improve Accuracy & Consistency").
 */
public final class IssueCategoryRegistry {

    public static final double PACKET_LOSS_WARN_PERCENT = 5.0;
    public static final double JITTER_WARN_MS = 30.0;
    public static final double RTT_WARN_MS = 300.0;
    public static final double MOS_WARN = 3.5;
    public static final int SIGNALING_RETRANSMIT_WARN_COUNT = 2;

    private static final Map<IssueCategory, IssueCategoryDefinition> DEFINITIONS = Map.of(
            IssueCategory.NETWORK_PACKET_LOSS, new IssueCategoryDefinition(
                    IssueCategory.NETWORK_PACKET_LOSS,
                    "Call connects and stays connected, but audio/video quality degrades because packets are "
                            + "lost in transit - not a connection-setup failure.",
                    List.of("High packetLostPercent on audio and/or video", "Low MOS score"),
                    List.of("End-call log call summary / periodic stats (audio.packetLostPercent, audio.audioMos)"),
                    "audio.packetLostPercent > " + PACKET_LOSS_WARN_PERCENT + "% OR audio.audioMos < " + MOS_WARN
                            + " for either leg",
                    "High packet loss and high jitter/RTT often co-occur; without jitter/RTT in the log we "
                            + "cannot always tell which is the primary cause."
            ),
            IssueCategory.NETWORK_DELAY_JITTER, new IssueCategoryDefinition(
                    IssueCategory.NETWORK_DELAY_JITTER,
                    "Call connects and stays connected, but audio/video quality degrades because of network "
                            + "delay or jitter rather than outright packet loss.",
                    List.of("High audio.jitter", "High transport.currentRttMs"),
                    List.of("End-call log periodic stats (audio.jitter, transport.currentRttMs)"),
                    "audio.jitter > " + JITTER_WARN_MS + "ms OR transport.currentRttMs > " + RTT_WARN_MS + "ms",
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
                    "A WebRTC ICE_CONNECTION_STATE_CHANGE event's message mentions FAILED/DISCONNECTED, "
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
                    "Out of scope per PROJECT_SPEC.md §9 to diagnose TURN server congestion or capacity even once "
                            + "client-observed TURN errors are detected reliably."
            ),
            IssueCategory.SIGNALING_FAILURE, new IssueCategoryDefinition(
                    IssueCategory.SIGNALING_FAILURE,
                    "Call could not be established because of a problem at the signaling layer: timeouts, "
                            + "excessive retransmits, or the callee session not being found.",
                    List.of("Repeated INVITE/BYE retransmits", "\"No sessions found\" in end-call log",
                            "Non-zero callErrorCode with the call ending before CONFIRMED"),
                    List.of("Signaling log (retries, timeouts)", "End-call log state timeline / log_detail"),
                    "INVITE (or BYE) retransmit count > " + SIGNALING_RETRANSMIT_WARN_COUNT
                            + ", or the call terminates with a callErrorCode before reaching CONFIRMED",
                    "A non-zero callErrorCode can also reflect a legitimate user action (e.g. busy, declined) "
                            + "rather than a system failure; evidence must be read in context, not just presence "
                            + "of an error code."
            ),
            IssueCategory.UNKNOWN, new IssueCategoryDefinition(
                    IssueCategory.UNKNOWN,
                    "Not enough evidence was available to attribute the issue to a specific category.",
                    List.of(),
                    List.of(),
                    "No other category's detection condition was met, or required source files were missing",
                    "N/A"
            )
    );

    private IssueCategoryRegistry() {
    }

    public static IssueCategoryDefinition definitionOf(IssueCategory category) {
        return DEFINITIONS.get(category);
    }

    public static Map<IssueCategory, IssueCategoryDefinition> all() {
        return DEFINITIONS;
    }
}
