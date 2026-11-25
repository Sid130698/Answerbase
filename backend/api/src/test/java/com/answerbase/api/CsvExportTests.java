package com.answerbase.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvExportTests {
    @Test
    void preventsSpreadsheetFormulaExecutionAndQuotesEmbeddedSeparators() {
        assertThat(QueryAdminService.cell("=HYPERLINK(\"bad\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"bad\"\")\"");
        assertThat(QueryAdminService.cell("  +cmd")).isEqualTo("\"'  +cmd\"");
        assertThat(QueryAdminService.cell("\tvalue")).isEqualTo("\"'\tvalue\"");
        assertThat(QueryAdminService.cell("How, and why?\nNext line"))
                .isEqualTo("\"How, and why?\nNext line\"");
    }
}
