package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Karten-Verwaltung eines Boards (Anlegen, Bearbeiten, Archivieren, Löschen). */
@RestController
class CardController {

    private final CardService cards;

    CardController(CardService cards) {
        this.cards = cards;
    }

    @PostMapping("/api/boards/{boardId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    CardView create(@AuthenticationPrincipal Long userId, @PathVariable long boardId,
                    @Valid @RequestBody CreateCardRequest request) {
        return cards.create(userId, boardId, request.columnId(), request.title(),
                request.description(), request.dependencies());
    }

    @GetMapping("/api/boards/{boardId}/cards")
    List<CardView> list(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
        return cards.listByBoard(userId, boardId);
    }

    @PatchMapping("/api/cards/{cardId}")
    CardView update(@AuthenticationPrincipal Long userId, @PathVariable long cardId,
                    @Valid @RequestBody UpdateCardRequest request) {
        return cards.update(userId, cardId, request.title(), request.description(), request.dependencies());
    }

    @PostMapping("/api/cards/{cardId}/move")
    CardView move(@AuthenticationPrincipal Long userId, @PathVariable long cardId,
                  @Valid @RequestBody MoveCardRequest request) {
        return cards.move(userId, cardId, request.columnId(), request.position());
    }

    @PostMapping("/api/cards/{cardId}/archive")
    CardView archive(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
        return cards.archive(userId, cardId);
    }

    @PostMapping("/api/cards/{cardId}/restore")
    CardView restore(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
        return cards.restore(userId, cardId);
    }

    @DeleteMapping("/api/cards/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
        cards.delete(userId, cardId);
    }

    record CreateCardRequest(
            @NotNull Long columnId,
            @NotBlank @Size(max = 300) String title,
            String description,
            List<Integer> dependencies) {
    }

    record UpdateCardRequest(
            @NotBlank @Size(max = 300) String title,
            String description,
            List<Integer> dependencies) {
    }

    record MoveCardRequest(
            @NotNull Long columnId,
            @jakarta.validation.constraints.PositiveOrZero int position) {
    }
}
