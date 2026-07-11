package org.mwolff.manban.kanbancompat.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit-Tests für die Spaltennamen-Normalisierung {@link KanbanCompatService#canonicalKey}. */
class KanbanCompatServiceTest {

    @ParameterizedTest
    @CsvSource({
        "Backlog, BACKLOG",
        "Ready, READY",
        "In Progress, IN_PROGRESS",
        "In Review, IN_REVIEW",
        "Done, DONE",
        "'  done  ', DONE",
        "In-Progress, IN_PROGRESS"
    })
    void canonicalKey_mapsKnownColumnName_toKey(String columnName, String expectedKey) {
        // When / Then: bekannter Spaltenname liefert den Kanban-Key
        assertThat(KanbanCompatService.canonicalKey(columnName)).contains(expectedKey);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Unbekannt", "Todo", "123"})
    void canonicalKey_returnsEmpty_whenNoMatch(String columnName) {
        // When / Then: kein Treffer -> leeres Optional (nicht null)
        assertThat(KanbanCompatService.canonicalKey(columnName)).isEmpty();
    }
}
