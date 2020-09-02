/**
 *
 *
 */


package com.dantin.omnilinkiot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitaldan.jomnilinkII.Connection;
import com.digitaldan.jomnilinkII.Message;
import com.digitaldan.jomnilinkII.NotificationListener;
import com.digitaldan.jomnilinkII.OmniInvalidResponseException;
import com.digitaldan.jomnilinkII.OmniNotConnectedException;
import com.digitaldan.jomnilinkII.OmniUnknownMessageTypeException;
import com.digitaldan.jomnilinkII.MessageTypes.AudioSourceStatus;
import com.digitaldan.jomnilinkII.MessageTypes.EventLogData;
import com.digitaldan.jomnilinkII.MessageTypes.ObjectProperties;
import com.digitaldan.jomnilinkII.MessageTypes.ObjectStatus;
import com.digitaldan.jomnilinkII.MessageTypes.ExtendedObjectStatus;
import com.digitaldan.jomnilinkII.MessageTypes.systemevents.ButtonEvent;
import com.digitaldan.jomnilinkII.MessageTypes.systemevents.SystemEvent;
import com.digitaldan.jomnilinkII.MessageUtils;
import com.digitaldan.jomnilinkII.MessageTypes.statuses.*;
import com.digitaldan.jomnilinkII.MessageTypes.systemevents.UPBLinkEvent;

import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;

import org.json.simple.JSONObject;
import java.io.IOException;

