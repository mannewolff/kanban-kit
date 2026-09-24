import type { NightRunErrorClass, NightRunState } from '../lib/nightRunLog'
import type { NightRunOutcomeView, NightRunServerMode } from './nightRuns'
import { apiFetch } from './client'
import { leserZone } from './nightRunUsage'

/**
 * Anbindung des Plattform-Leitstands (Issue #1083, Server in #1080; drei Listen seit #1095/#1098).
 *
 * Der Pfadstamm ist `/api/admin` und nicht `/api/platform` — das ist eine Sicherheitsentscheidung
 * des Servers (Plan #1072 E24): Nur dort verlangt die Filterkette eine Sitzung und laesst kein
 * Token zu. Der Browser folgt ihr hier bloss.
 */

/**
 * Eine Zeile des Plattform-Leitstands — **dieselbe Form fuer alle drei Listen** (#1095): ein
 * laufender Lauf, ein durchgefuehrter und eine Stoerung tragen dieselben Angaben. Woraus der
 * Browser welchen Melder und welches Wort bildet, steht im `outcome`.
 *
 * Der **Grund** kommt als `outcome` und nicht als fertiger Satz: Den Text bildet der Browser aus
 * denselben Tabellen, aus denen die Nachtlauf-Auswertung ihn zeigt (AK 6 der fachlichen Quelle
 * #1064). Ein Satz vom Server waere die zweite Formulierung desselben Sachverhalts.
 */
export interface DisruptionView {
  nightRunId: number
  projectId: number
  projectName: string
  /** Die Art des Laufs (Issue #1128) — an jeder Zeile als Marke. */
  mode: NightRunServerMode
  startedAt: string
  outcome: NightRunOutcomeView
}

/**
 * Die Bereiche der Ansicht in **einer** Antwort (Kriterium 18 der fachlichen Quelle #1086); seit
 * #1170 tragen die laufenden Laeufe ihre gemeldeten Pakete daneben.
 *
 * <p>Ein Abruf und nicht drei (Plan #1088 E5): Die Seite frischt sich auf, und ein Lauf kann
 * zwischen zwei Rundreisen den Bereich wechseln — aus drei Abrufen erschiene er doppelt oder gar
 * nicht. Aus derselben Antwort liest die Seite auch, ob es zu einem durchgefuehrten Lauf eine
 * Stoerung gibt; ein eigenes Serverfeld waere eine zweite Quelle fuer dieselbe Aussage.
 */
export interface LeitstandView {
  /** Laeufe der laufenden Nacht, die noch arbeiten; juengster zuoberst. */
  laufende: DisruptionView[]
  /** Beendete Laeufe des laufenden Zyklus, verstummte eingeschlossen; juengster zuoberst. */
  durchgefuehrte: DisruptionView[]
  /** Beendete Laeufe des vorigen Zyklus (Issue #1135), in derselben Form und Ordnung. */
  durchgefuehrteVoriger: DisruptionView[]
  /** Offene Stoerungen ueber **alle** Naechte (Kriterium 17), juengste zuoberst. */
  stoerungen: DisruptionView[]
  /**
   * Die gemeldeten Pakete der Sektion „Aktueller Status" (#1170, gezeigt seit #1173).
   *
   * <p><b>Nur die Pakete der laufenden Runs</b> — ein Eintrag je Lauf aus {@link laufende}, in
   * derselben Ordnung, auch ohne ein einziges Paket. Projektname und Ordnung kommen aus
   * `laufende`, damit es die Zuordnung Projekt → Lauf nur einmal gibt; die beendeten Laeufe
   * tragen hier nichts, weil AK 11 der Quelle #1153 ihre Pakete nicht zeigt.
   */
  gemeldetePakete: LaufPaketeView[]
}

/**
 * Die gemeldeten Pakete **eines** laufenden Laufs — eine eigene Liste neben {@link
 * LeitstandView.laufende} und kein Feld an {@link DisruptionView} (Plan #1167, E1): Jene ist die
 * eine Zeilenform aller vier Lauf-Listen, und ein nur dort gefuelltes Feld stuende in den anderen
 * leer und behauptete „keine Pakete" statt „nicht gefragt".
 */
export interface LaufPaketeView {
  nightRunId: number
  /** In der Reihenfolge, in der der Lauf sie meldete; leer, wenn noch keines vorliegt. */
  pakete: PaketView[]
}

/**
 * Ein gemeldetes Paket, so schmal wie die Sektion es braucht (Plan #1167, E2).
 *
 * <p>Absichtlich **nicht** {@link NightRunItemView}: Das brachte Kosten, Dauer, Stufen, Verbrauch
 * und vor allem den Protokollauszug je Paket mit — und AK 7 der Quelle #1153 verbietet fuer diese
 * Sektion jede Obergrenze.
 */
export interface PaketView {
  /** Projektweite Kartennummer des Pakets. */
  cardNumber: number
  /** Titel zum Zeitpunkt des Laufs — ein Schnappschuss, kein Verweis. */
  title: string
  state: NightRunState
  /** Grund fuer einen nicht-gruenen Ausgang; `null` bei gruen. */
  errorClass: NightRunErrorClass | null
  /** Ob es im Projekt noch eine Karte zu dieser Nummer gibt — nur dann fuehrt ein Weg dorthin. */
  cardExists: boolean
}

export const plattformLeitstandApi = {
  /**
   * Die drei Listen der Ansicht.
   *
   * Die **Zone kommt vom Leser** (Plan #1088 E6) — dieselbe Quelle wie in `api/nightRunUsage.ts`:
   * Im Container laeuft die JVM regelmaessig in UTC, und die Nachtgrenze „12:00 zonenlokal" laege
   * dann um Stunden verschoben gegen die, die die Nachtlauf-Auswertung zieht.
   */
  leitstand: (zone: string = leserZone()) =>
    apiFetch<LeitstandView>(`/api/admin/leitstand?${new URLSearchParams({ zone })}`),
  /**
   * Quittiert eine Stoerung — „ich habe es gesehen" (AK 8). Idempotent: Ein zweiter Aufruf ist kein
   * Fehler, weil zwei Admins dieselbe Zeile gleichzeitig wegraeumen koennen.
   */
  quittieren: (laufId: number) =>
    apiFetch<void>(`/api/admin/disruptions/${laufId}`, { method: 'DELETE' }),
}
