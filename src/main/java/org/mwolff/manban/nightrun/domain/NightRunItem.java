package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Ein Arbeitspaket, wie ein Nachtlauf es hinterlassen hat (Issue #721).
 *
 * <p>Titel und Auszug sind Schnappschüsse aus dem Protokoll, keine Verweise: Wird die Karte später
 * umbenannt oder gelöscht, bleibt die Auswertung lesbar.
 *
 * <p>Zu einem grünen Zustand gehört keine Fehlerklasse, zu jedem anderen genau eine. Diese
 * Korrelation steht bewusst nicht als {@code CHECK} in der Datenbank — die Zustandsliste kann sich
 * unabhängig von den Fehlerklassen weiterentwickeln —, sondern ist in {@code NightRunRepositoryIT}
 * belegt.
 *
 * <p>Ein Arbeitspaket überdauert die Verdrängung seines Laufs (Issue #964): Es trägt Projekt,
 * Startzeitpunkt, Lauf-Art und seit Issue #1010 auch die Gattung selbst, damit die Messwerte einer
 * Karte nicht mit der Aufbewahrung der Läufe verschwinden. Geschrieben werden diese vier stets aus
 * dem Lauf, zu dem das Paket gehört — die Werte am einzuliefernden Paket sind dafür nicht
 * maßgeblich.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param nightRunId zugehöriger Lauf; {@code null}, solange dessen ID noch nicht vergeben ist — und
 *     bei einem gespeicherten Paket, wenn der Lauf verdrängt wurde
 * @param projectId Projekt des Laufs
 * @param startedAt Startzeitpunkt des Laufs
 * @param mode Betriebsart des Laufs
 * @param kind Gattung des Laufs — Nachtlauf oder interaktive Sitzung (Issue #1010); niemals {@code
 *     null}, ein Erzeugungspfad ohne eigene Gattung trägt {@link NightRunKind#NIGHT}
 * @param cardNumber projektweite Kartennummer des Arbeitspakets
 * @param title Titel zum Zeitpunkt des Laufs
 * @param state Ausgang des Arbeitspakets
 * @param errorClass Grund für einen nicht-grünen Ausgang; {@code null} bei {@link
 *     NightRunState#GREEN}
 * @param durationMs Dauer in Millisekunden; {@code null} bei übergangenen Paketen
 * @param commitHash Commit der Session; {@code null}, wenn nichts festgeschrieben wurde
 * @param excerpt Protokollauszug, der den Zustand begründet; höchstens {@link
 *     NightRunLimits#EXCERPT_MAX} Zeichen
 */
public record NightRunItem(
    @Nullable Long id,
    @Nullable Long nightRunId,
    long projectId,
    Instant startedAt,
    NightRunMode mode,
    NightRunKind kind,
    int cardNumber,
    String title,
    NightRunState state,
    @Nullable NightRunErrorClass errorClass,
    @Nullable Long durationMs,
    @Nullable String commitHash,
    @Nullable String excerpt,
    @Nullable NightRunUsage usage)
    implements Identifiable {

  /**
   * Kopie mit gesetztem Fremdschlüssel.
   *
   * <p>Der Lauf vergibt seine ID erst beim Einfügen; erst danach lassen sich seine Arbeitspakete
   * schreiben. {@code NightRunRepository.insertIfAbsent} reicht die frisch vergebene ID hier
   * hinein, statt sie den Aufrufer verwalten zu lassen.
   */
  public NightRunItem withNightRunId(Long newNightRunId) {
    return new NightRunItem(
        id,
        newNightRunId,
        projectId,
        startedAt,
        mode,
        kind,
        cardNumber,
        title,
        state,
        errorClass,
        durationMs,
        commitHash,
        excerpt,
        usage);
  }
}
