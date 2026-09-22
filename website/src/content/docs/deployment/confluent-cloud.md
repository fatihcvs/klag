---
title: Confluent Cloud
description: Connect Klag to Confluent Cloud with SASL_SSL, a Kafka API key, and Helm-managed Kubernetes secrets.
---

Klag connects to Confluent Cloud with the standard Kafka client settings:
`SASL_SSL`, the `PLAIN` mechanism, and a Kafka API key/secret. Create the API key
for the Kafka cluster you want to monitor, then grant the service account the
read-only ACLs described in [ACL Permissions](/kafka/acl-permissions/).

## Connection settings

Get the bootstrap server from the Confluent Cloud Console under **Cluster >
Clients**. Keep the API key and secret outside source control and inject them at
runtime.

For a local process or container, set the Kafka client properties through Klag's
environment variables:

```bash
export KAFKA_BOOTSTRAP_SERVERS="<bootstrap-server>:9092"
export KAFKA_SECURITY_PROTOCOL="SASL_SSL"
export KAFKA_SASL_MECHANISM="PLAIN"
export KAFKA_SASL_JAAS_CONFIG="org.apache.kafka.common.security.plain.PlainLoginModule required username='<kafka-api-key>' password='<kafka-api-secret>';"
```

The same variables work with Docker:

```bash
docker run --rm \
  -e KAFKA_BOOTSTRAP_SERVERS="<bootstrap-server>:9092" \
  -e KAFKA_SECURITY_PROTOCOL="SASL_SSL" \
  -e KAFKA_SASL_MECHANISM="PLAIN" \
  -e KAFKA_SASL_JAAS_CONFIG="org.apache.kafka.common.security.plain.PlainLoginModule required username='<kafka-api-key>' password='<kafka-api-secret>';" \
  -e METRICS_REPORTER=prometheus \
  themoah/klag:latest
```

The placeholders above are examples only. Prefer your deployment platform's
secret manager or injected environment variables instead of putting real
credentials in shell history, manifests, or Git.

## Helm with an existing Secret

The chart's `kafka.existingSecret` option reads the `jaas-config` key from an
existing Kubernetes Secret. Create it with the JAAS value assembled from your
Confluent Cloud API key and secret:

```bash
kubectl create secret generic klag-kafka \
  --from-literal=jaas-config="org.apache.kafka.common.security.plain.PlainLoginModule required username='<kafka-api-key>' password='<kafka-api-secret>';"
```

Install or upgrade Klag with the cluster connection settings and Secret reference:

```bash
helm upgrade --install klag klag/klag \
  --set kafka.bootstrapServers="<bootstrap-server>:9092" \
  --set kafka.securityProtocol="SASL_SSL" \
  --set kafka.saslMechanism="PLAIN" \
  --set kafka.existingSecret="klag-kafka"
```

For a values file, use the equivalent configuration:

```yaml
kafka:
  bootstrapServers: "<bootstrap-server>:9092"
  securityProtocol: SASL_SSL
  saslMechanism: PLAIN
  existingSecret: klag-kafka
```

If your Secret uses a different key, set `kafka.secretKeys.jaasConfig` to that
key. The chart injects the value as `KAFKA_SASL_JAAS_CONFIG`; no API key or secret
is rendered into the Deployment manifest.

As an alternative, inject the value with the chart's generic `extraEnv` escape
hatch and a Secret reference:

```yaml
kafka:
  bootstrapServers: "<bootstrap-server>:9092"
  securityProtocol: SASL_SSL
  saslMechanism: PLAIN

extraEnv:
  - name: KAFKA_SASL_JAAS_CONFIG
    valueFrom:
      secretKeyRef:
        name: klag-kafka
        key: jaas-config
```

Use either `kafka.existingSecret` or `extraEnv` for the JAAS value, not both.

## Verify the connection

Check that the pod is running and that Klag can reach Kafka:

```bash
kubectl get pods -l app.kubernetes.io/instance=klag
kubectl port-forward svc/klag 8888:8888
curl http://localhost:8888/readyz
```

The `/readyz` endpoint returns HTTP `200` when at least one configured Kafka
cluster is reachable and `503` when all configured clusters are unavailable.
Authorization failures are reported in the Klag logs; check those logs before
changing ACLs or rotating credentials.
