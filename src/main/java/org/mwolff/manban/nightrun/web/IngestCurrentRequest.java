package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.RunnerStufe;

/**
 * Die gemeldeten laufenden Stufen eines Laufs (Issue #1725, Plan #1724 Vertrag V1).
 *
 * <p>Eine eigene Datei aus demselben Grund wie {@link IngestStuckRequest}: Die Stufen gehören zum
 * Laufbericht, und der Ingest-Controller ist ohnehin der längste Record-Träger des Moduls.
 */
@Schema(description = "Die Stufen, die der Lauf gerade fährt; höchstens 10.")
record IngestCurrentRequest(
    @Schema(description = "Die laufenden Stufen in der gemeldeten Reihenfolge.")
        @NotNull
        @Size(max = MAX_STAGES)
        List<@Valid @NotNull IngestCurrentStageRequest> stages) {

  /** Obergrenze der laufenden Stufen — der {@code CHECK} auf {@code position} aus {@code V53}. */
  static final int MAX_STAGES = 10;

  /** Die Stufen als Domänenwerte; ein fehlender Block ergibt die leere Liste. */
  static List<RunnerStufe> toDomain(@Nullable IngestCurrentRequest request) {
    if (request == null) {
      return List.of();
    }
    return request.stages().stream().map(IngestCurrentStageRequest::toDomain).toList();
  }
}
