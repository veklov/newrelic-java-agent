/*
 *
 *  * Copyright 2020 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.nr.instrumentation.akka22;

import akka.actor.ActorRef;
import com.newrelic.agent.bridge.AgentBridge;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AkkaUtil {

    private static final String tempActorName = "temp";

    private static final Set<String> tickClasses = new HashSet<>(Arrays.asList(
            "akka.remote.RemoteWatcher$Heartbeat$", "akka.remote.RemoteWatcher$HeartbeatTick$",
            "akka.remote.RemoteWatcher$ReapUnreachableTick$", "spray.io.TickGenerator$Tick$"));

    // Loggers
    private static final String defaultLoggerPrefix = "akka.event.Logging";
    private static final String slf4jLoggerPrefix = "akka.event.slf4j.Slf4jLogger";

    // Subscriber
    private static final String akkaStreamPrefix = "akka.stream";

    private static final Pattern allDigitsPattern = Pattern.compile("\\d+");
    private static final String akkaIOTCPIncomingConnection = "akka.io.TcpIncomingConnection";
    private static final String sprayCanHttpServerConnection = "spray.can.server.HttpServerConnection";

    public static boolean isHeartBeatMessage(String name) {
        return tickClasses.contains(name);
    }

    public static boolean isLogger(String receiver) {
        if (receiver.startsWith(defaultLoggerPrefix)) {
            return true;
        }
        if (receiver.startsWith(slf4jLoggerPrefix)) {
            return true;
        }
        return false;
    }

    public static boolean isTempActor(ActorRef actorRef) {
        return actorRef.path().parent().name().equals(tempActorName);
    }

    public static boolean isAkkaStream(String messageClassName) {
        if (messageClassName.startsWith(akkaStreamPrefix)) {
            return true;
        }
        return false;
    }

    public static String getActor(ActorRef actorRef) {
        if (actorRef == null || actorRef.path() == null) {
            return "";
        } else if (isTempActor(actorRef)) {
            return tempActorName;
        } else if (actorRef.path().parent().name().equals("/")) {
            // Report actor system name, not deadletters.
            // Should be equivalent to actorRef.path().parent().equals(actorRef.path().root()
            return actorRef.path().parent().address().system();
        } else {
            return actorRef.path().name();
        }
    }

    public static void recordTellMetric(String receiverName, String senderName, String messageClass) {
        if (senderName == null || receiverName == null) {
            return;
        }

        if (isHeartBeatMessage(messageClass)) {
            return;
        }

        if (isLogger(receiverName)) {
            return;
        }

        Matcher matcher = allDigitsPattern.matcher(senderName);
        if (receiverName.equals(akkaIOTCPIncomingConnection) && matcher.matches()) {
            senderName = tempActorName;
        } else if (receiverName.equals(sprayCanHttpServerConnection) && matcher.matches()) {
            senderName = tempActorName;
        } else if (senderName.startsWith("$")) {
            senderName = tempActorName;
        }

        AgentBridge.getAgent().getTracedMethod().setMetricName("Akka", senderName, "tell", receiverName);
    }
}
