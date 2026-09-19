# SensorThingsProcessor [![Build Status](https://github.com/FraunhoferIOSB/SensorThingsProcessor/workflows/Maven%20Build/badge.svg)](https://github.com/FraunhoferIOSB/SensorThingsProcessor/actions)
Automatic processors for the OGC SensorThings API

## Configuring

Start the jar with no options to open the configuration GUI.
```
java -jar SensorThingsProcessor-0.10-jar-with-dependencies.jar
```


## Running

The Processor takes the following command line options.
```
-noact -n :
    Read the file and give output, but do not actually post observations.

-config -c [file path] :
    The path to the config json file.

-daemon -d :
    Run in daemon mode, not listening for 'Enter' to exit.

-online -o :
    Run in on-line mode, listening for changes and processing as needed.
```

Start the Processor with no options to open the configuration GUI.

## Authenticating on the SensorThings service

The `authMethod` of a service (`de.fraunhofer.iosb.ilt.stp.sta.Service`) is one of:

- `AuthNone`: anonymous.
- `AuthBasic`: HTTP basic auth (`username`, `password`, `ignoreSslErrors`), for a FROST-Server on its BasicAuthProvider.
- `AuthPostCookie`: form login, cookie session.
- `AuthOidcClientCredentials`: OpenID Connect service account (OAuth 2.0 client credentials grant), for a FROST-Server on its Keycloak auth provider. The access token is fetched from `tokenUrl` with `clientId` / `clientSecret`, cached and renewed 30 s before it expires, and sent as a bearer token on every HTTP request. The MQTT connection stays anonymous.

```json
"authMethod": {
  "className": "de.fraunhofer.iosb.ilt.stp.sta.AuthOidcClientCredentials",
  "classConfig": {
    "tokenUrl": "https://keycloak.example.org/realms/frost/protocol/openid-connect/token",
    "clientId": "frost-processor",
    "clientSecret": "...",
    "ignoreSslErrors": false
  }
}
```
