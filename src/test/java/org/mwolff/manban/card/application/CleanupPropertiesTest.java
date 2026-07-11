package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests der Defaulting-Logik im Kompaktkonstruktor von {@link CleanupProperties}. */
class CleanupPropertiesTest {

    @Test
    void appliesDefaults_whenAllValuesNull() {
        // When
        CleanupProperties props = new CleanupProperties(null, null, null);

        // Then
        assertThat(props.enabled()).isTrue();
        assertThat(props.doneRetentionDays()).isEqualTo(30);
        assertThat(props.cron()).isEqualTo("0 0 * * * *");
    }

    @Test
    void clampsNonPositiveDaysAndBlankCronToDefaults() {
        // When
        CleanupProperties props = new CleanupProperties(true, 0, "   ");

        // Then
        assertThat(props.doneRetentionDays()).isEqualTo(30);
        assertThat(props.cron()).isEqualTo("0 0 * * * *");
    }

    @Test
    void keepsProvidedValues() {
        // When
        CleanupProperties props = new CleanupProperties(false, 14, "0 0 2 * * *");

        // Then
        assertThat(props.enabled()).isFalse();
        assertThat(props.doneRetentionDays()).isEqualTo(14);
        assertThat(props.cron()).isEqualTo("0 0 2 * * *");
    }
}