public class NotificationProcessor implements NotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private MqttClientConnection mqttconnection;

    public NotificationProcessor(MqttClientConnection mqttconnection) {
        super();
        this.mqttconnection = mqttconnection;
    }

    @Override
    public void objectStatusNotification(ObjectStatus s) {

        String payload = "";
        String topic = "";
        JSONObject payloadJSON = new JSONObject();
        payloadJSON.put("timestamp", System.currentTimeMillis());

        switch (s.getStatusType()) {
        case Message.OBJ_TYPE_AREA:
            //logger.info("STATUS_AREA changed");

            AreaStatus areastatus[] = (AreaStatus[])s.getStatuses();

            for (int i = 0; i < areastatus.length; i++) {
                AreaStatus area = areastatus[i];

                payloadJSON.put("MODE", area.getMode());

                payloadJSON.put("ENTRY_TIMER", area.getEntryTimer());
                payloadJSON.put("EXIT_TIMER", area.getExitTimer());

                JSONObject alarms = new JSONObject();
                alarms.put("BURGLARY", (area.getAlarms() & 1) == 1);
                alarms.put("FIRE", (area.getAlarms() & 2) == 2);
                alarms.put("GAS", (area.getAlarms() & 4) == 4);
                alarms.put("AUX", (area.getAlarms() & 8) == 8);
                alarms.put("FREEZE", (area.getAlarms() & 16) == 16);
                alarms.put("WATER", (area.getAlarms() & 32) == 32);
                alarms.put("DURESS", (area.getAlarms() & 64) == 64);
                alarms.put("TEMPERATURE", (area.getAlarms() & 128) == 128);
                payloadJSON.put("ALARMS", alarms);

                payload = payloadJSON.toString();
                topic = "hai/alarm/" + area.getNumber();

                sendMessage(topic, payload);
            }

            break;
        case Message.OBJ_TYPE_AUDIO_ZONE:
            //logger.info("STATUS_AUDIO_ZONE changed");

            AudioZoneStatus audiostatus[] = (AudioZoneStatus[])s.getStatuses();

            for (int i = 0; i < audiostatus.length; i++) {
                AudioZoneStatus azone = audiostatus[i];

                payloadJSON.put("POWER", azone.isPower());
                payloadJSON.put("SOURCE", azone.getSource());
                payloadJSON.put("VOLUME", azone.getVolume());
                payloadJSON.put("MUTE", azone.isMute());

                payload = payloadJSON.toString();
                topic = "hai/audio/" + azone.getNumber();

                sendMessage(topic, payload);
            }

            break;
        case Message.OBJ_TYPE_AUX_SENSOR:
            //logger.info("STATUS_AUX changed");
            break;
        case Message.OBJ_TYPE_EXP:
            //logger.info("STATUS_EXP changed");
            break;
        case Message.OBJ_TYPE_MESG:
            //logger.info("STATUS_MESG changed");
            break;
        case Message.OBJ_TYPE_THERMO:
            //logger.info("STATUS_THERMO changed");
            ExtendedThermostatStatus thermostatus[] = (ExtendedThermostatStatus[])s.getStatuses();

            for (int i = 0; i < thermostatus.length; i++) {
                ExtendedThermostatStatus thermo = thermostatus[i];
                ThermostatStatus status = thermo.getThermostatStatus();

                payloadJSON.put("TEMPERATURE", MessageUtils.omniToF(status.getTemperature()));
                payloadJSON.put("HEAT_SETPOINT", MessageUtils.omniToF(status.getHeatSetpoint()));
                payloadJSON.put("COOL_SETPOINT", MessageUtils.omniToF(status.getCoolSetpoint()));
                payloadJSON.put("MODE", status.getMode());
                payloadJSON.put("FAN", status.getFan());
                payloadJSON.put("HOLD", status.getHold());
                payloadJSON.put("HUMIDITY", MessageUtils.omniToF(thermo.getHumidity()));
                payloadJSON.put("HUMIDITY_SETPOINT", MessageUtils.omniToF(thermo.getHumiditySetpoint()));
                payloadJSON.put("DEHUMIDIFY_SETPOINT", MessageUtils.omniToF(thermo.getDehumidifySetpoint()));

                payloadJSON.put("HEATING", (thermo.getExtendedStatus() & 1) == 1);
                payloadJSON.put("COOLING", (thermo.getExtendedStatus() & 2) == 2);
                payloadJSON.put("HUMIDIFYING", (thermo.getExtendedStatus() & 4) == 4);
                payloadJSON.put("DEHUMIDIFYING", (thermo.getExtendedStatus() & 8) == 8);

                payloadJSON.put("COMMUNICATIONS_FAILURE", (status.getStatus() & 1) == 1);
                payloadJSON.put("FREEZE_ALARM", (status.getStatus() & 2) == 2);

                payload = payloadJSON.toString();
                topic = "hai/thermostat/" + thermo.getNumber();

                sendMessage(topic, payload);
            }

            break;

        case Message.OBJ_TYPE_UNIT:
            //logger.info("STATUS_UNIT changed");
            UnitStatus unitstatus[] = (UnitStatus[])s.getStatuses();

            for (int i = 0; i < unitstatus.length; i++) {
                UnitStatus unit = unitstatus[i];

                int percentage = 0;
                if (unit.getStatus() >= 100) {
                    percentage = unit.getStatus() - 100;
                    payloadJSON.put("PERCENTAGE", percentage);
                } else {
                    percentage = unit.getStatus();
                    payloadJSON.put("ON", percentage);
                }

                payload = payloadJSON.toString();
                topic = "hai/unit/" + unit.getNumber();

                sendMessage(topic, payload);
            }

            break;

        case Message.OBJ_TYPE_ZONE:
            //logger.info("STATUS_ZONE changed");
            ZoneStatus zonestatus[] = (ZoneStatus[])s.getStatuses();
            for (int i = 0; i < zonestatus.length; i++) {
                ZoneStatus zone = zonestatus[i];

                payloadJSON.put("NOT_READY", (zone.getStatus() & 1) == 1);
                payloadJSON.put("TROUBLE", (zone.getStatus() & 2) == 2);
                payloadJSON.put("BYPASSED", (zone.getStatus() & 32) == 32);

                payload = payloadJSON.toString();
                topic = "hai/zone/" + zone.getNumber();

                sendMessage(topic, payload);
            }

            break;

        default:
            //logger.info("Unknown type " + s.getStatusType());
            break;
        }
        // logger.info(s.toString());
    }

    @Override
    public void systemEventNotification(SystemEvent event) {
        logger.info("Got SystemEvent type {}", event.getType());
        switch (event.getType()) {
        case UPB_LINK:
            UPBLinkEvent linkEvent = (UPBLinkEvent) event;
            logger.info("UPB Link command({}) for link({})", linkEvent.getLinkCommand(), linkEvent.getLinkNumber() );
            break;
        case BUTTON:
            logger.info("ButtonEvent number {}", ((ButtonEvent) event).getButtonNumber());
            break;
        case PHONE_LINE_OFF_HOOK:
            logger.info("PHONE_LINE_OFF_HOOK event");
            break;
        default:
            break;
        }

    }

    private void sendMessage(String topic, String payload) {
        logger.info("Publishing " + topic + " - " + payload);
        try {
            mqttconnection.publish(new MqttMessage(topic, payload.getBytes()), QualityOfService.AT_MOST_ONCE, false).get();
        } catch (Exception e) {
            logger.info("Could not send the message");
        }
    }
}
