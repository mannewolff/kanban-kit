package org.mwolff.manban.ratelimit.application;

/**
 * Port, der eine Abweisung wegen Überlast je Person entgegennimmt (Issue #1000, Plan #995 E14).
 *
 * <p>Der Zählzustand der Bremse liegt im Prozessspeicher und überlebt keinen Neustart; die Sicht
 * des Plattform-Admins soll aber auch gestern beantworten können. Der Port trennt das Melden vom
 * Ablegen: Wer meldet, zahlt dafür keinen Schreibzugriff.
 */
@FunctionalInterface
public interface RejectionRecorder {

  /** Meldet eine Abweisung der Person. Kehrt sofort zurück; geschrieben wird gepuffert. */
  void record(long userId);
}
