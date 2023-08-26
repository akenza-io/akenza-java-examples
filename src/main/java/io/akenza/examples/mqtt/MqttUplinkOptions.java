package io.akenza.examples.mqtt;

import org.apache.commons.cli.*;

/**
 * Command line options for the MQTT example.
 */
public class MqttUplinkOptions {
    static final Options options = new Options();
    String deviceId;
    String privateKeyFile;
    String algorithm;
    int numMessages = 100;
    int tokenExpMinutes = 60;
    String mqttHostname = "mqtt.akenza.io";
    int mqttPort = 8883;
    int waitTimeSeconds = 60 * 5;

    public static MqttUplinkOptions fromFlags(String... args) {
        // Required arguments
        options.addOption(
                Option.builder()
                        .type(String.class)
                        .longOpt("device_id")
                        .hasArg()
                        .desc("The physical device id.")
                        .required()
                        .build());
        options.addOption(
                Option.builder()
                        .type(String.class)
                        .longOpt("private_key_file")
                        .hasArg()
                        .desc("Path to private key file.")
                        .required()
                        .build());
        options.addOption(
                Option.builder()
                        .type(String.class)
                        .longOpt("algorithm")
                        .hasArg()
                        .desc("Encryption algorithm to use to generate the JWT. Either 'RS256' or 'ES256'.")
                        .required()
                        .build());

        // Optional arguments.
        options.addOption(
                Option.builder()
                        .type(Number.class)
                        .longOpt("num_messages")
                        .hasArg()
                        .desc("Number of messages to publish.")
                        .build());
        options.addOption(
                Option.builder()
                        .type(String.class)
                        .longOpt("mqtt_hostname")
                        .hasArg()
                        .desc("MQTT hostname (defaults to mqtt.akenza.io).")
                        .build());
        options.addOption(
                Option.builder()
                        .type(Number.class)
                        .longOpt("mqtt_port")
                        .hasArg()
                        .desc("MQTT bridge port (defaults to 8883).")
                        .build());
        options.addOption(
                Option.builder()
                        .type(Number.class)
                        .longOpt("token_exp_minutes")
                        .hasArg()
                        .desc("Minutes to JWT token refresh (token expiration time).")
                        .build());

        options.addOption(
                Option.builder()
                        .type(Number.class)
                        .longOpt("wait_time_seconds")
                        .hasArg()
                        .desc("Seconds to wait for commands or configuration changes.")
                        .build());

        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
            MqttUplinkOptions result = new MqttUplinkOptions();

            result.deviceId = commandLine.getOptionValue("device_id");
            result.privateKeyFile = commandLine.getOptionValue("private_key_file");
            result.algorithm = commandLine.getOptionValue("algorithm");

            if (commandLine.hasOption("num_messages")) {
                result.numMessages = ((Number) commandLine.getParsedOptionValue("num_messages")).intValue();
            }
            if (commandLine.hasOption("token_exp_minutes")) {
                result.tokenExpMinutes = ((Number) commandLine.getParsedOptionValue("token_exp_minutes")).intValue();
            }
            if (commandLine.hasOption("wait_time_seconds")) {
                result.waitTimeSeconds = ((Number) commandLine.getParsedOptionValue("wait_time_seconds")).intValue();
            }
            if (commandLine.hasOption("mqtt_hostname")) {
                result.mqttHostname = commandLine.getOptionValue("mqtt_hostname");
            }
            if (commandLine.hasOption("mqtt_port")) {
                result.mqttPort = ((Number) commandLine.getParsedOptionValue("mqtt_port")).shortValue();
            }
            return result;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    public String toString() {
        return options.toString();
    }
}