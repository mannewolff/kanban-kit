package org.mwolff.manban.codeanalyse.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.codeanalyse.web.MessungIngestController.CodeAnalysisMeasurementRequest;
import org.mwolff.manban.codeanalyse.web.MessungIngestController.CodeAnalysisStateRequest;

/**
 * Prüft {@link Zaehlregel} (Issue #2277, Plan #2272 E8); jeder Verstoß steht unter seinem Feldnamen
 * aus V1, eine Zahl eines Zustands etwa unter {@code final.bugs}.
 *
 * <p>Öffentlich, weil die Bean Validation den Prüfer ohne Spring über seinen öffentlichen
 * Konstruktor anlegt.
 */
public final class ZaehlregelValidator
    implements ConstraintValidator<Zaehlregel, CodeAnalysisMeasurementRequest> {

  static final String ZUSTAND_FEHLT = "{codeanalyse.messung.zustandFehlt}";
  static final String GRUND_FEHLT = "{codeanalyse.messung.grundFehlt}";
  static final String ZAHL_FEHLT = "{jakarta.validation.constraints.NotNull.message}";
  static final String ZAHL_NEGATIV = "{jakarta.validation.constraints.PositiveOrZero.message}";

  @Override
  public boolean isValid(
      CodeAnalysisMeasurementRequest request, ConstraintValidatorContext context) {
    context.disableDefaultConstraintViolation();
    boolean gueltig = zahlen(request.before(), "before", context);
    gueltig &= zahlen(request.endzustand(), "final", context);
    Boolean counts = request.counts();
    if (Boolean.TRUE.equals(counts)) {
      gueltig &= vorhanden(request.before() != null, "before", ZUSTAND_FEHLT, context);
      gueltig &= vorhanden(request.endzustand() != null, "final", ZUSTAND_FEHLT, context);
    } else if (Boolean.FALSE.equals(counts)) {
      String reason = request.reason();
      gueltig &= vorhanden(reason != null && !reason.isBlank(), "reason", GRUND_FEHLT, context);
    }
    return gueltig;
  }

  private static boolean zahlen(
      @Nullable CodeAnalysisStateRequest state, String feld, ConstraintValidatorContext context) {
    if (state == null) {
      return true;
    }
    boolean gueltig = zahl(state.bugs(), feld + ".bugs", context);
    gueltig &= zahl(state.vulnerabilities(), feld + ".vulnerabilities", context);
    gueltig &= zahl(state.codeSmells(), feld + ".codeSmells", context);
    gueltig &= zahl(state.changedLines(), feld + ".changedLines", context);
    return gueltig;
  }

  private static boolean zahl(
      @Nullable Integer wert, String feld, ConstraintValidatorContext context) {
    if (wert == null) {
      return verstoss(feld, ZAHL_FEHLT, context);
    }
    return wert >= 0 || verstoss(feld, ZAHL_NEGATIV, context);
  }

  private static boolean vorhanden(
      boolean da, String feld, String meldung, ConstraintValidatorContext context) {
    return da || verstoss(feld, meldung, context);
  }

  private static boolean verstoss(String feld, String meldung, ConstraintValidatorContext context) {
    context
        .buildConstraintViolationWithTemplate(meldung)
        .addPropertyNode(feld)
        .addConstraintViolation();
    return false;
  }
}
