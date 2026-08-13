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
| refreshInterval | integer | Interval the camera settings are polled from the Bosch cloud in sec.      | 60      | no       | no       |
| clientSecret    | text    | Client secret of the Bosch app, only needed if it was not compiled in.    | N/A     | no       | yes      |

### `camera` Thing Configuration

| Name     | Type | Description                                | Default | Required | Advanced |
|----------|------|--------------------------------------------|---------|----------|----------|
| cameraId | text | ID of the camera as used by the Bosch cloud | N/A     | yes      | no       |

## Authorization

Bosch accepts exactly one redirect address for the app client, and that address does not point to openHAB.
The browser can therefore not return to openHAB by itself and the authorization code has to be carried over manually, once per account:

1. Add an `account` thing. It stays offline with the note that it is not authorized yet.
1. Open `http://<youropenhab>:8080/boschsmartcam` in a browser.
1. Click **Log in with Bosch SingleKey ID** and log in.
1. The browser ends up on a page that may show an error. That is expected - the page only carries the authorization code.
1. Copy the complete address of that page from the address bar, paste it into the field on the openHAB page and submit it.

The account thing goes online and the tokens are stored by openHAB.
From then on the binding refreshes the access token on its own, so this procedure is only needed again if the tokens are removed or revoked.

The same page also lets you remove the stored tokens of an account, for example to authorize it with a different Bosch account.

### Letting the login return automatically

The fixed redirect address belongs to the My Home Assistant service, which forwards the login to the instance URL stored in the browser and appends `/auth/external/callback`.
The binding listens on that path as well, so pointing that instance URL at openHAB removes the copy and paste step:

1. Open [the My Home Assistant settings page](https://my.home-assistant.io/redirect/_change/?redirect=oauth).
1. Set the instance URL to the address of your openHAB, e.g. `http://192.168.178.80:8080`, and save it.
1. Start the login as described above. The browser now ends up on the openHAB page with the account already authorized.

The instance URL is only stored in that browser, no Home Assistant installation is needed.
The authorization page shows the exact value to enter and offers a button to copy it.

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
Bridge boschsmartcam:account:home "Bosch Camera Account" [ refreshInterval=60 ] {
    Thing camera garden "Garden" [ cameraId="21E99E8A-0000-0000-0000-000000000000" ]
}
```

### Item Configuration

```java
Switch Garden_Privacy      "Garden Camera Off"       { channel="boschsmartcam:camera:home:garden:privacy-mode" }
Switch Garden_Notification "Garden Notifications"    { channel="boschsmartcam:camera:home:garden:notifications" }
String Garden_Status       "Garden Camera [%s]"      { channel="boschsmartcam:camera:home:garden:status" }
```

## Building

The client secret of the Bosch app is not published by Bosch.
Either put it into `OAUTH_CLIENT_SECRET` in `BoschSmartCamBindingConstants` before building, or set it as the advanced `clientSecret` parameter of the account thing.
Without it the account thing stays offline with a configuration error.
