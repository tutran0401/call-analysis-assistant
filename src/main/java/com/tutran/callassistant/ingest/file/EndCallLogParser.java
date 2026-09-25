package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parse định dạng TSV "End Call log" đã mô tả trong {@code sample-data/sample.md}: 9 dòng header
 * ({@code #H1}..{@code #H9}), mỗi dòng khai báo schema cột cho một "loại" bản ghi mà các dòng dữ
 * liệu tham chiếu tới bằng một tag số ở đầu dòng. Cột 1-2 của mọi dòng dữ liệu luôn là tag loại
 * và timestamp dạng epoch-millis; cột 3 là nhãn category dạng text tự do (ví dụ "log_detail",
 * "send_cmd"); từ cột 4 trở đi là các field riêng của schema đó, đặt tên theo header tương ứng
 * (3 token đầu của header - {@code #HN}, {@code #ts}, {@code #tag} - chỉ là nhãn, không phải cột
 * dữ liệu).
 *
 * <p><b>Lưu ý quan trọng:</b> số {@code N} trong {@code #HN} KHÔNG phải là id schema cố định, có
 * ý nghĩa ổn định - so sánh các file mẫu thật cho thấy cùng một schema logic (ví dụ "call
 * summary", hoặc bản ghi periodic stats khoảng 150 field) lại xuất hiện dưới số {@code #HN} khác
 * nhau ở các file khác nhau. Có vẻ số này được gán theo thứ tự đăng ký trong lần upload đó, không
 * phải theo quy ước cố định. Vì vậy <i>ý nghĩa</i> của từng header được phân loại bởi
 * {@link EndCallSchemaClassifier} dựa trên tập tên field đặc trưng, còn tag số chỉ dùng để tra
 * đúng layout cột trong phạm vi một file.
 */
@Component
public class EndCallLogParser implements ClientLogParser {

    private static final Pattern HEADER_LINE = Pattern.compile("^#H\\d+\\t");
    private static final String HEADER_PREFIX = "#H";
    /** Số cột nhãn ở đầu mỗi dòng header ({@code #HN}, {@code #ts}, {@code #tag}). */
    private static final int HEADER_LABEL_COLUMNS = 3;
    /** Cột đầu tiên chứa dữ liệu riêng của schema, trên cả dòng header và dòng dữ liệu. */
    private static final int FIRST_SCHEMA_COLUMN = 3;
    private static final String CALL_SUMMARY = "CALL_SUMMARY";
    private static final String ROLE_FIELD = "role";

    private final EndCallSchemaClassifier schemaClassifier;

    public EndCallLogParser(EndCallSchemaClassifier schemaClassifier) {
        this.schemaClassifier = schemaClassifier;
    }

    @Override
    public LogFileType type() {
        return LogFileType.END_CALL;
    }

    @Override
    public boolean recognizes(String firstMeaningfulLine) {
        return HEADER_LINE.matcher(firstMeaningfulLine).find();
    }

    @Override
    public NormalizedEvents parse(LogFile file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.path(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return NormalizedEvents.unreadable("Cannot read file " + file.path() + ": " + e.getMessage());
        }

        List<String> warnings = new ArrayList<>();
        Map<Integer, List<String>> headerFieldsBySchema = new LinkedHashMap<>();
        List<DataRow> dataRows = new ArrayList<>();
        splitHeadersAndRows(lines, headerFieldsBySchema, dataRows, warnings);

        Map<Integer, String> schemaTypeById = new LinkedHashMap<>();
        headerFieldsBySchema.forEach((id, fields) -> schemaTypeById.put(id, schemaClassifier.classify(fields)));

        Leg leg = inferLeg(dataRows, headerFieldsBySchema, schemaTypeById, file);

        List<CanonicalEvent> events = new ArrayList<>(dataRows.size());
        for (DataRow row : dataRows) {
            try {
                events.add(toEvent(row, headerFieldsBySchema, schemaTypeById, file, leg));
            } catch (RuntimeException e) {
                warnings.add("Skipped malformed row at " + file.path() + ":" + row.lineNumber()
                        + " - " + e.getMessage());
            }
        }
        return new NormalizedEvents(events, warnings);
    }

    private void splitHeadersAndRows(List<String> lines, Map<Integer, List<String>> headerFieldsBySchema,
                                     List<DataRow> dataRows, List<String> warnings) {
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split("\t", -1);
            if (columns[0].startsWith(HEADER_PREFIX)) {
                parseHeaderRow(columns, headerFieldsBySchema, warnings, lineNumber);
            } else {
                dataRows.add(new DataRow(lineNumber, columns));
            }
        }
    }

    private void parseHeaderRow(String[] columns, Map<Integer, List<String>> headerFieldsBySchema,
                               List<String> warnings, int lineNumber) {
        try {
            int schemaId = Integer.parseInt(columns[0].substring(HEADER_PREFIX.length()));
            List<String> fieldNames = columns.length > HEADER_LABEL_COLUMNS
                    ? List.of(columns).subList(HEADER_LABEL_COLUMNS, columns.length)
                    : List.of();
            headerFieldsBySchema.put(schemaId, fieldNames);
        } catch (NumberFormatException e) {
            warnings.add("Unrecognized header row at line " + lineNumber + ": " + String.join("\t", columns));
        }
    }

    private CanonicalEvent toEvent(DataRow row, Map<Integer, List<String>> headerFieldsBySchema,
                                   Map<Integer, String> schemaTypeById, LogFile file, Leg leg) {
        String[] columns = row.columns();
        if (columns.length < 2) {
            throw new IllegalArgumentException("row has fewer than 2 columns");
        }
        int schemaId = Integer.parseInt(columns[0].trim());
        long epochMillis = Long.parseLong(columns[1].trim());

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("tag", columns.length > 2 ? columns[2] : "");
        attributes.putAll(namedFields(columns, headerFieldsBySchema.get(schemaId)));

        return new CanonicalEvent(
                file.callId(),
                leg,
                EventSource.END_CALL,
                file.path().toString(),
                Instant.ofEpochMilli(epochMillis),
                columns[1],
                TimestampConfidence.EXACT,
                row.lineNumber(),
                schemaTypeById.getOrDefault(schemaId, "UNKNOWN_SCHEMA_" + schemaId),
                attributes,
                String.join("\t", columns)
        );
    }

    /**
     * Đặt tên các cột dữ liệu theo header tương ứng. Header của schema đó không có trong file
     * (tag số lạ) thì vẫn giữ lại giá trị dưới tên chung {@code field_N} - thà giữ dữ liệu không
     * có tên còn hơn mất dữ liệu.
     */
    private Map<String, String> namedFields(String[] columns, List<String> fieldNames) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (fieldNames == null) {
            for (int i = FIRST_SCHEMA_COLUMN; i < columns.length; i++) {
                fields.put("field_" + (i - FIRST_SCHEMA_COLUMN), columns[i]);
            }
            return fields;
        }
        for (int i = 0; i < fieldNames.size(); i++) {
            int columnIndex = FIRST_SCHEMA_COLUMN + i;
            if (columnIndex < columns.length && !columns[columnIndex].isEmpty()) {
                fields.put(fieldNames.get(i), columns[columnIndex]);
            }
        }
        return fields;
    }

    /**
     * Dòng CALL_SUMMARY có sẵn field {@code role} ("caller"/"callee") tường minh - đây là tín hiệu
     * đáng tin cậy nhất. Chỉ khi không có dòng nào như vậy mới dùng tới gợi ý từ tên file, vì mỗi
     * file vốn chỉ nói về một bên duy nhất.
     */
    private Leg inferLeg(List<DataRow> dataRows, Map<Integer, List<String>> headerFieldsBySchema,
                         Map<Integer, String> schemaTypeById, LogFile file) {
        for (Map.Entry<Integer, String> entry : schemaTypeById.entrySet()) {
            if (!CALL_SUMMARY.equals(entry.getValue())) {
                continue;
            }
            int schemaId = entry.getKey();
            int roleIndex = headerFieldsBySchema.get(schemaId).indexOf(ROLE_FIELD);
            if (roleIndex < 0) {
                continue;
            }
            for (DataRow row : dataRows) {
                String[] columns = row.columns();
                if (columns.length == 0 || !String.valueOf(schemaId).equals(columns[0].trim())) {
                    continue;
                }
                int columnIndex = FIRST_SCHEMA_COLUMN + roleIndex;
                if (columnIndex < columns.length) {
                    Leg leg = LegNaming.fromText(columns[columnIndex]);
                    if (leg != Leg.UNKNOWN) {
                        return leg;
                    }
                }
            }
        }
        return file.legHint() != Leg.UNKNOWN
                ? file.legHint()
                : LegNaming.fromText(file.fileName());
    }

    /** Một dòng dữ liệu chưa xử lý, giữ kèm số dòng để báo lỗi trỏ đúng chỗ. */
    private record DataRow(int lineNumber, String[] columns) {
    }
}
