/*
 * Copyright 2015-2016 Jeeva Kandasamy (jkandasa@gmail.com)
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mycontroller.standalone.gateway.serialport;

import org.mycontroller.standalone.AppProperties.STATE;
import org.mycontroller.standalone.gateway.model.GatewaySerial;
import org.mycontroller.standalone.message.RawMessage;
import org.mycontroller.standalone.message.RawMessageQueue;
import org.mycontroller.standalone.utils.McUtils;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Jeeva Kandasamy (jkandasa)
 * @since 0.0.1
 */
@Slf4j
public class SerialDataListenerjSerialComm implements SerialPortDataListener {

    private SerialPort serialPort;
    private GatewaySerial gateway = null;
    private StringBuilder message = new StringBuilder();
    private boolean failedStatusWritten = false;

    public SerialDataListenerjSerialComm(SerialPort serialPort, GatewaySerial gateway) {
        this.serialPort = serialPort;
        this.gateway = gateway;
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
            return;
        }
        try {
            byte[] buffer = new byte[serialPort.bytesAvailable()];
            serialPort.readBytes(buffer, buffer.length);
            for (byte b : buffer) {
                if ((b == SerialPortCommon.MESSAGE_SPLITTER) && message.length() > 0) {
                    String toProcess = message.toString();
                    _logger.debug("Received a message:[{}]", toProcess);
                    //Send Message to message factory
                    RawMessageQueue.getInstance().putMessage(RawMessage.builder()
                            .gatewayId(gateway.getId())
                            .data(toProcess)
                            .networkType(gateway.getNetworkType())
                            .timestamp(System.currentTimeMillis())
                            .build());
                    message.setLength(0);
                } else if (b != SerialPortCommon.MESSAGE_SPLITTER) {
                    _logger.trace("Received a char:[{}]", ((char) b));
                    message.append((char) b);
                } else if (message.length() >= MYCSerialPort.SERIAL_DATA_MAX_SIZE) {
                    _logger.warn(
                            "Serial receive buffer size reached to MAX level[{} chars], "
                                    + "Now clearing the buffer. Existing data:[{}]",
                            MYCSerialPort.SERIAL_DATA_MAX_SIZE, message.toString());
                    message.setLength(0);
                } else {
                    _logger.debug("Received MESSAGE_SPLITTER and current message length is ZERO! Nothing to do");
                }
            }
            failedStatusWritten = false;
        } catch (Exception ex) {
            if (ex.getMessage() != null) {
                _logger.error("Exception, ", ex);
            }
            if (!failedStatusWritten) {
                gateway.setStatus(STATE.DOWN, "ERROR: " + ex.getMessage());
                failedStatusWritten = true;
                _logger.error("Exception, ", ex);
            }
            message.setLength(0);
            try {
                //If serial port removed in between throws 'java.lang.NegativeArraySizeException: null' continuously
                //This continuous exception eats CPU heavily, to reduce CPU usage on this state added Thread.sleep
                Thread.sleep(McUtils.TEN_MILLISECONDS);
            } catch (InterruptedException tE) {
                _logger.error("Exception,", tE);
            }
        }

    }
}
