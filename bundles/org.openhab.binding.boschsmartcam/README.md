# Bosch Smart Home Camera Binding

This binding integrates the Bosch Smart Home Cameras of the second generation (Eyes Outdoor Camera II, Eyes Indoor Camera II) into openHAB.

It talks to each camera directly through the local API Bosch provides for these cameras.
That API is read only, so an optional Bosch SingleKey ID account can be added for what only the Bosch cloud can do: switching the privacy mode, and the push notifications of the Bosch Smart Camera app.
The binding is unrelated to the Bosch Smart Home binding, which talks to the Smart Home Controller.

## Prerequisites

The local API is switched on per camera in the Bosch Smart Camera app: _camera settings_, _More_, _Local Data Interface_.
The app then shows the user and the password for it.
Without this a camera cannot be added.

## Supported Things

- `camera`: A single camera, talked to in the local network. Works on its own.
- `account`: A Bosch SingleKey ID account. Optional bridge for cameras whose privacy mode should be switchable, and the place of the push notifications.

## Discovery

Cameras are found in the local network, an account is not needed for that.
The cameras answer neither mDNS nor WS-Discovery, so the binding probes every address of the IPv4 networks openHAB is directly attached to, up to a size of /22.
A camera is recognized by two open ports, 443 for the local API and 9554 for the RTSP tunnel, and by a certificate below the root Bosch publishes for the local API.
The tunnel only opens once the local data interface is enabled in the app, so cameras that could not be used anyway are not offered.

A scan runs when started by hand. Background discovery is off by default, because every scan probes the whole network and asks the cloud about the cameras it did not find; when enabled in the discovery settings of openHAB, it runs every 30 minutes.
It takes about ten seconds for a /22.

Cameras in a network openHAB is not attached to, for example behind a router, are found through an online `account`: after the local scan, every camera of the account that was not found is looked for at the address the cloud names for it.
That address is only a hint, the camera found there is checked the same way and has to have the MAC address the cloud names.
Without an account such a camera has to be added by hand.

A camera is identified by the MAC address it uses on the network, which is part of its certificate.
It is used as thing id, in lower case and without separators, e.g. `boschsmartcam:camera:64daa0123456`.
The id does not contain the account, so a camera keeps it when it is moved under an account later.

If an online account has the camera, the result is put under that account and labeled with the name the camera has in the app, e.g. _Front Door (Eyes Outdoor Camera II)_.
Only the password of the local API has to be entered when adding it.

## Thing Configuration

### `camera` Thing Configuration

