# akenza-java-examples

A set of Java examples for using the `akenza` APIs.

Also refer to the [product documentation](https://docs.akenza.io/), the documentation of
the [REST API](https://docs.api.akenza.io/) and
the [akenza Java client](https://github.com/akenza-io/akenza-java-client/).

## MQTT Example

This example connects to akenza via MQTT with the [Eclipse Paho Java Client](https://github.com/eclipse/paho.mqtt.java)
using a JWT for [device authentication](https://docs.akenza.io/akenza.io/api-reference/device-security). After
connecting, the client subscribes to the downlink topic and 100 messages are published to the device's MQTT uplink topic
at a rate of one message per second.

To run this example,
first [create your credentials](https://docs.akenza.io/akenza.io/tutorials/using-device-credentials/creating-public-private-key-pairs)
and register your device in akenza.

In akenza:

- Create a new data flow with an MQTT device connector using `Device credentials` as the authentication method.
- Create a device with this data flow, select the algorithm `RSA-256 x.509` or `EC-256 x.509` and upload/paste the
  certificate.

After you have generated your credentials and created the device in akenza, compile and run this example with the
corresponding algorithm flag.

NOTE: always keep your private key secret.

### Using a Self-Signed RSA X509 Certificate

Generate the private key and certificate:

```
openssl req -x509 -nodes -newkey rsa:2048 -keyout ./keys/rsa_private.pem -out ./keys/rsa_cert.pem -subj "/CN=<deviceId>"
```

Run the example:

```
mvn compile
mvn exec:exec -Dmqtt \
    -Ddevice_id=<deviceId> \
    -Dalgorithm=RS256 \
    -Dprivate_key_file="../path/to/your_rsa_key"
```

A full example:

```
mkdir keys
openssl req -x509 -nodes -newkey rsa:2048 -keyout ./keys/rsa_private.pem -out ./keys/rsa_cert.pem -subj "/CN=965200347BC53111"
pbcopy < ./keys/rsa_cert.pem
# create the device in akenza and paste the certificate
mvn compile
mvn exec:exec -Dmqtt \
    -Ddevice_id=965200347BC53111 \
    -Dalgorithm=RS256 \
    -Dprivate_key_file="./keys/rsa_client_key.key"
```

### Using a Self-Signed EC X509 Certificate

Generate the private key and certificate:

```
openssl ecparam -genkey -name prime256v1 -noout -out ./keys/ec_private.pem 
openssl req -x509 -new -key ./keys/ec_private.pem -out ./keys/ec_cert.pem -subj "/CN=<deviceId>"
```

Run the example:

```
mvn compile
mvn exec:exec -Dmqtt \
    -Ddevice_id=<deviceId> \
    -Dalgorithm=ES256 \
    -Dprivate_key_file="../path/to/your_rsa_key"
```

A full example:

```
mkdir keys
openssl ecparam -genkey -name prime256v1 -noout -out ./keys/ec_private.pem 
openssl req -x509 -new -key ./keys/ec_private.pem -out ./keys/ec_cert.pem -subj "/CN=D5C30777E0A6ED9B"
pbcopy < ./keys/ec_cert.pem
# create the device in akenza and paste the certificate
mvn compile
mvn exec:exec -Dmqtt \
    -Ddevice_id=D5C30777E0A6ED9B \
    -Dalgorithm=ES256 \
    -Dprivate_key_file="./keys/ec_private.pem"
```

More configuration options can be set with:

```
mvn exec:exec -Dmqtt \
    -Ddevice_id=<deviceId> \
    -Dalgorithm=RS256|ES256 \
    -Dprivate_key_file="../path/to/your_rsa_key" \
    -Dexp=-token_exp_minutes=60 \
    -Dmhn=-mqtt_hostname=mqtt.akenza.io \
    -Dmp=-mqtt_port=1883|8883 \
    -Dwt=-wait_time_seconds=300 \
    -Daud=-audience=akenza.io
```

For private cloud customers, the audience has to be changed accordingly (e.g. `<customer>.akenza.io`).

### Using a Self-Signed RSA X509 Certificate with a Certificate Authority

Create a Certificate Authority (CA) certificate (this only needs to be done once):

```
mkdir keys
openssl genrsa -out ./keys/rsa_ca_key.key 2048
openssl req -x509 -new -nodes \
    -key ./keys/rsa_ca_key.key \
    -sha256 -days 1024 \
    -out ./keys/rsa_ca_cert.pem
```

Submit the CA certificate (`rsa_ca_cert.pem`) to akenza. Keep the CA private key (`rsa_ca_key.key`) local and secret —
it is used to sign each device's certificate below.

For each device, generate a device private key, create a CSR, and sign it with the CA to produce the device
certificate:

```
openssl genrsa -out ./keys/rsa_client_key.key 2048

# Create a CSR for the device certificate
openssl req -new \
    -subj "/CN=<deviceId>" \
    -key ./keys/rsa_client_key.key \
    -out ./keys/rsa_client_cert_csr.csr

# Sign the CSR with the CA to produce the device certificate
openssl x509 -req \
    -in ./keys/rsa_client_cert_csr.csr \
    -CA ./keys/rsa_ca_cert.pem \
    -CAkey ./keys/rsa_ca_key.key \
    -CAcreateserial \
    -out ./keys/rsa_client_cert.pem \
    -days 500 -sha256
```

Run the example:

```
mvn compile
mvn exec:exec -Dmqtt \
    -Ddevice_id=<deviceId> \
    -Dalgorithm=RS256 \
    -Dprivate_key_file="./keys/rsa_client_key.key"
```

A full example:

```
mkdir keys
openssl genrsa -out ./keys/rsa_ca_key.key 2048
openssl req -x509 -new -nodes \
    -key ./keys/rsa_ca_key.key \
    -sha256 -days 1024 \
    -out ./keys/rsa_ca_cert.pem
# submit ./keys/rsa_ca_cert.pem to akenza

openssl genrsa -out ./keys/rsa_client_key.key 2048
openssl req -new \
    -subj "/CN=E92938F95FBDF0FD" \
    -key ./keys/rsa_client_key.key \
    -out ./keys/rsa_client_cert_csr.csr
openssl x509 -req \
    -in ./keys/rsa_client_cert_csr.csr \
    -CA ./keys/rsa_ca_cert.pem \
    -CAkey ./keys/rsa_ca_key.key \
    -CAcreateserial \
    -out ./keys/rsa_client_cert.pem \
    -days 500 -sha256

mvn compile
mvn exec:exec -Dmqtt \
    -Ddevice_id=E92938F95FBDF0FD \
    -Dalgorithm=RS256 \
    -Dprivate_key_file="./keys/rsa_client_key.key" \
    -Dexp=-token_exp_minutes=60 \
    -Dmhn=-mqtt_hostname=mqtt.akenza.io \
    -Dmp=-mqtt_port=8883 \
    -Dwt=-wait_time_seconds=300 \
    -Daud=-audience=akenza.io
```

