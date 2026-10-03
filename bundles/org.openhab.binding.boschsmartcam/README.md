# Bosch Smart Home Camera Binding

This binding integrates the Bosch Smart Home Cameras of the second generation (Eyes Outdoor Camera II, Eyes Indoor Camera II) into openHAB.

It talks to each camera directly through the local API Bosch provides for these cameras.
That API is read only, so an optional Bosch SingleKey ID account can be added for what only the Bosch cloud can do: switching the privacy mode, and the push notifications of the Bosch app.
The binding is unrelated to the Bosch Smart Home binding, which talks to the Smart Home Controller.

## Prerequisites

The local API is switched on per camera in the Bosch app: _camera settings_, _More_, _Local Data Interface_.
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

A scan runs in the background every 30 minutes and can be started by hand at any time.
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
| user                 | text    | User of the local API, as shown in the Bosch app.                   | `localuser` | yes      | no       |
| password             | text    | Password of the local API, as shown in the Bosch app.               | N/A         | yes      | no       |
| snapshotCacheSeconds | integer | How long a fetched image is reused in sec.                          | 3           | no       | yes      |
| streamAudio          | boolean | Whether the live stream carries the sound of the camera.            | true        | no       | yes      |
| trustAllCertificates | boolean | Accept any certificate instead of verifying it, see below.          | false       | no       | yes      |

The binding verifies the certificate of the camera against the root Bosch publishes for the local API.
The name in that certificate is the MAC address of the camera rather than its host name, so the host name itself is not checked.
Instead the MAC address is compared with the one the thing was created for: if another device answers at the configured address, the thing goes offline with a note saying so.

Should a firmware update ever bring a certificate below a different root, the thing goes offline saying its certificate is not trusted.
`trustAllCertificates` is the way out until the binding knows the new root: the chain is no longer verified then, the MAC address is still read and compared.

### `account` Bridge Configuration

| Name            | Type    | Description                                                              | Default | Required | Advanced |
|-----------------|---------|--------------------------------------------------------------------------|---------|----------|----------|
| refreshInterval | integer | Interval the camera settings are polled from the Bosch cloud in sec.      | 300     | no       | no       |

Commands sent from openHAB are read back a few seconds later, so `refreshInterval` only determines how fast a change made elsewhere, for example in the Bosch app, shows up in openHAB.
The minimum is 30 seconds.

### Binding Configuration

| Name                    | Type | Description                                                         | Default                         |
|-------------------------|------|---------------------------------------------------------------------|---------------------------------|
| snapshotAllowedNetworks | text | Networks that may fetch the snapshot URLs, as comma separated CIDR. | loopback and the private ranges |

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
| local#privacy-mode    | Switch   | RW         | `ON` switches the camera off. The indoor camera closes its shutter. Switching needs an account.  |
| local#event           | Trigger  |            | Fires once per detected event, with its kind as payload, see below.                             |
| local#last-event      | String   | R          | Kind of the event detected last.                                                                |
| local#last-event-time | DateTime | R          | When the camera detected the last event.                                                        |
| local#recording       | Switch   | R          | `ON` while the camera records the clip of an event.                                             |
| local#snapshot-url    | String   | R          | Address a still image can be fetched from. Contains a token, treat it as a secret.              |
| local#hls-url         | String   | R          | Address of the live stream as HLS, for a video widget. Contains a token, treat it as a secret.  |

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

### `account` Channels

The push notifications are a setting of the Bosch app, the camera itself does not know about them.
They therefore sit on the account, in one channel group per camera of the account, whether or not that camera is a thing in openHAB.
The group is named after the cloud id of the camera, e.g. `boschsmartcam:account:home:1a2b3c4d5e6f40718293a4b5c6d7e8f9#notifications`, and the channels carry the name of the camera from the app.

| Channel                   | Type   | Read/Write | Description                                                                                     |
|---------------------------|--------|------------|-------------------------------------------------------------------------------------------------|
| <camera>#notifications        | Switch | RW         | Push notifications of this camera to the Bosch app.                                            |
| <camera>#notifications-status | String | R          | The notification setting as the cloud reports it, e.g. `FOLLOW_CAMERA_SCHEDULE` or `ALWAYS_OFF`. |

