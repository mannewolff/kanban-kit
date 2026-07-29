package org.mwolff.manban.outbox.infrastructure;

import org.mwolff.manban.outbox.application.OutboxDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Geplanter Outbox-Worker: ruft den {@link OutboxDispatchService} auf (separater Bean → keine
 * Self-Invocation). Abschaltbar über {@code manban.outbox.enabled=false} — dann bleiben fällige
 * Einträge liegen und werden nachgeholt, sobald der Worker wieder läuft (Issue #501).
 *
 * <p>{@code fixedDelay} statt {@code fixedRate}: Der Abstand zählt ab dem <em>Ende</em> des vorigen
 * Laufs. Ein langsamer Lauf staut so keine Folgeläufe auf.
 */
@Component
@ConditionalOnProperty(name = "manban.outbox.enabled", havingValue = "true", matchIfMissing = true)
class OutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

  private final OutboxDispatchService dispatch;

  OutboxWorker(OutboxDispatchService dispatch) {
    this.dispatch = dispatch;
  }

  @Scheduled(fixedDelayString = "${manban.outbox.poll-interval-ms:5000}")
  void run() {
    int dispatched = dispatch.dispatchDue();
    if (dispatched > 0) {
      log.info("Outbox: {} Einträge zugestellt", dispatched);
    }
  }
}
