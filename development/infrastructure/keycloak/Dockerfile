FROM quay.io/keycloak/keycloak:24.0.3

ADD ./dependency /opt/keycloak/providers
ADD ./exports /opt/keycloak/data/import

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "-v", "start-dev", "--import-realm"]
