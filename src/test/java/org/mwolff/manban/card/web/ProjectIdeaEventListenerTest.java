package org.mwolff.manban.card.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.ProjectIdeasChangedEvent;

/** Der Listener reicht das Event an die Registry weiter. */
class ProjectIdeaEventListenerTest {

  @Test
  void onProjectIdeasChanged_forwardsToRegistry() {
    ProjectIdeaEventRegistry registry = mock(ProjectIdeaEventRegistry.class);
    var listener = new ProjectIdeaEventListener(registry);
    var event = new ProjectIdeasChangedEvent(42L);

    listener.onProjectIdeasChanged(event);

    verify(registry).publish(42L);
  }
}
