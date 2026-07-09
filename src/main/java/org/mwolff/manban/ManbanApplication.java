package org.mwolff.manban;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Einstiegspunkt der manban-Anwendung.
 *
 * <p>manban ist eine eigenständige, self-hostbare Kanban-Board-Anwendung
 * (Trello-Alternative). Aufbau nach hexagonaler Architektur je Fachmodul,
 * siehe {@code package-info.java} im Basispaket.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ManbanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManbanApplication.class, args);
    }
}
