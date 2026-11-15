package org.mwolff.manban.fremdboard.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.FremdBoardQuelle;
import org.mwolff.manban.fremdboard.domain.FremdEreignis;
import org.mwolff.manban.fremdboard.domain.FremdKarte;
import org.mwolff.manban.fremdboard.domain.FremdNotiz;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.springframework.stereotype.Component;

/**
 * Liest das Board eines Projekts aus GitLab über die REST-API v4 (Issue #2160, Plan #2152 A5, A15),
 * gleich ob selbst betrieben oder gitlab.com. Nur lesend: Der Zugang braucht in GitLab nicht mehr
 * als {@code read_api}.
 *
 * <p>Jeder Abruf geht über {@link GitLabAbruf} und damit über {@link HostErlaubnis}
 * (Erlaubnisliste, nur {@code https}, keine Weiterleitung, Zeitlimit; A14); Listen werden
 * seitenweise zu je {@value #SEITENGROESSE} Einträgen gelesen. Scheitert ein Abruf, wirft die
 * Quelle {@link GitLabAbrufException} mit lesbarem Grund; weder Meldung noch Protokoll nennen den
 * Zugang.
 */
@Component
public final class GitLabQuelle implements FremdBoardQuelle {

  private static final int SEITENGROESSE = 100;
  private static final String ISSUES = "/issues";

  private final GitLabAbruf abruf;

  GitLabQuelle(HostErlaubnis erlaubnis, ZugangsTresor tresor) {
    this.abruf = new GitLabAbruf(erlaubnis, tresor);
  }

  @Override
  public FremdBoardArt art() {
    return FremdBoardArt.GITLAB;
  }

  @Override
  public List<Long> nummern(Verbindung verbindung) {
    return abruf.seiten(verbindung, ISSUES + "?state=all&per_page=" + SEITENGROESSE).stream()
        .map(eintrag -> GitLabAntwort.zahl(eintrag, "iid"))
        .toList();
  }

  @Override
  public List<FremdKarte> geaenderteKarten(Verbindung verbindung, Instant seit) {
    return abruf
        .seiten(
            verbindung,
            ISSUES
                + "?state=all&updated_after="
                + GitLabAbruf.kodiert(seit.toString())
                + "&order_by=updated_at&sort=asc&per_page="
                + SEITENGROESSE)
        .stream()
        .map(GitLabAntwort::karte)
        .toList();
  }

  @Override
  public Optional<FremdKarte> karte(Verbindung verbindung, long nummer) {
    return abruf.eintrag(verbindung, ISSUES + "/" + nummer).map(GitLabAntwort::karte);
  }

  @Override
  public List<FremdEreignis> ereignisse(Verbindung verbindung, long nummer) {
    List<FremdEreignis> ereignisse = new ArrayList<>();
    String karte = ISSUES + "/" + nummer;
    for (JsonNode eintrag :
        abruf.seiten(verbindung, karte + "/resource_label_events?per_page=" + SEITENGROESSE)) {
      GitLabAntwort.labelEreignis(eintrag, nummer).ifPresent(ereignisse::add);
    }
    for (JsonNode eintrag :
        abruf.seiten(verbindung, karte + "/resource_state_events?per_page=" + SEITENGROESSE)) {
      GitLabAntwort.statusEreignis(eintrag, nummer).ifPresent(ereignisse::add);
    }
    ereignisse.sort(Comparator.comparing(FremdEreignis::zeit));
    return List.copyOf(ereignisse);
  }

  @Override
  public List<FremdNotiz> notizen(Verbindung verbindung, long nummer) {
    return abruf
        .seiten(
            verbindung,
            ISSUES + "/" + nummer + "/notes?sort=asc&order_by=created_at&per_page=" + SEITENGROESSE)
        .stream()
        .flatMap(eintrag -> GitLabAntwort.notiz(eintrag, nummer).stream())
        .toList();
  }

  @Override
  public String adresse(Verbindung verbindung, long nummer) {
    return GitLabAbruf.ohneSchlussstrich(verbindung.basisUrl())
        + "/"
        + verbindung.projektPfad()
        + "/-/issues/"
        + nummer;
  }
}
