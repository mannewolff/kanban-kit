package org.mwolff.manban.kanbancompat.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Created;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Epic;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Item;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Toolbox-Kanban-API für tbx.mjs / board.mjs (Dogfooding, #45). Vertragsgleich mit dem
 * bestehenden Toolbox-Backend: der Client sendet nur den Token ({@code X-Kanban-Token});
 * das Board kommt aus der Token-Bindung (#44), die der {@code PatAuthenticationFilter} an
 * die Authentication-{@code details} hängt. Nur per PAT erreichbar (SecurityConfig).
 */
@RestController
@RequestMapping("/api/kanban")
class KanbanCompatController {

    private final KanbanCompatService service;

    KanbanCompatController(KanbanCompatService service) {
        this.service = service;
    }

    @GetMapping("/items")
    Map<String, List<Item>> items(Authentication authentication) {
        return service.items(principal(authentication));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    Created create(Authentication authentication, @Valid @RequestBody CreateItemRequest request) {
        return service.create(principal(authentication), request.title(), request.body(), request.column());
    }

    @PutMapping("/items/{id}/move")
    void move(Authentication authentication, @PathVariable long id, @Valid @RequestBody MoveRequest request) {
        service.move(principal(authentication), id, request.column(), request.position());
    }

    @PostMapping("/items/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    void comment(Authentication authentication, @PathVariable long id, @Valid @RequestBody CommentRequest request) {
        service.comment(principal(authentication), id, request.body());
    }

    @GetMapping("/epics")
    List<Epic> epics(Authentication authentication) {
        return service.epics(principal(authentication));
    }

    /** Zieht die Token-Bindung aus den Authentication-details; ohne Bindung → 409. */
    private static KanbanPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof KanbanPrincipal p) {
            return p;
        }
        throw new TokenNotBoundException();
    }

    record CreateItemRequest(@NotBlank @Size(max = 300) String title, String body, String column) {
    }

    record MoveRequest(@NotBlank String column, @PositiveOrZero int position) {
    }

    record CommentRequest(@NotBlank String body) {
    }
}
