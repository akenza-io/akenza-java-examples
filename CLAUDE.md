# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

A small set of Java examples demonstrating how to interact with the akenza IoT platform APIs. Currently contains a single example: connecting to akenza via MQTT using JWT-based device authentication. There is no test suite.

Related docs (for context, not part of this repo):
- Product docs: https://docs.akenza.io/
- REST API docs: https://docs.api.akenza.io/
- akenza Java client: https://github.com/akenza-io/akenza-java-client/

## Build and run

Requires JDK 26 (`maven.compiler.source`/`target` are set to 26 in `pom.xml`).

Compile:
```
mvn compile
```

Run the MQTT example (via the `mqtt` Maven profile, activated by `-Dmqtt`):
```
mvn exec:exec -Dmqtt \
    -Ddevice_id=<deviceId> \
    -Dalgorithm=RS256|ES256 \
    -Dprivate_key_file="./keys/rsa_private.pem"
```

Additional optional flags are passed as full `-Dprop=-flag=value` pairs because the `exec-maven-plugin` `<arguments>` list in `pom.xml` substitutes each property directly as a raw CLI argument (empty if unset):
```
    -Dnmsg=-num_messages=10 \
    -Dexp=-token_exp_minutes=60 \
    -Dmhn=-mqtt_hostname=mqtt.akenza.io \
    -Dmp=-mqtt_port=8883 \
    -Dwt=-wait_time_seconds=300 \
    -Daud=-audience=akenza.io
```
For private cloud customers, `-Daud=-audience=<customer>.akenza.io` must be set.

Generating device credentials (self-signed, into the gitignored `./keys/` directory) is documented in `README.md`, covering both plain self-signed RSA/EC X.509 certs and a CA-signed RSA flow. The corresponding public certificate must be uploaded when creating the device in akenza (data flow with an MQTT device connector using `Device credentials` auth, algorithm `RSA-256 x.509` or `EC-256 x.509`).

There are no tests and no lint/format tooling configured in this project.

## Architecture

Everything lives under the single package `io.akenza.examples.mqtt`:

- **`MqttCommandLineOptions`** — parses CLI flags with Apache Commons CLI into a plain options object (device id, private key file, algorithm, and optional overrides with defaults: 100 messages, 60 min token expiry, `mqtt.akenza.io:8883`, 300s wait, `akenza.io` audience).
- **`MqttExample`** — the entry point and all the connection/auth/pub-sub logic:
  - Builds a JWT (`RS256` via `createJwtRS`/RSA key, or `ES256` via `createJwtES`/EC key) from the PEM private key using Nimbus JOSE+JWT, with `sub=deviceId`, `aud=https://{audience}/devices/{deviceId}`, and expiry `tokenExpMinutes` from now.
  - Connects over TLS (`ssl://host:port`) with Eclipse Paho's `MqttClient` (MQTT 3.1.1), using an unused username and the JWT as the password — this is akenza's device-credentials auth scheme. Connection retries with exponential backoff (500ms → up to 6s, capped at 15 min total) on `REASON_CODE_CONNECTION_LOST`/`REASON_CODE_SERVER_CONNECT_ERROR`.
  - Subscribes to downlink topics `/down/device/id/{deviceId}/commands` and `/down/device/id/{deviceId}/config` before publishing anything.
  - Publishes `numMessages` sample JSON telemetry payloads (via Jackson `JsonMapper`, `tools.jackson.core` package — note this is Jackson 3.x, not the classic `com.fasterxml.jackson`) to uplink topic `/up/device/id/{deviceId}` at 1 msg/sec, QoS 1.
  - Then idles for `waitTimeSeconds` to receive downlink messages before disconnecting.
  - Known gaps marked with `TODO` in the source: no JWT refresh on expiry, no publish-retry-on-failure, no multi-certificate key-fingerprint handling.
- **`MqttQos`** — thin enum wrapper (`QOS_0`/`QOS_1`/`QOS_2`) around Paho's integer QoS levels.

Notable dependency choices to be aware of when editing:
- `org.eclipse.paho.client.mqttv3` is the client actually used, even though `mqttv5.client` is also a declared dependency.
- The Eclipse Paho releases repository (`https://repo.eclipse.org/content/repositories/paho-releases/`) is added explicitly in `pom.xml`.
- Jackson comes from the `tools.jackson.core` groupId (Jackson 3), not `com.fasterxml.jackson.core`.
