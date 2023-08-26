package io.akenza.examples.mqtt;

public enum MqttQos {
    /**
     * Quality of Service 0 - indicates that a message should be delivered at most once (zero or one times).
     * The message will not be persisted to disk, and will not be acknowledged across the network.
     * This QoS is the fastest, but should only be used for messages which are not valuable -
     * note that if the server cannot process the message (for example, there is an authorization problem),
     * then an MqttCallback.deliveryComplete(IMqttDeliveryToken). Also known as "fire and forget".
     **/
    QOS_0(0),
    /**
     * Quality of Service 1 - indicates that a message should be delivered at least once (one or more times).
     * The message can only be delivered safely if it can be persisted, so the application must supply a means of
     * persistence using MqttConnectOptions. If a persistence mechanism is not specified, the message will not be
     * delivered in the event of a client failure. The message will be acknowledged across the network.
     * This is the default QoS.
     **/
    QOS_1(1),
    /**
     * Quality of Service 2 - indicates that a message should be delivered once.
     * The message will be persisted to disk, and will be subject to a two-phase acknowledgement across the network.
     * The message can only be delivered safely if it can be persisted, so the application must supply a means of
     * persistence using MqttConnectOptions. If a persistence mechanism is not specified,
     * the message will not be delivered in the event of a client failure.
     */
    QOS_2(2);

    private final int qos;


    MqttQos(int qos) {
        this.qos = qos;
    }

    public int getQos() {
        return qos;
    }
}
