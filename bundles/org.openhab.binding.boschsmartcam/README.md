# Bosch Smart Camera Binding

This binding integrates the Bosch Smart Home Cameras (Eyes outdoor camera, 360° indoor camera) into openHAB.

The cameras are not reachable through the Bosch Smart Home Controller, they are managed by a separate Bosch cloud service that the Bosch Smart Camera app talks to.
This binding uses the same cloud API as that app, so a Bosch SingleKey ID account is all that is needed.

The binding currently covers the camera settings.
Snapshots and video streams are not part of it.

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
That address belongs to the My Home Assistant service, a static page that forwards the login to the instance URL stored in the browser and appends `/auth/external/callback` to it.
Setting that instance URL to the authorization page of this binding therefore makes the login return to the binding's own path, and the authorization completes on its own.

1. Add an `account` thing. It stays offline with the note that it is not authorized yet.
1. Open `http://<youropenhab>:8080/boschsmartcam` in a browser.
1. Follow step 1 on that page: open the My Home Assistant settings and set the instance URL to the address of that very page, e.g. `http://192.168.178.80:8080/boschsmartcam`. The page shows the exact value and offers a button to copy it. This is stored only in that browser and is needed once.
1. Click **Log in with Bosch SingleKey ID** and log in. If you are already signed in with your SingleKey ID in that browser, no login form appears and you are forwarded straight through.
1. On the My Home Assistant page, click the button that opens the link to openHAB. The account thing goes online.

No Home Assistant installation is involved at any point - the instance URL is just a value in the browser's local storage.
Because it points at the binding's own path, the callback stays inside `/boschsmartcam` and the binding does not occupy any global path of the openHAB instance.

If the instance URL cannot be set, for example because the login happens on a phone or in a private window, the page offers a fallback: copy the complete address you were redirected to and paste it into the field under _The login did not return to openHAB?_.
That page may show an error - it only carries the authorization code.

From then on the binding refreshes the access token on its own, so this procedure is only needed again if the tokens are removed or revoked.
The same page also lets you remove the stored tokens of an account, for example to authorize it with a different Bosch account.

## Channels

| Channel        | Type   | Read/Write | Description                                                                                         |
|----------------|--------|------------|-----------------------------------------------------------------------------------------------------|
| privacy-mode   | Switch | RW         | `ON` switches the camera off. The 360° indoor camera closes its shutter, the Eyes camera stops the feed. |
| notifications  | Switch | RW         | Push notifications of this camera to the Bosch app.                                                  |
| status         | String | R          | Connection state the Bosch cloud reports, e.g. `ONLINE`.                                             |

After switching `privacy-mode` the camera needs a few seconds to apply the change, so the confirmed state arrives with a small delay.

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
