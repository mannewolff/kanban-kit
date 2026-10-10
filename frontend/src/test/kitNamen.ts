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
    { sache: 'DURCHZIEHEN', namen: ['kit:durchziehen', 'kit:carry-through'] },
    { sache: 'GESCHUETZT', namen: ['kit:geschuetzt', 'kit:protected'] },
    { sache: 'KLAEREN', namen: ['kit:klaeren', 'kit:clarify'] },
    { sache: 'NIGHT', namen: ['kit:night'] },
    { sache: 'NIGHTRUN', namen: ['kit:nightrun'] },
    { sache: 'PRUEFEN', namen: ['kit:pruefen', 'kit:check'] },
    { sache: 'NIGHTPLAN', namen: ['kit:nightplan'] },
    { sache: 'NIGHTREVIEW', namen: ['kit:nightreview'] },
    { sache: 'NIGHTISSUES', namen: ['kit:nightissues'] },
    { sache: 'LAUF_ABGEBROCHEN', namen: ['lauf:abgebrochen', 'run:aborted'] },
    { sache: 'LAUF_LAEUFT', namen: ['lauf:laeuft', 'run:running'] },
    { sache: 'LAUF_WARTET', namen: ['lauf:wartet', 'run:waiting'] },
    { sache: 'REVIEW_OFFEN', namen: ['review:offen'] },
    { sache: 'REVIEW_FERTIG', namen: ['review:fertig', 'review:done'] },
    { sache: 'ZIEL_PLAN', namen: ['ziel:plan', 'goal:plan'] },
    { sache: 'ZIEL_PAKETE', namen: ['ziel:pakete', 'goal:packages'] },
    { sache: 'ZIEL_UMSETZUNG', namen: ['ziel:umsetzung', 'goal:implementation'] },
    { sache: 'ZIEL_PUSH_VORBEREITET', namen: ['ziel:push-vorbereitet', 'goal:push-prepared'] },
    { sache: 'PLANREVIEW_1', namen: ['planreview:1'] },
    { sache: 'PLANREVIEW_2', namen: ['planreview:2'] },
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