| Name                 | Type    | Description                                                         | Default     | Required | Advanced |
|----------------------|---------|---------------------------------------------------------------------|-------------|----------|----------|
| host                 | text    | Address of the camera in the local network.                         | N/A         | yes      | no       |
| user                 | text    | User of the local API, as shown in the Bosch Smart Camera app.                   | `localuser` | yes      | no       |
| password             | text    | Password of the local API, as shown in the Bosch Smart Camera app.               | N/A         | yes      | no       |
| snapshotCacheSeconds | integer | How long a fetched image is reused in sec.                          | 3           | no       | yes      |
| accessToken          | text    | Token in the addresses of snapshot, streams and events, see [Who may fetch them](#who-may-fetch-them). | generated | no | yes |
| publishEventsApi     | boolean | Offer the events the cloud keeps as a JSON API, see below. Needs an account. | false | no  | yes      |
| trustAllCertificates | boolean | Accept any certificate instead of verifying it, see below.          | false       | no       | yes      |

The binding verifies the certificate of the camera against the root Bosch publishes for the local API.
The name in that certificate is the MAC address of the camera rather than its host name, so the host name itself is not checked.
Instead the MAC address is compared with the one the thing was created for: if another device answers at the configured address, the thing goes offline with a note saying so.

Should a firmware update ever bring a certificate below a different root, the thing goes offline saying its certificate is not trusted.
`trustAllCertificates` is the way out until the binding knows the new root: the chain is no longer verified then, the MAC address is still read and compared.

### `account` Bridge Configuration

| Name            | Type    | Description                                                              | Default | Required | Advanced |
|-----------------|---------|--------------------------------------------------------------------------|---------|----------|----------|
| refreshInterval | integer | Interval the account is polled in sec.                                    | 3600    | no       | no       |

Everything the cameras report themselves, such as the privacy mode, arrives locally within a second, and commands sent from openHAB are read back a few seconds later.
The poll only picks up what is changed in the Bosch Smart Camera app: the notifications, and cameras added to or removed from the account, which get or lose their group of notification channels.
The cloud does not tell about such changes, so a notification setting switched in the app shows up in openHAB with the next poll.
The minimum is 30 seconds.

### Binding Configuration

| Name                    | Type | Description                                                         | Default                         |
|-------------------------|------|---------------------------------------------------------------------|---------------------------------|
| snapshotAllowedNetworks | text | Networks that may fetch the snapshot URLs and the streams, as comma separated CIDR. | loopback, private and link-local ranges |
| rtspPort                | integer | Port the streams of all cameras are offered on, plain and over TLS, see below. | N/A              |

## Authorization

Bosch accepts exactly one return address for this client, `https://my.home-assistant.io/redirect/oauth`, and it does not point to openHAB.
That address belongs to the My Home Assistant service, a static page that forwards the login to the instance URL stored in the browser and appends `/auth/external/callback`.
The binding listens on that path, so pointing the instance URL at openHAB makes the authorization complete on its own.

1. Add an `account` thing. It stays offline with the note that it is not authorized yet.
1. Open `http://<youropenhab>:8080/boschsmartcam` in a browser.
1. Follow step 1 on that page: open the My Home Assistant settings and set the instance URL to the address of your openHAB, e.g. `http://192.168.178.80:8080`. The page shows the exact value and offers a button to copy it. This is stored only in that browser and is needed once.
1. Click **Log in with Bosch SingleKey ID** and log in. If you are already signed in with your SingleKey ID in that browser, no login form appears and you are forwarded straight through.
1. You land on a page titled _Link account to Home Assistant?_. Click **Link account**, not _Decline_.
   The wording is misleading: no Home Assistant is involved, the button only forwards to the instance URL shown at the bottom of that page, which is your openHAB.
   The account thing goes online.

No Home Assistant installation is involved at any point - the instance URL is just a value in the browser's local storage.
It has to be the bare openHAB address without a path: the settings page keeps only protocol and host of what is entered and drops everything else, which is why the binding serves `/auth/external/callback` and `/_my_redirect/oauth` as global paths rather than below `/boschsmartcam`.

If the instance URL cannot be set, for example because the login happens on a phone or in a private window, the page offers a fallback: copy the complete address you were redirected to and paste it into the field under _The login did not return to openHAB?_.
That page may show an error - it only carries the authorization code.

From then on the binding refreshes the access token on its own, so this procedure is only needed again if the tokens are removed or revoked.
The same page also lets you remove the stored tokens of an account, for example to authorize it with a different Bosch account.

## Channels

### `camera` Channels

| Channel               | Type     | Read/Write | Description                                                                                     |
|-----------------------|----------|------------|-------------------------------------------------------------------------------------------------|
| local#privacy-mode    | Switch   | RW         | `ON` puts the camera into privacy mode, the Eyes Indoor Camera II lowers its lens into the housing. Switching needs an account, see below. |
| local#event           | Trigger  |            | Fires once per detected event, with its kind as payload, see below.                             |
| local#last-event      | String   | R          | Kind of the event detected last.                                                                |
| local#last-event-time | DateTime | R          | When the camera detected the last event.                                                        |
| local#recording       | Switch   | R          | `ON` while the camera records the clip of an event.                                             |
| local#snapshot-url    | String   | R          | Address a still image can be fetched from. Contains a token, treat it as a secret. Only served while linked. |
| local#rtsp-url        | String   | R          | Address of the video stream through openHAB, plain RTSP without password. Contains a token.     |
| local#rtsp-substream-url | String | R          | The same for the small stream without sound, e.g. for motion detection in a video recorder.     |
| local#rtsps-url       | String   | R          | Address of the video stream through openHAB over TLS, without password. Contains a token.       |
| local#rtsps-substream-url | String | R         | The same for the small stream without sound.                                                    |
| local#camera-rtsps-url | String  | R          | Address of the video stream directly at the camera, RTSP over TLS.                              |
| light#front-light    | Switch   | RW         | Front light on or off, like its button in the app. Eyes Outdoor Camera II only, see below.      |
| light#top-bottom-light | Switch | RW         | The LEDs on top and below on or off together, like their button in the app.                    |
| light#motion-light    | Switch   | RW         | Whether the lights go on with motion, as set in the app.                                        |
| alarm#siren           | Switch   | RW         | `ON` sounds the siren until switched off or the alarm time of the app has passed.               |
| cloud#last-clip-snapshot-url | String | R      | Address of the still image of the last clip, as the cloud keeps it. Needs an account, only served while linked. |
| cloud#last-clip-url   | String   | R          | Address of the clip of the last event, once uploaded. Needs an account, only served while linked. |
| cloud#clip-ready      | Trigger  |            | Fires when the clip of an event is in the cloud, with the kind of the event as payload.          |

The camera reports what happens through an ONVIF PullPoint subscription that the binding keeps open.
The connection goes out from openHAB, so nothing has to be reachable from the camera, and nothing is polled: the camera answers when something happens.
As long as it answers, the thing is online; a failing cloud does not change that.
When the subscription breaks, the binding checks that the right camera answers at the address before it subscribes again, with a delay growing from 5 seconds up to a minute.

The `event` channel fires with `PERSON`, `INTRUSION`, `NOISE`, `GLASSBREAK`, `FIRE` or `WATER`, depending on what the camera supports.
A kind the binding does not know yet is passed on as well, named after its topic.
The camera repeats an event about every half second while it lasts; the binding passes on each event once, recognized by the id of the clip that belongs to it.
`recording` goes `ON` with the event and `OFF` when the camera reports the clip as finished, usually 15 seconds later.

The privacy mode is read from the camera, which reports every change within a second.
Switching it has to go through the cloud, because the local API cannot change anything: without an account a command is refused with a note in the log, and the switch goes back to what the camera reports.

### Lights and Alarm

The local API of the camera only reads, so lights and siren are switched through the Bosch cloud, like the privacy mode: without an account a command is refused with a note in the log.
What they do is read from the camera itself.

The `light` group appears once the account has told the binding that the camera is an Eyes Outdoor Camera II, the only model with lights.
`front-light` and `top-bottom-light` work like the buttons of the app and show `ON` while the light shines, whatever turned it on: the button, motion or dusk.
The camera reports that within a second.
Brightness and color stay as set in the app.
Like the buttons, switching off takes precedence over the ambient light: it stays off until the camera turns the ambient light on again, as Bosch describes it once a day.

The camera reports when an alarm starts or stops, whatever started it, and the binding then reads whether the siren sounds.
`motion-light` is not reported, so the binding reads it a few seconds after a command and with every poll of the account.

### `account` Channels

The push notifications are a setting of the Bosch Smart Camera app, the camera itself does not know about them.
They therefore sit on the account, in one channel group per camera of the account, whether or not that camera is a thing in openHAB.
The group is named after the MAC address of the camera, like the camera thing, e.g. `boschsmartcam:account:home:64daa0123456#notifications`, and the channels carry the name of the camera from the app.

| Channel                   | Type   | Read/Write | Description                                                                                     |
|---------------------------|--------|------------|-------------------------------------------------------------------------------------------------|
| <camera>#notifications        | Switch | RW         | App push notifications: whether the Bosch Smart Camera app notifies about this camera.                                            |
| <camera>#notifications-status | String | R          | The notification setting as the cloud reports it, e.g. `ON_CAMERA_SCHEDULE` or `OFF_OVERRIDE`. |

The notification setting is not a plain on/off in the cloud: it can also follow a schedule.
`notifications` therefore reads `ON` for everything that is not switched off.
Writing `ON` sends `FOLLOW_CAMERA_SCHEDULE` and writing `OFF` sends `ALWAYS_OFF`; the cloud then reports them as `ON_CAMERA_SCHEDULE` and `OFF_OVERRIDE`.
Use `notifications-status` to see the exact setting: `FOLLOW_CAMERA_SCHEDULE`, `FOLLOW_SCHEDULE`, `ON_CAMERA_SCHEDULE`, `OFF_CAMERA_SCHEDULE`, `OFF_OVERRIDE`, `OFF_UNTIL` or `ALWAYS_OFF`.
Everything starting with `OFF`, and `ALWAYS_OFF`, counts as switched off, so a value Bosch adds later is read correctly as well.

## Properties

| Property        | Description                                                                    |
|-----------------|--------------------------------------------------------------------------------|
| vendor          | Always `Bosch`.                                                                |
| macAddress      | MAC address the camera uses on the network, read from its certificate.         |
| serialNumber    | Serial number of the camera, read from its certificate.                        |
| firmwareVersion | Firmware currently on the camera, written the way the Bosch Smart Camera app shows it.      |
| rtspsCertificate | Certificate openHAB presents for `rtsps-url`, as PEM, see [Video Streams](#video-streams). |
| rtspsCertificateSha256 | SHA-256 fingerprint of that certificate.                                 |
| eventsApiUrl    | Address of the events API, only while `publishEventsApi` is on; it answers only with an account. |

With an account these are added:

| Property        | Description                                                                    |
|-----------------|--------------------------------------------------------------------------------|
| cameraId        | ID of the camera in the Bosch cloud.                                           |
| modelId         | Model code from the cloud, e.g. `HOME_Eyes_Outdoor`.                           |
| productName     | The name Bosch sells that model under, e.g. `Eyes Outdoor Camera II`.          |
| generation      | Hardware generation, `1` or `2`.                                               |

`productName` and `generation` are only set for models the binding knows:

| modelId             | productName            | generation |
|---------------------|------------------------|------------|
| `INDOOR`            | 360° Indoor Camera     | 1          |
| `OUTDOOR`           | Eyes Outdoor Camera    | 1          |
| `HOME_Eyes_Indoor`  | Eyes Indoor Camera II  | 2          |
| `HOME_Eyes_Outdoor` | Eyes Outdoor Camera II | 2          |

## Snapshots

Each camera serves a still image at an address of its own:

```text
http://<youropenhab>:8080/boschsmartcam/<token>/snapshot.jpg
```

The `snapshot-url` channel carries the ready made address, so linking a String item to it is the easiest way to get at it.
The image is only served while that channel is linked to an item; leave it unlinked and the address answers with 404.
Put that URL into an Image widget instead of an Image item and the picture is only fetched while somebody is actually looking at it - openHAB has no way to tell whether an item is being viewed, a browser request is the only honest signal for that.

Fetched images are reused for `snapshotCacheSeconds`, 3 by default and never below 1.
Ten viewers therefore cause no more traffic than one, and nobody looking causes none at all.
A fetch is a single request to the camera in the local network, the cloud is not involved.

### Who may fetch them

Two things guard the URL.

The token is a random UUID and part of the path, so the address cannot be guessed.
The binding keeps it in the storage of openHAB, so it survives restarts, also for things defined in files.
It is not shown as a property, as it opens the camera to whoever has it; the address channels carry it.
Removing the camera, in the UI or from a file, revokes its addresses: added again, it gets a new token, unless one is set in its configuration.
To revoke addresses that leaked, enter a new token as `accessToken` in the configuration of the camera, at least 16 letters, digits, `-` or `_`; every address with the old one fails from then on.
A token can also be set in a file right away, to keep addresses fixed that are written down elsewhere.

On top of that the request has to come from one of the networks in `snapshotAllowedNetworks` of the binding configuration, which defaults to loopback, the private ranges and the link-local ranges of IPv4 and IPv6.
The comparison works on the raw address bytes against the CIDR blocks, so `192.168.0.9` does not accidentally match `192.168.0.99`.
Behind a reverse proxy openHAB sees the address of the proxy, so add that one rather than the address of the browser.

Be aware of what such a URL is: whoever holds the link sees the picture, without logging in to openHAB.

## Video Streams

The cameras send H.264 in full HD and AAC sound over RTSP, tunneled through TLS on port 9554.
These addresses are offered for players such as VLC, Frigate or a video recorder:

| Channel            | Path                                            | Login                         | When to use it                                               |
|--------------------|-------------------------------------------------|-------------------------------|--------------------------------------------------------------|
| `rtsp-url`         | player to openHAB plain, openHAB to camera TLS  | none, a token in the address  | The usual choice: any player, no password, no certificate.   |
| `rtsp-substream-url` | as `rtsp-url`, small stream without sound     | none, a token in the address  | Second stream for a video recorder, e.g. to detect motion on. |
| `rtsps-url`        | player to openHAB TLS, openHAB to camera TLS    | none, a token in the address  | Like `rtsp-url`, but encrypted all the way.                  |
| `rtsps-substream-url` | as `rtsps-url`, small stream without sound   | none, a token in the address  | Second stream over TLS.                                      |
| `camera-rtsps-url` | player to camera, TLS                           | user and password of the camera | The player reaches the camera itself.                        |

`rtsp-url` reads `rtsp://<openhab>:<port>/<token>`, with the token of the snapshot.
openHAB takes the TLS connection to the camera with the same checks as everything else, logs in with the user and password of the camera itself and passes the stream on; whatever the player sends as login is dropped.
One port serves all cameras, set as `rtspPort` in the binding configuration; without it the RTSP and RTSPS channels stay `UNDEF`.
With `rtsp-url` the connection is unencrypted on the network between player and openHAB.

`rtsps-url` reads `rtsps://<openhab>:<port>/<token>`, on the same port: openHAB tells TLS and plain RTSP apart by the first byte the player sends.
openHAB presents the certificate it serves HTTPS with, read from the keystore named by `jetty.keystore.path`.
Out of the box that is a self-signed certificate openHAB generated itself, so a player has to trust it explicitly.
To make that easy, every camera carries it in its properties: `rtspsCertificate` holds it as PEM, to be saved as a file and handed to the player, and `rtspsCertificateSha256` its fingerprint, to compare with what the player is shown.
Its name is `openhab.org` rather than the host of openHAB, so a player that also checks the host name has to be told which name to expect.
With ffmpeg, after saving `rtspsCertificate` as `openhab.pem`:

```shell
ffplay -rtsp_transport tcp -tls_verify 1 -ca_file openhab.pem -verifyhost openhab.org "rtsps://<openhab>:<port>/<token>"
```

Whoever replaced that keystore with a certificate of their own gets that one here too, after a restart of the binding.
If the keystore cannot be read, the RTSPS channels stay empty and only plain RTSP is offered.

A stream is only offered the way its address is linked to an item: plain RTSP while `rtsp-url` or `rtsp-substream-url` is linked, TLS while `rtsps-url` or `rtsps-substream-url` is.
Whoever does not want a stream unencrypted, or not at all, leaves those channels unlinked; the gateway then answers with 404 as for an unknown token.
Linking only the snapshot does not open the stream, although both share the token.

When a player goes away without ending its session, openHAB ends it at the camera, so the camera does not keep streaming to nobody.

With `camera-rtsps-url` the player speaks TLS with the camera and has to accept its certificate, which names its MAC address instead of its host; ffmpeg for instance needs `-tls_verify 0`.

Only players in `snapshotAllowedNetworks` may use the addresses through openHAB.
Players have to use RTSP over TCP, e.g. `-rtsp_transport tcp` for ffmpeg; only the one connection is passed on.

### Stream Parameters

The camera picks the stream by the query of the address, as documented by Bosch:

| Parameter     | Value | Stream                                              |
|---------------|-------|-----------------------------------------------------|
| `line`        | `1`   | Always 1.                                           |
| `inst`        | `1`   | Full resolution, 1920×1080.                         |
| `inst`        | `2`   | Low resolution, 704×400.                            |
| `inst`        | `3`   | Preview, refreshed once a second.                   |
| `enableaudio` | `1`   | With sound (AAC, 16 kHz, mono).                     |
| `enableaudio` | `0`   | Without sound.                                      |

`camera-rtsps-url` carries `?line=1&inst=1&enableaudio=1`; change the query in place.
`rtsp-url` and `rtsps-url` have no query; append one and it replaces the default `?line=1&inst=1&enableaudio=1`, e.g. `rtsp://<openhab>:<port>/<token>?line=1&inst=2&enableaudio=0` for the low resolution without sound.
The substream channels carry exactly that query.

## Events in the Cloud

The camera uploads an image and a clip of every event to the Bosch cloud.
With an account the binding finds them for the events the camera reports locally: the cloud dates an event within a few hundred milliseconds of the camera, which is how the two are matched, as the cloud does not know the clip id of the camera.
The cloud is only asked after a local event, so nothing is polled in between.

The image is there once the camera has finished recording, some 15 seconds after the detection, and `last-clip-snapshot-url` points to it.
The clip follows some 15 seconds later; then `last-clip-url` points to it and `clip-ready` fires.
Both addresses lead to openHAB, which fetches the file from the cloud with the token of the account and passes it on without keeping it, so a viewer needs no Bosch login.
Like the snapshot they carry the token of the camera, are limited to `snapshotAllowedNetworks` and are only served while their channel is linked.

### Events API

With `publishEventsApi` switched on, all events the cloud keeps for the camera are offered as JSON at the address in the `eventsApiUrl` property, e.g. for an app of your own:

```text
http://<youropenhab>:8080/boschsmartcam/<token>/events
```

| Parameter | Description                                                                    |
|-----------|--------------------------------------------------------------------------------|
| `limit`   | How many events, 1 to 100, 20 by default.                                      |
| `before`  | Only events older than the one with this id, to page back.                     |
| `since`   | Only events newer than the one with this id, for a client that has the others. |

```json
{"events": [
  {"id": "3F2A9C1E-7B4D-4E8A-9C21-5D6E7F809A1B", "time": "2026-10-03T17:43:45.435+02:00", "kind": "PERSON",
   "eventType": "MOVEMENT", "tags": ["PERSON"], "read": false, "clip": "READY",
   "imageUrl": "events/3F2A9C1E-7B4D-4E8A-9C21-5D6E7F809A1B/image.jpg",
   "clipUrl": "events/3F2A9C1E-7B4D-4E8A-9C21-5D6E7F809A1B/clip.mp4"}
]}
```

`kind` names the event like the `event` channel does, `clip` is `READY`, `PENDING` while the camera still uploads it, or `NONE`.
The links are relative to the list.
The newest events are reused for 10 seconds, and fetched anew after a local event, so a client asking often does not reach the cloud every time.
The API only reads: it neither marks events as read nor deletes them.

## Full Example

### Thing Configuration

A camera on its own:

```java
Thing boschsmartcam:camera:64daa0123456 "Front Door" [ host="192.168.0.42", user="localuser", password="secret" ]
```

The same camera under an account; the account is given as its bridge rather than nesting the camera in it, which would put the account into the id of the camera:

```java
Bridge boschsmartcam:account:home "Bosch Camera Account" [ refreshInterval=3600 ]
Thing boschsmartcam:camera:64daa0123456 "Front Door" (boschsmartcam:account:home) [ host="192.168.0.42", user="localuser", password="secret" ]
```

### Item Configuration

```java
Switch FrontDoor_Privacy      "Front Door Privacy Mode"  { channel="boschsmartcam:camera:64daa0123456:local#privacy-mode" }
String FrontDoor_Snapshot     "Front Door Snapshot [%s]" { channel="boschsmartcam:camera:64daa0123456:local#snapshot-url" }
Switch FrontDoor_Notification "Front Door Notifications" { channel="boschsmartcam:account:home:64daa0123456#notifications" }
```

## Credentials

The binding authorizes itself as `oss_residential_app`, the OAuth client Bosch provides for third party integrations - the same one the Home Assistant integration uses.
Client id and client secret identify that client, not the user, and are the same for every installation, so nothing has to be configured for them.

## Credits

Bosch documents the local API of the cameras, but not its cloud API, so that part of the binding is built on work others did first.

The [Bosch Smart Home Camera integration for Home Assistant](https://github.com/mosandlt/Bosch-Smart-Home-Camera-Tool-HomeAssistant) by Thomas Mosandl and its API client [bosch-shc-camera-client](https://github.com/mosandlt/bosch-shc-camera-client), both MIT licensed, were the reference for several details that would have taken a lot of guessing otherwise:

- that `oss_residential_app` is the OAuth client Bosch provides for third party integrations, and that its only registered redirect URI is the one of the My Home Assistant service
- the model codes behind `hardwareVersion` and which hardware generation they are
- the full set of values the notification state can take

Thanks for documenting all of it in the open.
