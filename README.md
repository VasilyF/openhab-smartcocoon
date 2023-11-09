# smartcocoon Binding

This is an openHAB binding for the SmartCocoon Smart Vent Fans

http://mysmartcocoon.com


## Supported Things


This binding supports the following thing types:

- account: Bridge - Implements the API for an account registered within SmartCocoon App

- fan: This thing represents each Vent Fan under the account


## Discovery

Not implemented in this version

## Thing Configuration

You need to have an account within the SmartCocoon app and add the fans to your account in the app.
Then you can proceed creating a Bridge thing for your account, and multiple Fan things linked to this account


### Bridge Configuration

| Name            | Type    | Description                           | Default | Required | Advanced |
|-----------------|---------|---------------------------------------|---------|----------|----------|
| usernname       | text    | User email as registered within app   | N/A     | yes      | no       |
| password        | text    | Password as registered within app     | N/A     | yes      | no       |
| refreshInterval | integer | Interval the device is polled in sec. | 60      | no       | yes      |

### Fan Configuration

| Parameter | Description                                                             | Type   | Default  | Required |
|-----------|-------------------------------------------------------------------------|--------|----------|----------|
| deviceId  | Fan id as show on the front of device or within the app                 | text   | NA       | yes      |


## Channels

_Here you should provide information about available channel types, what their meaning is and how they can be used._

_Note that it is planned to generate some part of this based on the XML files within ```src/main/resources/OH-INF/thing``` of your binding._

| Channel | Type   | Read/Write | Description                   |
|---------|--------|------------|-------------------------------|
| power   | Switch | RW         | Use to control the fan ON/OFF |
| fanSpeed| Number | RW         | Use to control the fan speed  |

Note that sending ON/OFF commands to the `power` channel will set `always_on`/`always_off` modes.
On status refrest the `power` channel will reflect the state of the fan and will work even `auto` or `eco` modes.

## Full Example


### Thing Configuration

```java
Bridge smartcocoon:account:my "My SmartCocoon Account" [ username="xxxx@xxxx.xxx", password="xxxxxxxx"] {
   Thing fan VentFan "My Fan" [ deviceId="xxxxxx" ]
}
```

### Item Configuration

```java
Switch Vent_Fan "Vent Fan [%s]" {channel="smartcocoon:fan:my:VentFan:power"}
Number Vent_Speed "Vent Speed [%s]" {channel="smartcocoon:fan:my:VentFan:fanSpeed"}
```

### Sitemap Configuration

```perl
Optional Sitemap configuration goes here.
Remove this section, if not needed.
```

