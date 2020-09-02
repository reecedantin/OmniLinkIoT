# OmniLinkIoT
AWS IoT Core and Greengrass translation for the Omni-Link II protocol


Using [jomnilink](https://github.com/digitaldan/jomnilink)

### How to run

This project was built to run on a Raspberry Pi 4 with a AWS Greengrass Daemon running.
It also runs in a docker container on the pi.


* First make sure docker is installed
```
curl -fsSL https://get.docker.com | sh
```

* Clone the repo
```
git clone https://github.com/reecedantin/OmniLinkIoT.git
```

* Then build the image
```
docker image build -t hai omnilinkiot/
```

* And run
```
docker run -tid -v "(your config folder full location):/config" --name hai --restart on-failure hai
```

* To stop
```
docker rm --force hai
```


### Config

The config.properties file is an example of how to setup and explains each value. Place this file along with the certificates in a folder on the pi and reference the folder in the docker run command.


### Commands

OmniLinkIoT will listen to commands from IoT. Make sure to route the subscriptions in the AWS console.

```
hai/alarm/0-n    (0 for all)
    /MODE 0-6
    /BEEP/0-16/ ON/OFF/0-6  (see protocol for beep counts) (0 for all consoles)
    /BYPASS/1-n ON/OFF

hai/thermostat/1-n
    /MODE 0-3
    /HEAT_SETPOINT ºF-integer
    /COOL_SETPOINT ºF-integer
    /FAN 0-1
    /HOLD ON/OFF

hai/garage/1-n (will flip relays on and off, kinda custom... use TRIGGER)
    /OPEN OPEN
    /CLOSE CLOSE
    /TRIGGER TRIGGERR

hai/unit/1-n
    /POWER ON/OFF
    /PERCENT 0-100

hai/audio/1-8
    /POWER ON/OFF
    /SOURCE 1-8
    /VOLUME 0-100
    /MUTE ON/OFF

hai/allstatus/0 STATUS (will spit out all the current statuses, with respect to numDevices in config)
```

### Statuses

If anything happens on the Omni, we wanna know. OmniLinkIoT will translate some of these commands and spit them back to IoT.

```
hai/alarm/#
{
    "EXIT_TIMER": #,
    "MODE": #,
    "ENTRY_TIMER": #,
    "ALARMS": {
        "FREEZE": true/false,
        "AUX": true/false,
        "TEMPERATURE": true/false,
        "FIRE": true/false,
        "GAS": true/false,
        "DURESS": true/false,
        "BURGLARY": true/false,
        "WATER": true/false
    },
    "timestamp": #
}

hai/thermostat/#
{
    "COMMUNICATIONS_FAILURE": true/false,
    "HEAT_SETPOINT": #,
    "HUMIDITY": #,
    "MODE": #,
    "HEATING": true/false,
    "HUMIDITY_SETPOINT": #,
    "DEHUMIDIFYING": true/false,
    "DEHUMIDIFY_SETPOINT": #,
    "COOL_SETPOINT": #,
    "FAN": #,
    "TEMPERATURE": #,
    "COOLING": true/false,
    "FREEZE_ALARM": true/false,
    "HUMIDIFYING": true/false,
    "HOLD": #,
    "timestamp": #
}

hai/unit/#
{
  "timestamp": 1599011812899,
  "ON": #  (this ones wierd, check the protocol, 0/1 is on off, 100-200 is percent)
}

hai/zone/#
{
  "BYPASSED": true/false,
  "NOT_READY": true/false,
  "TROUBLE": true/false,
  "timestamp": #
}

hai/audio/#
{
  "VOLUME": #,
  "SOURCE": #,
  "POWER": true/false,
  "MUTE": true/false,
  "timestamp": #
}

hai/
    connected (will send when first booted)
```
