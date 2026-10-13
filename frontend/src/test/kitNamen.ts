import type { KitNamen } from '../api/kitNamen'

/**
 * Das Namen-Register, wie `GET /api/kit-namen` es liefert (Issue #1588): eine Abschrift von
 * `KitNamen.STANDARD` im Backend, je Sache der deutsche Name zuerst.
 *
 * **Warum das hier steht und nicht im Produktionscode:** Das Frontend führt keine eigene Paarliste
 * (Plan #1582, A4, E10) — es liest sie vom Server. Tests, die eine Karte mit Kit-Labels darstellen,
 * brauchen aber die Antwort des Servers; dieses Test-Double gibt sie wieder.
 */
export const KIT_NAMEN: KitNamen = {
  sachen: [
    { sache: 'DURCHZIEHEN', namen: ['kit:durchziehen', 'kit:carry-through'], schutz: 'KEINER' },
    { sache: 'GESCHUETZT', namen: ['kit:geschuetzt', 'kit:protected'], schutz: 'NUR_MENSCH_NIMMT_AB' },
    { sache: 'KLAEREN', namen: ['kit:klaeren', 'kit:clarify'], schutz: 'NUR_MENSCH_NIMMT_AB' },
    { sache: 'GEKLAERT', namen: ['kit:geklaert', 'kit:clarified'], schutz: 'KEINER' },
    { sache: 'NIGHT', namen: ['kit:night'], schutz: 'NUR_MENSCH_SETZT' },
    { sache: 'NIGHTRUN', namen: ['kit:nightrun'], schutz: 'NUR_MENSCH_SETZT' },
    { sache: 'PRUEFEN', namen: ['kit:pruefen', 'kit:check'], schutz: 'KEINER' },
    { sache: 'NIGHTPLAN', namen: ['kit:nightplan'], schutz: 'KEINER' },
    { sache: 'NIGHTREVIEW', namen: ['kit:nightreview'], schutz: 'KEINER' },
    { sache: 'NIGHTISSUES', namen: ['kit:nightissues'], schutz: 'KEINER' },
    { sache: 'WARTET_EREIGNIS', namen: ['kit:wartet', 'kit:on-hold'], schutz: 'NUR_MENSCH_NIMMT_AB' },
    { sache: 'LAUF_ABGEBROCHEN', namen: ['lauf:abgebrochen', 'run:aborted'], schutz: 'KEINER' },
    { sache: 'LAUF_LAEUFT', namen: ['lauf:laeuft', 'run:running'], schutz: 'KEINER' },
    { sache: 'LAUF_WARTET', namen: ['lauf:wartet', 'run:waiting'], schutz: 'KEINER' },
    { sache: 'REVIEW_OFFEN', namen: ['review:offen'], schutz: 'KEINER' },
    { sache: 'REVIEW_FERTIG', namen: ['review:fertig', 'review:done'], schutz: 'KEINER' },
    { sache: 'ZIEL_PLAN', namen: ['ziel:plan', 'goal:plan'], schutz: 'KEINER' },
    { sache: 'ZIEL_PAKETE', namen: ['ziel:pakete', 'goal:packages'], schutz: 'KEINER' },
    { sache: 'ZIEL_UMSETZUNG', namen: ['ziel:umsetzung', 'goal:implementation'], schutz: 'KEINER' },
    { sache: 'ZIEL_PUSH_VORBEREITET', namen: ['ziel:push-vorbereitet', 'goal:push-prepared'], schutz: 'KEINER' },
    { sache: 'PLANREVIEW_1', namen: ['planreview:1'], schutz: 'KEINER' },
    { sache: 'PLANREVIEW_2', namen: ['planreview:2'], schutz: 'KEINER' },
  ],
  titelPraefixe: [
    { art: 'FACHLICH', namen: ['[Fachlich]', '[Requirement]'] },
    { art: 'IDEE', namen: ['[Idee]', '[Idea]'] },
    { art: 'MENSCH', namen: ['[Mensch]', '[Human]'] },
    { art: 'PLAN', namen: ['[Plan]'] },
    { art: 'TASK', namen: ['[Task]'] },
  ],
  namensraum: ['kit:', 'lauf:', 'ziel:', 'review:', 'planreview:', 'run:', 'goal:'],
}
