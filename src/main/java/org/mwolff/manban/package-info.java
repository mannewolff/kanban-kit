/**
 * Basispaket der manban-Anwendung.
 *
 * <h2>Architektur-Konvention (hexagonal, je Fachmodul)</h2>
 * Jedes Fachmodul (z. B. {@code auth}, {@code project}, {@code board}) gliedert sich in:
 * <ul>
 *   <li>{@code domain} — Aggregate, Wertobjekte, fachliche Invarianten. Keine Framework-Abhängigkeit.</li>
 *   <li>{@code application} — Use-Cases und Ports (Interfaces), die die Domäne orchestrieren.</li>
 *   <li>{@code web} — eingehende Adapter: REST-Controller, DTOs, Request-/Response-Mapping.</li>
 *   <li>{@code infrastructure} — ausgehende Adapter: JPA-Repositories, Mail, Objektspeicher, Crypto.</li>
 * </ul>
 * Die Abhängigkeitsrichtung zeigt stets nach innen: {@code web}/{@code infrastructure} → {@code application} → {@code domain}.
 * Dieses Skelett (Issue F1) legt nur die Basis; die Fachmodule entstehen mit den Feature-Issues.
 */
package org.mwolff.manban;
