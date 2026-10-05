package com.tutran.callassistant.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Phân tích tham số dạng {@code --ten gia-tri} (và {@code --ten=gia-tri}) của các lệnh CLI. */
final class Options {

    private Options() {
    }

    static Map<String, String> parse(List<String> arguments) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (!argument.startsWith("--")) {
                continue;
            }
            String body = argument.substring(2);
            int equals = body.indexOf('=');
            if (equals >= 0) {
                options.put(body.substring(0, equals), body.substring(equals + 1));
            } else if (i + 1 < arguments.size() && !arguments.get(i + 1).startsWith("--")) {
                options.put(body, arguments.get(++i));
            } else {
                options.put(body, "true");
            }
        }
        return options;
    }

    static int intOf(Map<String, String> options, String key, int fallback) {
        try {
            return Integer.parseInt(options.getOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + key + " phải là số nguyên");
        }
    }
}
