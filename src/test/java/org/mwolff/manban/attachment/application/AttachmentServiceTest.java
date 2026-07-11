package org.mwolff.manban.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.attachment.domain.Attachment;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;

/** Zeit-Test: der Anlege-Zeitstempel eines Anhangs stammt aus der injizierten Clock. */
class AttachmentServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void upload_setsCreatedAtFromInjectedClock() {
        // Given
        AttachmentRepository attachments = mock(AttachmentRepository.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        ContentTypeDetector detector = mock(ContentTypeDetector.class);
        ObjectStorageProperties properties = new ObjectStorageProperties(null, null, null, null, 20);
        CardRepository cards = mock(CardRepository.class);
        BoardRepository boards = mock(BoardRepository.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(cards.findById(5L)).thenReturn(Optional.of(new Card(5L, 10L, 20L, 1, "T", null, 0,
                false, null, 1L, FIXED, FIXED, CardType.CARD, null, null)));
        when(boards.findById(10L)).thenReturn(Optional.of(new Board(10L, 1L, "B", FIXED)));
        when(attachments.countByCardId(5L)).thenReturn(0L);
        when(detector.detect(any(), any())).thenReturn("text/plain");
        when(attachments.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));
        AttachmentService service = new AttachmentService(attachments, storage, detector, properties,
                cards, boards, permissions, clock);

        // When
        service.upload(1L, 5L, "note.txt", new byte[] {1, 2, 3});

        // Then
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachments).save(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
    }
}
