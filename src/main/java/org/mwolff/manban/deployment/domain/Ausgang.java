package org.mwolff.manban.deployment.domain;

/** Ausgang eines gemeldeten Auslieferungsversuchs (Issue #1832, Fachplan #1784 AK 5). */
public enum Ausgang {
  /** Der Auslieferungslauf meldete Erfolg. */
  SUCCESS,
  /** Der Auslieferungslauf meldete das Scheitern, auch eine nicht bereite Anwendung. */
  FAILURE
}
