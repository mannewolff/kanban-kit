Übernahme aus dem Prüfungslauf.

`decision` trägt einen Wert außerhalb von `angenommen | abgelehnt`. Ein Protokoll ohne
entscheidbaren Ausgang hält nichts fest.

```pruefbefund-vorschlaege
{"formatVersion":1,"runId":"1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d","proposals":[{"proposalId":"p1","target":{"kind":"TEXT_SPAN"},"expectedText":"der Betreuer","replacementText":"die zuständige Person","findingRef":"WICHTIG 1"}]}
```

```pruefbefund-protokoll
{"formatVersion":1,"runId":"1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d","entries":[{"proposalId":"p1","decision":"vielleicht"}]}
```