The notification setting is not a plain on/off in the cloud: it can also follow a schedule.
`notifications` therefore reads `ON` for everything that is not switched off, and writing `ON` always sets `FOLLOW_CAMERA_SCHEDULE`.
Use `notifications-status` to see the exact setting: `FOLLOW_CAMERA_SCHEDULE`, `FOLLOW_SCHEDULE`, `ON_CAMERA_SCHEDULE`, `OFF_CAMERA_SCHEDULE`, `OFF_OVERRIDE`, `OFF_UNTIL` or `ALWAYS_OFF`.
Everything starting with `OFF` counts as switched off, so a value Bosch adds later is read correctly as well.

## Properties

| Property        | Description                                                                    |
|-----------------|--------------------------------------------------------------------------------|
| vendor          | Always `Bosch`.                                                                |
| macAddress      | MAC address the camera uses on the network, read from its certificate.         |
| serialNumber    | Serial number of the camera, read from its certificate.                        |
| firmwareVersion | Firmware currently on the camera, written the way the Bosch app shows it.      |

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
Put that URL into an Image widget instead of an Image item and the picture is only fetched while somebody is actually looking at it - openHAB has no way to tell whether an item is being viewed, a browser request is the only honest signal for that.

Fetched images are reused for `snapshotCacheSeconds`, 3 by default and never below 1.
Ten viewers therefore cause no more traffic than one, and nobody looking causes none at all.
A fetch is a single request to the camera in the local network, the cloud is not involved.

### Who may fetch them

Two things guard the URL.

The token is a random UUID and part of the path, so the address cannot be guessed.
It is stored as the `accessToken` property of the camera and survives restarts.
Deleting that property hands out a new one on the next start, which makes every previously shared link fail.

On top of that the request has to come from one of the networks in `snapshotAllowedNetworks` of the binding configuration, which defaults to loopback and the private ranges of IPv4 and IPv6.
The comparison works on the raw address bytes against the CIDR blocks, so `192.168.0.9` does not accidentally match `192.168.0.99`.
Behind a reverse proxy openHAB sees the address of the proxy, so add that one rather than the address of the browser.

Be aware of what such a URL is: whoever holds the link sees the picture, without logging in to openHAB.

## Live Video

The binding turns the stream of a camera into HLS itself, without ffmpeg and without transcoding: the camera already sends H.264 and AAC, they are only put into fragmented MP4 segments of about two seconds.
The `hls-url` channel carries the address of the playlist.

The stream starts when the playlist is fetched, which takes a few seconds, and stops again 30 seconds after the last request.
Nothing is received from the camera while nobody watches.

Where it plays:

- The video card of the main UI plays HLS in every current browser.
- A `Video` element in a sitemap with `encoding="hls"` relies on the browser playing HLS itself, which Safari, current versions of Chrome and the openHAB apps for Android and iOS do. Basic UI hands the address to the browser as it is, so the browser has to reach openHAB directly.

Browsers start a video with sound only after the user interacted with the page; set `streamAudio` to `false` for a stream that starts on its own everywhere.

A sitemap requires a `url` for every `Video` element. With an item linked to `hls-url` the address in the item wins, so the `url` only has to be valid:

```java
Video item=FrontDoor_Live url="http://192.168.0.10:8080/boschsmartcam/<token>/live.m3u8" encoding="hls"
```

## Full Example

### Thing Configuration

A camera on its own:

```java
Thing boschsmartcam:camera:64daa0123456 "Front Door" [ host="192.168.0.42", user="localuser", password="secret" ]
```

The same camera under an account:

```java
Bridge boschsmartcam:account:home "Bosch Camera Account" [ refreshInterval=300 ] {
    Thing camera 64daa0123456 "Front Door" [ host="192.168.0.42", user="localuser", password="secret" ]
}
```

### Item Configuration

```java
Switch FrontDoor_Privacy      "Front Door Camera Off"    { channel="boschsmartcam:camera:home:64daa0123456:local#privacy-mode" }
String FrontDoor_Snapshot     "Front Door Snapshot [%s]" { channel="boschsmartcam:camera:home:64daa0123456:local#snapshot-url" }
Switch FrontDoor_Notification "Front Door Notifications" { channel="boschsmartcam:account:home:1a2b3c4d5e6f40718293a4b5c6d7e8f9#notifications" }
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
