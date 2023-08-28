package io.akenza.examples.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

/**
 * Java example of connecting to akenza via MQTT, using a JWT.
 * <p>
 * inspired by: https://github.com/GoogleCloudPlatform/java-docs-samples/blob/dcf2452680b4715b69e0d5617d37b8399ab83c27/iot/api-client/manager/src/main/java/com/example/cloud/iot/examples/MqttExample.java
 */
public class MqttExample {
    private static MqttCallback mCallback;
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        MqttCommandLineOptions options = MqttCommandLineOptions.fromFlags(args);
        if (options == null) {
            // could not parse options
            System.exit(-1);
        }

        try (var client = connect(options)) {
            String topic = String.format("/up/device/id/%s", options.deviceId);

            // publish numMessages messages to the MQTT broker, at a rate of 1 message per second
            for (int i = 1; i <= options.numMessages; ++i) {
                Map<String, Object> payload = Map.of("temperature", 12 * i);
                MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(payload));
                System.out.format("Publishing message %d/%d: to topic '%s'  '%s'%n", i, options.numMessages, topic, payload);

                //TODO implement token refresh
                //TODO implement retry on failure

                // publish "payload" to the MQTT topic. qos=1 means at least once delivery
                message.setQos(MqttQos.QOS_1.getQos());
                client.publish(topic, message);

                // send telemetry events every second
                Thread.sleep(1000);
            }

            // wait for downlinks (configuration / commands) to arrive
            for (int i = 1; i <= options.waitTimeSeconds; ++i) {
                System.out.print('.');
                Thread.sleep(1000);
            }

            // disconnect the client if still connected
            if (client.isConnected()) {
                client.disconnect();
            }

            System.out.println("Finished loop successfully. Goodbye!");
        } catch (MqttException | InterruptedException | IOException | JOSEException ex) {
            System.out.println(ex.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Create a JWT for the given device id, signed with the given RSA private key.
     */
    private static String createJwtRS(String privateKeyFile, String audience, String deviceId, int tokenExpMinutes) throws JOSEException, IOException {
        String keyString = Files.readString(Paths.get(privateKeyFile));
        JWK jwk = JWK.parseFromPEMEncodedObjects(keyString);
        RSAKey rsaKey = jwk.toRSAKey();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(deviceId)
                .audience(String.format("https://%s/devices/%s", audience, deviceId))
                .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(tokenExpMinutes))))
                .issueTime(Date.from(Instant.now()))
                .build();

        var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                //TODO if multiple certificates are in use, provide the fingerprint
                //.keyID(getFingerPrint(publicKeyPEM))
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claimsSet);

        JWSSigner signer = new RSASSASigner(rsaKey);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }


    /**
     * Create a JWT for the given device id, signed with the given elliptic curve private key.
     */
    private static String createJwtES(String privateKeyFile, String audience, String deviceId, int tokenExpMinutes) throws JOSEException, IOException {
        String keyString = Files.readString(Paths.get(privateKeyFile));
        JWK jwk = JWK.parseFromPEMEncodedObjects(keyString);
        ECKey ecKey = jwk.toECKey();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(deviceId)
                .audience(String.format("https://%s/devices/%s", audience, deviceId))
                .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(tokenExpMinutes))))
                .issueTime(Date.from(Instant.now()))
                .build();

        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                //TODO if multiple certificates are in use, provide the fingerprint
                //.keyID(getFingerPrint(publicKeyPEM))
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claimsSet);

        JWSSigner signer = new ECDSASigner(ecKey);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    /**
     * Connect to the MQTT broker
     */
    private static MqttClient connect(MqttCommandLineOptions options) throws MqttException, InterruptedException, IOException, JOSEException {
        final String mqttServerAddress = String.format("ssl://%s:%s", options.mqttHostname, options.mqttPort);
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

        Properties sslProps = new Properties();
        sslProps.setProperty("com.ibm.ssl.protocol", "TLSv1.2");
        connectOptions.setSSLProperties(sslProps);

        connectOptions.setUserName("unused");

        var password = switch (options.algorithm) {
            case "RS256" ->
                    createJwtRS(options.privateKeyFile, options.audience, options.deviceId, options.tokenExpMinutes);
            case "ES256" ->
                    createJwtES(options.privateKeyFile, options.audience, options.deviceId, options.tokenExpMinutes);
            default -> throw new IllegalArgumentException(
                    "Invalid algorithm: " + options.algorithm + ". Should be one of 'RS256' or 'ES256'.");
        };

        connectOptions.setPassword(password.toCharArray());

        // both connect and publish operations may fail. If they do, allow retries but with an
        // exponential backoff time period.
        long initialConnectIntervalMillis = 500L;
        long maxConnectIntervalMillis = 6000L;
        long maxConnectRetryTimeElapsedMillis = 900000L;
        float intervalMultiplier = 1.5f;

        long retryIntervalMs = initialConnectIntervalMillis;
        long totalRetryTimeMs = 0;

        var client = new MqttClient(mqttServerAddress, options.deviceId, new MemoryPersistence());
        while ((totalRetryTimeMs < maxConnectRetryTimeElapsedMillis) && !client.isConnected()) {
            try {
                client.connect(connectOptions);
            } catch (MqttException ex) {
                int reason = ex.getReasonCode();

                // if the connection is lost or if the server cannot be connected, allow retries, but with
                // exponential backoff.
                System.out.println("An error occurred: " + ex.getMessage());
                if (reason == MqttException.REASON_CODE_CONNECTION_LOST
                        || reason == MqttException.REASON_CODE_SERVER_CONNECT_ERROR) {
                    System.out.println("Retrying in " + retryIntervalMs / 1000.0 + " seconds.");
                    Thread.sleep(retryIntervalMs);
                    totalRetryTimeMs += retryIntervalMs;
                    retryIntervalMs *= intervalMultiplier;
                    if (retryIntervalMs > maxConnectIntervalMillis) {
                        retryIntervalMs = maxConnectIntervalMillis;
                    }
                } else {
                    throw ex;
                }
            }
        }

        subscribe(client, options.deviceId);

        return client;
    }

    /**
     * Attaches a callback used when configuration changes occur and other downlinks are received
     */
    private static void subscribe(MqttClient client, String deviceId) throws MqttException {
        mCallback =
                new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        // do nothing
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        try {
                            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {
                            });
                            System.out.println("Payload : " + payload);
                            // TODO: Insert your handling of commands and configuration message here
                        } catch (IOException ex) {
                            System.err.println("could not parse message: " + ex.getMessage());
                        }
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        // do nothing
                    }
                };

        // NOTE: subscribe to /down/device/id/%s/# for all messages
        String commandTopic = String.format("/down/device/id/%s/commands", deviceId);
        System.out.println(String.format("Listening on %s", commandTopic));

        String configTopic = String.format("/down/device/id/%s/config", deviceId);
        System.out.println(String.format("Listening on %s", configTopic));

        client.subscribe(configTopic, 1);
        client.subscribe(commandTopic, 1);
        client.setCallback(mCallback);
    }
}