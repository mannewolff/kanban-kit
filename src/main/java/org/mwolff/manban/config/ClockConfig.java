package org.mwolff.manban.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stellt eine {@link Clock} als Bean bereit, damit zeitabhängige Use-Cases die Uhr per Konstruktor
 * injiziert bekommen (statt direkt auf die System-Uhr zuzugreifen) und so mit einer fixierten Clock
 * deterministisch testbar sind. In Produktion die System-UTC-Uhr — verhaltensgleich zum bisherigen
 * direkten Zeitzugriff.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
