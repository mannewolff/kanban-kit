**WICHTIG 1 — Das Beispiel im Issue ist nicht mehr aktuell.** Der Vorschlagsblock sieht heute so
aus:

````text
```pruefbefund-vorschlaege
{"formatVersion":1,"runId":"6f1c3a2e-9d4b-4d1f-8e77-2b0a5c9d1e34","proposals":[{"proposalId":"p1","target":{"kind":"TEXT_SPAN"},"expectedText":"der Betreuer","replacementText":"die zuständige Person","findingRef":"WICHTIG 1"}]}
```
````

Die Kennzeichnung steht hier innerhalb eines umschließenden Codeblocks und ist deshalb **kein**
Vertragsblock: Der Kommentar erklärt das Format, er wendet es nicht an. Ohne diese Regel würde jede
Dokumentation über den Vertrag beim Lesen zum Vertrag.
