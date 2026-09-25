package com.tutran.callassistant.domain.report;

/** Một dòng evidence trong report, đã phẳng hoá để render/serialize. */
public record EvidenceItem(String id, String source, String timestamp, String description) {
}
