# Bosch Smart Home Camera Binding

This binding integrates the Bosch Smart Home Cameras (Eyes outdoor camera, 360° indoor camera) into openHAB.

The cameras are not reachable through the Bosch Smart Home Controller, they are managed by a separate Bosch cloud service that the Bosch Smart Camera app talks to.
This binding uses the same cloud API as that app, so a Bosch SingleKey ID account is all that is needed.
It is therefore unrelated to the Bosch Smart Home binding, which talks to the Smart Home Controller.

The binding covers the camera settings and serves a still image per camera.
Video streams are not part of it - use the `ipcamera` binding for those.

## Supported Things

- `account`: A Bosch SingleKey ID account. This bridge holds the authorization and polls the cameras.
- `camera`: A single camera of the account.

## Discovery

Cameras cannot be discovered before the account is authorized.
Add an `account` thing, authorize it as described below, then start a scan for this binding to find all cameras of the account.

## Thing Configuration

### `account` Bridge Configuration

| Name            | Type    | Description                                                              | Default | Required | Advanced |
|-----------------|---------|--------------------------------------------------------------------------|---------|----------|----------|
| refreshInterval | integer | Interval the camera settings are polled from the Bosch cloud in sec.      | 300     | no       | no       |

Commands sent from openHAB are read back a few seconds later, so `refreshInterval` only determines how fast a change made elsewhere, for example in the Bosch app, shows up in openHAB.
The minimum is 30 seconds.

A `REFRESH` command on any channel of a camera reads the settings from the cloud as well, so a rule can pick up the current state without waiting for the next poll.
Such a refresh is skipped if the values were read less than 10 seconds ago, because openHAB sends `REFRESH` per channel.

### `camera` Thing Configuration

| Name     | Type | Description                                | Default | Required | Advanced |
|----------|------|--------------------------------------------|---------|----------|----------|
| cameraId | text | ID of the camera as used by the Bosch cloud | N/A     | yes      | no       |

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

| Channel        | Type   | Read/Write | Description                                                                                         |
|----------------|--------|------------|-----------------------------------------------------------------------------------------------------|
| privacy-mode   | Switch | RW         | `ON` switches the camera off. The 360° indoor camera closes its shutter, the Eyes camera stops the feed. |
| notifications  | Switch | RW         | Push notifications of this camera to the Bosch app.                                                  |
| notifications-status | String | R    | The notification setting as the cloud reports it, e.g. `FOLLOW_CAMERA_SCHEDULE` or `ALWAYS_OFF`.      |
| status         | String | R          | `ONLINE`, `OFFLINE`, `UPDATING` while a firmware update runs, or `SESSION_LIMIT`.                     |
| snapshot-url   | String | R          | Address a still image can be fetched from. Contains a token, treat it as a secret.                    |

After switching `privacy-mode` the camera needs a few seconds to apply the change, so the confirmed state arrives with a small delay.

The notification setting is not a plain on/off in the cloud: it can also follow a schedule.
`notifications` therefore reads `ON` for everything that is not switched off, and writing `ON` always sets `FOLLOW_CAMERA_SCHEDULE`.
Use `notifications-status` to see the exact setting: `FOLLOW_CAMERA_SCHEDULE`, `FOLLOW_SCHEDULE`, `ON_CAMERA_SCHEDULE`, `OFF_CAMERA_SCHEDULE`, `OFF_OVERRIDE`, `OFF_UNTIL` or `ALWAYS_OFF`.
Everything starting with `OFF` counts as switched off, so a value Bosch adds later is read correctly as well.

The reachability behind `status` is read from `/ping`, falling back to `/commissioned`, because the camera list does not carry a usable state for it.
Bosch answers `ONLINE`, `OFFLINE`, `UNREACHABLE` or one of `UPDATING_REGULAR`, `UPDATING_FORCED` and `UPDATING_APP0`; the binding folds these into the four values above.
That costs one extra request per camera and poll.
`SESSION_LIMIT` means Bosch refused the request because too many live sessions are open at once - counted across every client of the account, so the Bosch app can cause it.
It says nothing about the camera, which is why the thing stays online in that case.

## Properties

The `camera` thing carries these properties, refreshed with every poll:

