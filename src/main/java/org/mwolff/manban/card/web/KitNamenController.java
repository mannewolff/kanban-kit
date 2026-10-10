package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Eintrag;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert dem Frontend das Namen-Register des claude-workflow-kits aus {@link KitNamen#STANDARD}
 * (Issue #1588, Plan #1582 A4, E2, E11), damit es keine zweite Kopie in TypeScript führt. Lesen
 * darf jeder Angemeldete, per Session oder ungebundenem Token; die Liste enthält keine
 * Projektdaten.
 */
@Tag(
    name = "Kit-Namen",
    description =
        "Die Namen, unter denen das claude-workflow-kit Labels und Kartenarten führt, in deutscher"
            + " und englischer Form.")
@RestController
class KitNamenController {

  @Operation(
      summary = "Kit-Namen lesen",
      description =
          "Liefert die Sachen des Kits mit allen ihren Label-Namen, die Titel-Präfixe je Kartenart"
              + " und die Präfixe des Namensraums des Kits, jeweils in Registerreihenfolge, der"
              + " deutsche Name zuerst. Deutscher und englischer Name derselben Sache gelten als"
              + " eins.")
  @ApiResponse(responseCode = "200", description = "Das Namen-Register.")
  @ApiVertrag
  @GetMapping("/api/kit-namen")
  KitNamenView kitNamen() {
    KitNamen register = KitNamen.STANDARD;
    List<SacheView> sachen =
        register.eintraege().stream()
            .map(Eintrag::sache)
            .distinct()
            .map(s -> new SacheView(s.name(), register.namen(s)))
            .toList();
    List<ArtView> titelPraefixe =
        KitNamen.titelPraefixe().entrySet().stream()
            .map(e -> new ArtView(e.getKey().name(), e.getValue()))
            .toList();
    return new KitNamenView(sachen, titelPraefixe, KitNamen.namensraumPraefixe());
  }

  @Schema(description = "Das Namen-Register des claude-workflow-kits.")
  record KitNamenView(
      @ArraySchema(arraySchema = @Schema(description = "Sachen in Registerreihenfolge."))
          List<SacheView> sachen,
      @ArraySchema(arraySchema = @Schema(description = "Titel-Präfixe je Kartenart."))
          List<ArtView> titelPraefixe,
      @ArraySchema(
              arraySchema =
                  @Schema(
                      description =
                          "Präfixe des Namensraums des Kits, ohne Rücksicht auf Groß- und"
                              + " Kleinschreibung verglichen."),
              schema = @Schema(example = "kit:"))
          List<String> namensraum) {}

  @Schema(description = "Eine Sache des Kits mit allen ihren Label-Namen.")
  record SacheView(
      @Schema(description = "Kennung der Sache.", example = "KLAEREN") String sache,
      @ArraySchema(
              arraySchema = @Schema(description = "Namen der Sache, der deutsche zuerst."),
              schema = @Schema(example = "kit:klaeren"))
          List<String> namen) {}

  @Schema(description = "Eine Kartenart mit ihren Titel-Präfixen.")
  record ArtView(
      @Schema(description = "Kennung der Kartenart.", example = "FACHLICH") String art,
      @ArraySchema(
              arraySchema = @Schema(description = "Titel-Präfixe der Kartenart, deutsch zuerst."),
              schema = @Schema(example = "[Fachlich]"))
          List<String> namen) {}
}
