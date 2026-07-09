package org.mwolff.manban.board.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
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

/** Board-Verwaltung innerhalb eines Projekts. */
@RestController
class BoardController {

    private final BoardService boards;

    BoardController(BoardService boards) {
        this.boards = boards;
    }

    @PostMapping("/api/projects/{projectId}/boards")
    @ResponseStatus(HttpStatus.CREATED)
    BoardView create(@AuthenticationPrincipal Long userId, @PathVariable long projectId,
                     @Valid @RequestBody BoardRequest request) {
        return boards.createBoard(userId, projectId, request.name());
    }

    @GetMapping("/api/projects/{projectId}/boards")
    List<BoardView> list(@AuthenticationPrincipal Long userId, @PathVariable long projectId) {
        return boards.listBoards(userId, projectId);
    }

    @PatchMapping("/api/boards/{boardId}")
    BoardView rename(@AuthenticationPrincipal Long userId, @PathVariable long boardId,
                     @Valid @RequestBody BoardRequest request) {
        return boards.renameBoard(userId, boardId, request.name());
    }

    @DeleteMapping("/api/boards/{boardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
        boards.deleteBoard(userId, boardId);
    }

    record BoardRequest(@NotBlank @Size(max = 200) String name) {
    }
}
