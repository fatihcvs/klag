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
runtime. For a local process or container, set the Kafka client properties
through Klag's environment variables. In a Bash shell, enter both credentials
without echoing them or putting their values in shell history:

```bash
export KAFKA_BOOTSTRAP_SERVERS="<bootstrap-server>:9092"
export KAFKA_SECURITY_PROTOCOL="SASL_SSL"
export KAFKA_SASL_MECHANISM="PLAIN"
read -r -s -p "Kafka API key: " KAFKA_API_KEY; printf '\n'
read -r -s -p "Kafka API secret: " KAFKA_API_SECRET; printf '\n'
export KAFKA_SASL_JAAS_CONFIG="org.apache.kafka.common.security.plain.PlainLoginModule required username='$KAFKA_API_KEY' password='$KAFKA_API_SECRET';"
unset KAFKA_API_KEY KAFKA_API_SECRET
```

The same exported variables work with Docker. Pass the **variable name** for
the JAAS config so its value is absent from the `docker run` arguments:

```bash
docker run --rm \
  -p 8888:8888 \
  -e KAFKA_BOOTSTRAP_SERVERS \
  -e KAFKA_SECURITY_PROTOCOL \
  -e KAFKA_SASL_MECHANISM \
  -e KAFKA_SASL_JAAS_CONFIG \
  -e METRICS_REPORTER=prometheus \
  themoah/klag:latest
```

The JAAS config is still visible to anyone with access to the process or
Docker container environment. Restrict that access, and clear the exported
variable with `unset KAFKA_SASL_JAAS_CONFIG` when finished. For a long-lived
deployment, use your platform's secret manager.

## Helm with an existing Secret

The chart's `kafka.existingSecret` option reads the `jaas-config` key from an
existing Kubernetes Secret. With the JAAS variable set as above, create the
Secret from standard input on a Unix-like system; the value does not appear
in the `kubectl` arguments or a local credentials file:

```bash
printf '%s' "$KAFKA_SASL_JAAS_CONFIG" | \
  kubectl create secret generic klag-kafka --from-file=jaas-config=/dev/stdin
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
