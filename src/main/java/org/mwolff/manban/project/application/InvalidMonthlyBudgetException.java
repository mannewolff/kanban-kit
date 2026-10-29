package org.mwolff.manban.project.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Monatsbudget ist nicht größer als 0, hat mehr als zwei Nachkommastellen oder liegt über 9 999 999
 * 999,99 USD (Issue #1965, Plan #1962 E3).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidMonthlyBudgetException extends RuntimeException
    implements UebersetzbareMeldung {

  public InvalidMonthlyBudgetException() {
    super(
        "Das Monatsbudget muss größer als 0 USD sein, höchstens zwei Nachkommastellen haben und"
            + " darf höchstens 9 999 999 999,99 USD betragen.");
  }

  @Override
  public String schluessel() {
    return "project.monatsbudget-ungueltig";
  }
}
