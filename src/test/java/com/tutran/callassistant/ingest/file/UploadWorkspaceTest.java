package com.tutran.callassistant.ingest.file;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UploadWorkspaceTest {

    @Test
    void storesFileInsideWorkspaceEvenWhenNameTriesToEscape() throws Exception {
        try (UploadWorkspace workspace = UploadWorkspace.create()) {
            Path stored = workspace.store("../../evil.log", "x".getBytes());

            assertThat(stored.getParent()).isEqualTo(workspace.directory());
            assertThat(stored.getFileName().toString()).isEqualTo("evil.log");
            assertThat(Files.readString(stored)).isEqualTo("x");
        }
    }

    @Test
    void sameNameTwiceDoesNotOverwrite() {
        try (UploadWorkspace workspace = UploadWorkspace.create()) {
            Path first = workspace.store("a.log", "1".getBytes());
            Path second = workspace.store("a.log", "2".getBytes());

            assertThat(second).isNotEqualTo(first);
            assertThat(workspace.directory().toFile().list()).hasSize(2);
        }
    }

    @Test
    void blankOrMissingNameFallsBackToDefault() {
        assertThat(UploadWorkspace.safeName(null)).isEqualTo("upload.log");
        assertThat(UploadWorkspace.safeName("  ")).isEqualTo("upload.log");
        assertThat(UploadWorkspace.safeName("C:\\logs\\caller_endcall.log")).isEqualTo("caller_endcall.log");
    }

    @Test
    void closeRemovesDirectoryAndContents() {
        Path dir;
        try (UploadWorkspace workspace = UploadWorkspace.create()) {
            workspace.store("a.log", "1".getBytes());
            dir = workspace.directory();
        }
        assertThat(dir).doesNotExist();
    }
}
