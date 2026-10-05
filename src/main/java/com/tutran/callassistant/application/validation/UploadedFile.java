package com.tutran.callassistant.application.validation;

import java.util.Objects;

/** Một file người dùng đính kèm, ở dạng thô: tên người dùng đặt (không đáng tin) + nội dung. */
public record UploadedFile(String name, byte[] content) {

    public UploadedFile {
        Objects.requireNonNull(content, "content");
        name = name == null ? "" : name;
    }
}
