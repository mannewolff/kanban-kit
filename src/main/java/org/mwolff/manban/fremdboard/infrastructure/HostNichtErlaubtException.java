package org.mwolff.manban.fremdboard.infrastructure;

/**
 * Eine Adresse liegt außerhalb der Erlaubnisliste oder ist kein {@code https} (Issue #2157, Plan
 * #2152 A14). Abgerufen wird sie dann nicht.
 */
public class HostNichtErlaubtException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  HostNichtErlaubtException(String meldung) {
    super(meldung);
  }

  HostNichtErlaubtException(String meldung, Throwable ursache) {
    super(meldung, ursache);
  }
}
