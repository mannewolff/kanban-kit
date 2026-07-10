# Spezifikation Rollen und Rechte

## Rollen
Es gibt drei Ebenen von Rollen:
1. Die erste Ebene ist die Systemebene. Dort gibt es Admin und User. Jeder User kann zum Admin gemacht werden, aber nur von einem anderen Admin.
2. Die zweite Ebene sind fertige Rollen, die mit fertigen Rechten versehen sind. Diese Rollen sind Viewer, Member, Admin und Owner.
3. Die dritte Ebene sind zusätzliche Rollen, die ein Admin einrichten kann (Ver 2.0)

## Rechte

Die Rechte für die Standardrollen sind statisch:
- Viewer können Boards und Karten lesen.
- Member können zusätzlich Karten und Epics anlegen, verschieben und löschen, Kommentare schreiben und Anhänge hochladen.

In Version 2.0 im Moment noch nicht umsetzen, können dann weitere Rollen konfiguriert werden vom Admin und mit weiteren Rechten versehen werden

Rechte werden mit CRUD konfiguriert. Diese Rechte gibt es auf verschiedene Ebenen. Mitglieder sind Menschen, die zu einem Board hinzugefügt werden. Das können neben Admins auch Menschen mit der Rolle Owner machen. Also, wenn ich in einem Projekt Demo hinzugefügt habe und die Rolle Owner habe, dann kann ich weitere Mitglieder einladen.

Das heißt, von Anfang an gibt es diese Matrix:

- Board: Create, Read, Update, Delete
- Epics: Create, Read, Update, Delete
- Tickets: Create, Read, Update, Delete
- Kommentare: nur Create und Read
- Anhänge: Create, Read und Delete
- Karten verschieben  

Für die Standardrollen sind die dann dementsprechend gesetzt, und für zusätzliche Rollen kann man das dann ab 2.0 anlegen.

Benutzer           Bord               Epics            Tickets       Kommentare       Anhänge       Karten Verschieben
               C   R   U   D      C   R   U   D     C   R   U   D        C   R       C   R   D 
Rolle         [ ] [ ] [ ] [ ]    [ ] [ ] [ ] [ ]   [ ] [ ] [ ] [ ]      [ ] [ ]     [ ] [ ] [ ]            [ ] 


## Projekte

Projekte werden ausschließlich vom Admin angelegt. Admins können die Projekte auch löschen. Es gibt eine Sicherheitsabfrage, und wenn ein Projekt gelöscht wird, werden alle zugehörigen EPICs, Tickets und Boards mitgelöscht.

## Boards
Owner können Boards anlegen.Wohner können Boards auch löschen. Es gibt dann eine Sicherheitsfrage, wenn ein Board gelöscht wird und alle zugehörigen EPICS und Tickets mit gelöscht werden.

## Tickets/Epics

Member können Tickets anlegen, verschieben und löschen. Member können auch Epics anlegen, verschieben und löschen. Ab Member-Ebene können Kommentare erzeugt werden und Items auf dem Board verschoben werden.