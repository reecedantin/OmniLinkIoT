/**
 *
 *
 */


package com.dantin.omnilinkiot;

import java.lang.Runnable;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitaldan.jomnilinkII.Connection;
import com.digitaldan.jomnilinkII.Message;
import com.digitaldan.jomnilinkII.OmniInvalidResponseException;
import com.digitaldan.jomnilinkII.OmniNotConnectedException;
import com.digitaldan.jomnilinkII.OmniUnknownMessageTypeException;
import com.digitaldan.jomnilinkII.MessageTypes.ObjectStatus;

public class AllStatusRunner implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private Connection currentConnection;
    private NotificationProcessor notifProcessor;
    private int[] max_devices;

    public AllStatusRunner(Connection c, NotificationProcessor notifP, int[] max_devices) {
        this.currentConnection = c;
        this.notifProcessor = notifP;
        this.max_devices = max_devices;
    }

    public void run(){
        try {
            logger.info("received all status");
            sendAll();
        } catch (IOException | OmniNotConnectedException |
                            OmniUnknownMessageTypeException | OmniInvalidResponseException e ) {
            logger.error("couldn't send all statuses", e);
        }
    }

    private void sendAll() throws IOException, OmniNotConnectedException,
                        OmniUnknownMessageTypeException, OmniInvalidResponseException  {

        int max_zones = max_devices[0];
        int max_units = max_devices[1];
        int max_areas = max_devices[2];
        int max_thermos = max_devices[3];
        int max_audio_zones = max_devices[4];

        ObjectStatus status = currentConnection.reqObjectStatus(Message.OBJ_TYPE_UNIT, 1, max_units);
        notifProcessor.objectStatusNotification(status);
        status = currentConnection.reqObjectStatus(Message.OBJ_TYPE_ZONE, 1, max_zones);
        notifProcessor.objectStatusNotification(status);
        status = currentConnection.reqObjectStatus(Message.OBJ_TYPE_AREA, 1, max_areas);
        notifProcessor.objectStatusNotification(status);
        status = currentConnection.reqObjectStatus(Message.OBJ_TYPE_THERMO, 1, max_thermos, true);
        notifProcessor.objectStatusNotification(status);
        status = currentConnection.reqObjectStatus(Message.OBJ_TYPE_AUDIO_ZONE, 1, max_audio_zones);
        notifProcessor.objectStatusNotification(status);

    }
}
