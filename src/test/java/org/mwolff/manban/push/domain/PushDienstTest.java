package org.mwolff.manban.push.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Erlaubnisliste der Push-Dienste (Issue #2290): Ein Abo darf nur auf den Push-Dienst eines
 * Browsers zeigen, nie auf ein Ziel im Netz des Servers.
 */
class PushDienstTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://fcm.googleapis.com/fcm/send/abc:def",
        "https://updates.push.services.mozilla.com/wpush/v2/gAAAAA",
        "https://web.push.apple.com/QGuQyavXutnMH",
        "https://wns2-db5p.notify.windows.com/w/?token=BQYAAA",
        "https://FCM.googleapis.com/fcm/send/gross",
        "https://fcm.googleapis.com:443/fcm/send/mit-port"
      })
  void dieDiensteDerBrowser_sindErlaubt(String endpunkt) {
    assertThat(PushDienst.erlaubt(endpunkt)).isTrue();
    assertThat(PushDienst.erlaubt(URI.create(endpunkt))).isTrue();
  }

  /** Der Angriff aus dem Befund: Ziele im Netz des Servers, wie sie das Format zuließ. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://127.0.0.1:8443/internal",
        "https://10.0.0.1/admin",
        "https://192.168.1.1/",
        "https://169.254.169.254/latest/meta-data/",
        "https://localhost/",
        "https://[::1]/",
        "https://intern.example/hook"
      })
  void zieleImServernetz_sindNichtErlaubt(String endpunkt) {
    assertThat(PushDienst.erlaubt(endpunkt)).isFalse();
  }

  /** Umgehungen: falsches Schema, fremder Port, Zugangsdaten, ähnlich klingende Hosts. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://fcm.googleapis.com/fcm/send/abc",
        "https://fcm.googleapis.com:8443/fcm/send/abc",
        "https://fcm.googleapis.com@127.0.0.1/fcm/send/abc",
        "https://nutzer@fcm.googleapis.com/fcm/send/abc",
        "https://evilfcm.googleapis.com/fcm/send/abc",
        "https://googleapis.com/fcm/send/abc",
        "https://fcm.googleapis.com.evil.example/fcm/send/abc",
        "https://push.apple.com.evil.example/x",
        "https://fcm.googleapis.com./fcm/send/abc",
        "fcm.googleapis.com/fcm/send/abc",
        "https:///ohne-host",
        "https://fcm googleapis.com/kein-uri"
      })
  void umgehungen_sindNichtErlaubt(String endpunkt) {
    assertThat(PushDienst.erlaubt(endpunkt)).isFalse();
  }

  @Test
  void einRelativerEndpunkt_istNichtErlaubt() {
    assertThat(PushDienst.erlaubt(URI.create("/fcm/send/abc"))).isFalse();
  }
}
