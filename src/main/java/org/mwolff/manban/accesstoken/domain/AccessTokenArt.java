package org.mwolff.manban.accesstoken.domain;

/** Art eines {@link AccessToken} (Plan #2100, E1). */
public enum AccessTokenArt {
  /** Gewöhnliches Token, gebunden oder ungebunden. */
  STANDARD,
  /** Board-gebundener Zugang der Desktop-App; je Board höchstens einer wirksam. */
  APP
}