| Property        | Description                                                                    |
|-----------------|--------------------------------------------------------------------------------|
| vendor          | Always `Bosch`.                                                                |
| modelId         | Model code from the cloud, e.g. `HOME_Eyes_Outdoor`.                           |
| productName     | The name Bosch sells that model under, e.g. `Eyes Outdoor Camera II`.          |
| generation      | Hardware generation, `1` or `2`. The two differ in the endpoints they support. |
| firmwareVersion | Firmware currently on the camera.                                              |
| cameraId        | ID of the camera in the Bosch cloud.                                           |

`productName` and `generation` are only set for models the binding knows:

| modelId             | productName          | generation |
|---------------------|----------------------|------------|
| `INDOOR`            | 360° Indoor Camera     | 1          |
| `OUTDOOR`           | Eyes Outdoor Camera    | 1          |
| `HOME_Eyes_Indoor`  | Eyes Indoor Camera II  | 2          |
| `HOME_Eyes_Outdoor` | Eyes Outdoor Camera II | 2          |

Discovery uses the product name in the suggested label as well, e.g. _Garden (Eyes Outdoor Camera II)_.

## Snapshots

Each camera serves a still image at an address of its own:

```text
http://<youropenhab>:8080/boschsmartcam/<token>/snapshot.jpg
```

The `snapshot-url` channel carries the ready made address, so linking a String item to it is the easiest way to get at it.
Put that URL into an Image widget instead of an Image item and the picture is only fetched while somebody is actually looking at it - openHAB has no way to tell whether an item is being viewed, a browser request is the only honest signal for that.

Fetched images are reused for `snapshotCacheSeconds`, 15 by default and never below 5.
Ten viewers therefore cause no more traffic than one, and nobody looking causes none at all.
A fetch costs one request to the cloud for the credentials, which are cached for 45 seconds, and one to the camera in the local network.

### Who may fetch them

Two things guard the URL.

The token is 24 random bytes and part of the path, so the address cannot be guessed.
It is stored as the `snapshotToken` property of the camera and survives restarts.
Deleting that property hands out a new one on the next start, which makes every previously shared link fail.

On top of that the request has to come from one of the networks in `snapshotAllowedNetworks` on the account bridge, which defaults to loopback and the private ranges of IPv4 and IPv6.
The comparison works on the raw address bytes against the CIDR blocks, so `192.168.0.9` does not accidentally match `192.168.0.99`.
Behind a reverse proxy openHAB sees the address of the proxy, so add that one rather than the address of the browser.

Be aware of what such a URL is: whoever holds the link sees the picture, without logging in to openHAB.

## Full Example

### Thing Configuration

```java
Bridge boschsmartcam:account:home "Bosch Camera Account" [ refreshInterval=300 ] {
    Thing camera garden "Garden" [ cameraId="21E99E8A-0000-0000-0000-000000000000" ]
}
```

### Item Configuration

```java
Switch Garden_Privacy      "Garden Camera Off"       { channel="boschsmartcam:camera:home:garden:privacy-mode" }
Switch Garden_Notification "Garden Notifications"    { channel="boschsmartcam:camera:home:garden:notifications" }
String Garden_Status       "Garden Camera [%s]"      { channel="boschsmartcam:camera:home:garden:status" }
```

## Credentials

The binding authorizes itself as `oss_residential_app`, the OAuth client Bosch provides for third party integrations - the same one the Home Assistant integration uses.
Client id and client secret identify that client, not the user, and are the same for every installation, so nothing has to be configured for them.

## Credits

Bosch does not document this cloud API, so the binding is built on work others did first.

The [Bosch Smart Home Camera integration for Home Assistant](https://github.com/mosandlt/Bosch-Smart-Home-Camera-Tool-HomeAssistant) by Thomas Mosandl and its API client [bosch-shc-camera-client](https://github.com/mosandlt/bosch-shc-camera-client), both MIT licensed, were the reference for several details that would have taken a lot of guessing otherwise:

- that `oss_residential_app` is the OAuth client Bosch provides for third party integrations, and that its only registered redirect URI is the one of the My Home Assistant service
- the model codes behind `hardwareVersion` and which hardware generation they are
- the full set of values the notification state can take
- that reachability has to be read from `/ping` and `/commissioned` rather than from the camera list

Thanks for documenting all of it in the open.
