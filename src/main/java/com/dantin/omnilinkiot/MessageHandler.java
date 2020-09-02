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
import com.digitaldan.jomnilinkII.MessageTypes.systemevents.ButtonEvent;
import com.digitaldan.jomnilinkII.MessageTypes.systemevents.SystemEvent;
import com.digitaldan.jomnilinkII.MessageUtils;
import com.digitaldan.jomnilinkII.MessageTypes.statuses.*;
import com.digitaldan.jomnilinkII.MessageTypes.systemevents.UPBLinkEvent;
import com.digitaldan.jomnilinkII.MessageTypes.CommandMessage;

import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;

import org.json.simple.JSONObject;
import java.nio.charset.StandardCharsets;
import java.io.IOException;


public class MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private Connection currentConnection;

    public MessageHandler(Connection c) {
        this.currentConnection = c;
    }

    public void process(MqttMessage mqttMessage) {
        String topic = mqttMessage.getTopic();
        String[] topicList = topic.split("/");

        String message = new String(mqttMessage.getPayload(), StandardCharsets.UTF_8);
        logger.info("new message on " + topic + " - " + message);


        if (topicList.length > 2) {
            logger.info(topicList[0] + " " + topicList[1] + " " + topicList[2]);
        }


        if(!topicList[0].equals("hai") || topicList.length < 3) {
            return;
        }

        String type = topicList[1];
        try {

            switch(type) {
                case "alarm":
                    handleAlarm(topicList, message);
                    break;
                case "thermostat":
                    handleThermostat(topicList, message);
                    break;
                case "garage":
                    handleGarage(topicList, message);
                    break;
                case "unit":
                    handleUnit(topicList, message);
                    break;
                case "audio":
                    handleAudio(topicList, message);
                    break;


            }
        } catch (OmniUnknownMessageTypeException | OmniInvalidResponseException | IOException e) {
            logger.error("Could not send message", e);
        } catch (OmniNotConnectedException e) {
            logger.error("Controller not connected!", e);
            System.exit(-1);
        }
    }


    private void handleAlarm(String[] topicList, String message) throws IOException, OmniNotConnectedException,
                                                    OmniUnknownMessageTypeException, OmniInvalidResponseException {

        int number = Integer.parseInt(topicList[2]);

        if(number < 0) {
            return;
        }

        String service = topicList[3];

        switch (service) {
            case "MODE":
                int mode = Integer.parseInt(message);
                int commandMessage;
                switch (mode) {
                    case 0:
                        commandMessage = CommandMessage.CMD_SECURITY_OMNI_DISARM;
                        break;
                    case 1:
                        commandMessage = CommandMessage.CMD_SECURITY_OMNI_DAY_MODE;
                        break;
                    case 2:
                        commandMessage = CommandMessage.CMD_SECURITY_OMNI_NIGHT_MODE;
                        break;
                    case 3:
                        commandMessage = CommandMessage.CMD_SECURITY_OMNI_AWAY_MODE;
                        break;
                    case 4:
                        commandMessage = CommandMessage.CMD_SECURITY_OMNI_VACATION_MODE;
                        break;
                    case 5:
                        commandMessage = CommandMessage.CMD_SECURITY_OMNI_DAY_INSTANCE_MODE;
                        break;
                    case 6:
                        commandMessage = CommandMessage.CMD_SECURITY_OMNI_NIGHT_DELAYED_MODE;
                        break;
                    default:
                        return;
                }

                currentConnection.controllerCommand(commandMessage, 1, number); // user code 1

                break;

            case "BEEP":
                if (topicList.length != 5) {
                    return;
                }

                int console = Integer.parseInt(topicList[4]);  //0 for all consoles
                logger.info("beep " + message);
                if (message.equals("OFF")) {
                    currentConnection.controllerCommand(CommandMessage.CMD_CONSOLE_ENABLE_DISABLE_BEEPER, 0, console);
                } else if (message.equals("ON")) {
                    currentConnection.controllerCommand(CommandMessage.CMD_CONSOLE_ENABLE_DISABLE_BEEPER, 1, console);
                } else {
                    int beepAmount = Integer.parseInt(message);
                    if (beepAmount < 0 || beepAmount >  6) {
                        return;
                    }

                    currentConnection.controllerCommand(CommandMessage.CMD_CONSOLE_BEEP, beepAmount, console);
                }

                break;

            case "BYPASS":
                if (topicList.length != 5) {
                    return;
                }

                int zone = Integer.parseInt(topicList[4]);

                if (zone < 1) {
                    return;
                }

                if (message.equals("OFF")) {
                    currentConnection.controllerCommand(CommandMessage.CMD_SECURITY_RESTORE_ZONE, 1, zone);
                } else if (message.equals("ON")) {
                    currentConnection.controllerCommand(CommandMessage.CMD_SECURITY_BYPASS_ZONE, 1, zone);
                }

                break;

            default:
                break;
        }

    }

    private void handleThermostat(String[] topicList, String message) throws IOException, OmniNotConnectedException,
                                                    OmniUnknownMessageTypeException, OmniInvalidResponseException {

        int number = Integer.parseInt(topicList[2]);

        if(number < 1) {
            return;
        }

        String service = topicList[3];

        switch (service) {
            case "HEAT_SETPOINT":
                int heatTemperatureF = Integer.parseInt(message);
                int heatConvertedTemp = MessageUtils.FtoOmni(heatTemperatureF);

                currentConnection.controllerCommand(CommandMessage.CMD_THERMO_SET_HEAT_POINT, heatConvertedTemp, number);

                break;
            case "COOL_SETPOINT":
                int coolTemperatureF = Integer.parseInt(message);
                int coolConvertedTemp = MessageUtils.FtoOmni(coolTemperatureF);

                currentConnection.controllerCommand(CommandMessage.CMD_THERMO_SET_COOL_POINT, coolConvertedTemp, number);

                break;
            case "MODE":
                int mode = Integer.parseInt(message);
                if (mode < 0 || mode > 3) {
                    return;
                }

                currentConnection.controllerCommand(CommandMessage.CMD_THERMO_SET_SYSTEM_MODE, mode, number);

                break;
            case "FAN":
                int fanMode = Integer.parseInt(message);
                if (fanMode < 0 || fanMode > 1) {
                    return;
                }

                currentConnection.controllerCommand(CommandMessage.CMD_THERMO_SET_SYSTEM_MODE, fanMode, number);

                break;
            case "HOLD":
                if(message.equals("ON")) {
                    currentConnection.controllerCommand(CommandMessage.CMD_THERMO_SET_HOLD_MODE, 255, number);
                } else {
                    currentConnection.controllerCommand(CommandMessage.CMD_THERMO_SET_HOLD_MODE, 0, number);
                }

                break;

            case "PERCENT":
                int percentage = Integer.parseInt(message);
                if(percentage >= 1 || percentage <= 100) {
                    currentConnection.controllerCommand(CommandMessage.CMD_UNIT_PERCENT, percentage, number);
                }

                break;

            default:
                break;
        }
    }

    private void handleGarage(String[] topicList, String message) throws IOException, OmniNotConnectedException,
                                                    OmniUnknownMessageTypeException, OmniInvalidResponseException {

        int number = Integer.parseInt(topicList[2]);
        int zoneNumber;

        if(number > 8 || number < 1) {
            return;
        }

        String service = topicList[3];

        if (number == 1) {
            number = 385;
            zoneNumber = 14;
        } else if (number == 2) {
            number = 386;
            zoneNumber = 15;
        } else {
            return;
        }


        switch (service) {
            case "OPEN":
                ObjectStatus openstatus = currentConnection.reqObjectStatus(Message.OBJ_TYPE_ZONE, zoneNumber, zoneNumber);
                ZoneStatus[] openzones = (ZoneStatus[]) openstatus.getStatuses();
                if ((openzones[0].getStatus() & 1) != 1) {  //status secure (garage is closed)
                    currentConnection.controllerCommand(CommandMessage.CMD_UNIT_ON, 2, number);  //turns on then off after 2 seconds
                }

                break;

            case "CLOSE":
                ObjectStatus closedstatus = currentConnection.reqObjectStatus(Message.OBJ_TYPE_ZONE, zoneNumber, zoneNumber);
                ZoneStatus[] closedzones = (ZoneStatus[]) closedstatus.getStatuses();
                if ((closedzones[0].getStatus() & 1) == 1) {  //status not ready (garage is open)
                    currentConnection.controllerCommand(CommandMessage.CMD_UNIT_ON, 2, number);  //turns on then off after 2 seconds
                }

                break;

            case "TRIGGER":
                currentConnection.controllerCommand(CommandMessage.CMD_UNIT_ON, 2, number);  //turns on then off after 2 seconds

                break;

            default:
                break;
        }
    }

    private void handleUnit(String[] topicList, String message) throws IOException, OmniNotConnectedException,
                                                    OmniUnknownMessageTypeException, OmniInvalidResponseException {

        int number = Integer.parseInt(topicList[2]);

        if(number < 1) {
            return;
        }

        String service = topicList[3];

        switch (service) {
            case "POWER":
                if(message.equals("ON")) {
                    currentConnection.controllerCommand(CommandMessage.CMD_UNIT_ON, 0, number);
                } else {
                    currentConnection.controllerCommand(CommandMessage.CMD_UNIT_OFF, 0, number);
                }

                break;

            case "PERCENT":
                int percentage = Integer.parseInt(message);
                if(percentage >= 1 || percentage <= 100) {
                    currentConnection.controllerCommand(CommandMessage.CMD_UNIT_PERCENT, percentage, number);
                }

                break;

            default:
                break;
        }
    }

    private void handleAudio(String[] topicList, String message) throws IOException, OmniNotConnectedException,
                                                    OmniUnknownMessageTypeException, OmniInvalidResponseException {

        int number = Integer.parseInt(topicList[2]);

        if(number > 8 || number < 1) {
            return;
        }

        String service = topicList[3];

        switch (service) {
            case "POWER":
                if(message.equals("ON")) {
                    currentConnection.controllerCommand(CommandMessage.CMD_AUDIO_ZONE_SET_ON_AND_MUTE, 1, number);
                } else {
                    currentConnection.controllerCommand(CommandMessage.CMD_AUDIO_ZONE_SET_ON_AND_MUTE, 0, number);
                }

                break;

            case "SOURCE":
                int source = Integer.parseInt(message);
                if(source >= 1 || source <= 8) {
                    currentConnection.controllerCommand(CommandMessage.CMD_AUDIO_ZONE_SET_SOURCE, source, number);
                }

                break;

            case "VOLUME":
                int volume = Integer.parseInt(message);
                if(volume >= 0 || volume <= 100) {
                    currentConnection.controllerCommand(CommandMessage.CMD_AUDIO_ZONE_SET_VOLUME, volume, number);
                }

                break;

            case "MUTE":
                if(message.equals("ON")) {
                    currentConnection.controllerCommand(CommandMessage.CMD_AUDIO_ZONE_SET_ON_AND_MUTE, 3, number);
                } else {
                    currentConnection.controllerCommand(CommandMessage.CMD_AUDIO_ZONE_SET_ON_AND_MUTE, 2, number);
                }

                break;

            default:
                break;
        }
    }

}
