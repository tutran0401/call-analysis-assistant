package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the TSV "End Call log" format documented in {@code sample.md}: 9 header rows
 * ({@code #H1}..{@code #H9}), each declaring the column schema for a record "type" that
 * data rows reference by a leading numeric tag. Columns 1-2 of every data row are always
 * the type tag and an epoch-millis timestamp; column 3 is a free-text category label
 * (e.g. "log_detail", "send_cmd"); columns 4+ are the schema-specific fields named by the
 * matching header (whose own first 3 tokens - "#HN", "#ts", "#tag" - are markers, not data
 * columns).
 *
 * <p><b>Important:</b> the {@code N} in {@code #HN} is NOT a stable, semantic schema id -
 * comparing real sample files shows the same logical schema (e.g. "call summary", or the
 * ~150-field periodic quality stats record) shows up under a different {@code #HN} number
 * in different files. It appears to be assigned by registration order within that upload,
 * not a fixed convention. This parser therefore classifies each header's *meaning* by the
 * distinctive field names it declares (mirroring the file-type detection principle used
 * elsewhere: identify by content, never by a name/number that can vary) and only uses the
 * numeric tag to look up the right column layout within one file.
 */
public final class EndCallLogParser {

    public ParseResult parse(Path file, String callId) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ParseResult(List.of(), List.of("Cannot read file " + file + ": " + e.getMessage()));
        }

        Map<Integer, List<String>> headerFieldsBySchema = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        List<CanonicalEvent> events = new ArrayList<>();
        List<String[]> dataRows = new ArrayList<>();
        List<Integer> dataRowLineNumbers = new ArrayList<>();

        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            if (line.isBlank()) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols[0].startsWith("#H")) {
                parseHeaderRow(cols, headerFieldsBySchema, warnings, lineNo);
                continue;
            }
            dataRows.add(cols);
            dataRowLineNumbers.add(lineNo);
        }

        Map<Integer, String> schemaTypeById = new LinkedHashMap<>();
        headerFieldsBySchema.forEach((id, fields) -> schemaTypeById.put(id, EndCallSchemaClassifier.classify(fields)));

        Leg leg = inferLeg(dataRows, headerFieldsBySchema, schemaTypeById, file);

        for (int i = 0; i < dataRows.size(); i++) {
            String[] cols = dataRows.get(i);
            int rowLineNo = dataRowLineNumbers.get(i);
            try {
                CanonicalEvent event = toEvent(cols, headerFieldsBySchema, schemaTypeById, callId, leg, file, rowLineNo);
                if (event != null) {
                    events.add(event);
                }
            } catch (RuntimeException e) {
                warnings.add("Skipped malformed row at " + file + ":" + rowLineNo + " - " + e.getMessage());
            }
        }

        return new ParseResult(events, warnings);
    }

    private void parseHeaderRow(String[] cols, Map<Integer, List<String>> headerFieldsBySchema,
                                 List<String> warnings, int lineNo) {
        try {
            int schemaId = Integer.parseInt(cols[0].substring(2));
            List<String> fieldNames = cols.length > 3
                    ? List.of(cols).subList(3, cols.length)
                    : List.of();
            headerFieldsBySchema.put(schemaId, fieldNames);
        } catch (NumberFormatException e) {
            warnings.add("Unrecognized header row at line " + lineNo + ": " + String.join("\t", cols));
        }
    }

    private CanonicalEvent toEvent(String[] cols, Map<Integer, List<String>> headerFieldsBySchema,
                                    Map<Integer, String> schemaTypeById, String callId, Leg leg, Path file, int lineNo) {
        if (cols.length < 2) {
            throw new IllegalArgumentException("row has fewer than 2 columns");
        }
        int schemaId = Integer.parseInt(cols[0].trim());
        long epochMillis = Long.parseLong(cols[1].trim());
        String tag = cols.length > 2 ? cols[2] : "";

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("tag", tag);
        List<String> fieldNames = headerFieldsBySchema.get(schemaId);
        if (fieldNames != null) {
            for (int i = 0; i < fieldNames.size(); i++) {
                int colIndex = 3 + i;
                if (colIndex < cols.length && !cols[colIndex].isEmpty()) {
                    attributes.put(fieldNames.get(i), cols[colIndex]);
                }
            }
        } else {
            for (int i = 3; i < cols.length; i++) {
                attributes.put("field_" + (i - 3), cols[i]);
            }
        }

        String eventType = schemaTypeById.getOrDefault(schemaId, "UNKNOWN_SCHEMA_" + schemaId);
        String rawLine = String.join("\t", cols);

        return new CanonicalEvent(
                callId,
                leg,
                EventSource.END_CALL,
                file.toString(),
                Instant.ofEpochMilli(epochMillis),
                cols[1],
                TimestampConfidence.EXACT,
                lineNo,
                eventType,
                attributes,
                rawLine
        );
    }

    /**
     * The CALL_SUMMARY row carries an explicit {@code role} field ("caller"/"callee") -
     * that is the authoritative signal. Only when no such row is present do we fall back
     * to the filename, since the file is otherwise entirely about one leg.
     */
    private Leg inferLeg(List<String[]> dataRows, Map<Integer, List<String>> headerFieldsBySchema,
                          Map<Integer, String> schemaTypeById, Path file) {
        for (Map.Entry<Integer, String> entry : schemaTypeById.entrySet()) {
            if (!"CALL_SUMMARY".equals(entry.getValue())) {
                continue;
            }
            int schemaId = entry.getKey();
            int roleIndex = headerFieldsBySchema.get(schemaId).indexOf("role");
            if (roleIndex < 0) {
                continue;
            }
            for (String[] cols : dataRows) {
                if (cols.length > 0 && String.valueOf(schemaId).equals(cols[0].trim())) {
                    int colIndex = 3 + roleIndex;
                    if (colIndex < cols.length) {
                        Leg leg = legFromText(cols[colIndex]);
                        if (leg != Leg.UNKNOWN) {
                            return leg;
                        }
                    }
                }
            }
        }
        return legFromText(file.getFileName().toString());
    }

    private Leg legFromText(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("callee")) {
            return Leg.CALLEE;
        }
        if (lower.contains("caller")) {
            return Leg.CALLER;
        }
        return Leg.UNKNOWN;
    }
}
