/*
 * Copyright (c) 1997-2018 InfoReach, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * InfoReach ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with InfoReach.
 *
 * CopyrightVersion 2.0
 */

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.inforeach.eltrader.engine.api.grpc.*;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;

import static com.inforeach.eltrader.engine.api.grpc.FIXEngineCommon.FIXTag.*;
import static com.inforeach.eltrader.engine.api.grpc.FIXEngineCommon.FIXFields;
import static com.inforeach.eltrader.engine.api.grpc.FIXEngineCommon.HandlInst.HandlInst_AutomatedExecutionOrderPrivateNoBrokerIntervention_VALUE;
import static com.inforeach.eltrader.engine.api.grpc.FIXEngineCommon.OrdType.OrdType_Market_VALUE;
import static com.inforeach.eltrader.engine.api.grpc.FIXEngineCommon.Side.Side_Buy_VALUE;
import static com.inforeach.eltrader.engine.api.grpc.FIXEngineCommon.MessageIds.*;

/**
 * Sample client app for FIXEngine remote gRPC API
 *
 * The example shows usage of the following features:
 * <ul>
 * <li> Obtaining FIX connection list
 * <li> Subscribing for messages and connection state
 * <li> Obtaining connection state
 * <li> Starting client FIX connection
 * <li> Disconnecting FIX connection
 * <li> Retrieving FIX messages from DB
 * <li> Sending FIX messages with subgroups
 * </ul>
 */
public class FIXEngineAppGrpc
{
    private static final Logger LOGGER = Logger.getLogger(FIXEngineAppGrpc.class.getName());

    private static final String GRPC_HOST = "localhost";
    private static final int GRPC_PORT = 8089;
    private static final String USER = "demo";
    private static final String PASSWORD = "";

    private static FIXEngineGrpc.FIXEngineBlockingStub CLIENT;

    static abstract class AbstractStreamObserver<EVENT> implements StreamObserver<EVENT>
    {
        @Override
        public void onError(Throwable t)
        {
            LOGGER.log(Level.SEVERE, this.getClass().getSimpleName() + " error: " + t.getMessage(), t);
        }

        @Override
        public void onCompleted()
        {
            LOGGER.log(Level.FINE, this.getClass().getSimpleName() + " completed");
        }

        @Override
        public void onNext(EVENT event)
        {
            LOGGER.log(Level.FINE, this.getClass().getSimpleName() + " received event: " + event);
        }
    }

    public static void main(String[] args)
    {
        try
        {
            ManagedChannel channel = NettyChannelBuilder.forAddress(GRPC_HOST, GRPC_PORT)
                .sslContext(GrpcSslContexts.forClient().trustManager(new FileInputStream(new File(System.getProperty("user.dir"), "cert.pem"))).build())
//                .usePlaintext(true)
                .build();

            FIXEngineGrpc.FIXEngineStub asyncClient = FIXEngineGrpc.newStub(channel);
            CLIENT = FIXEngineGrpc.newBlockingStub(channel);

            //START SNIPPET: Login
            CLIENT.login(FIXEngineRequests.LoginRequest.newBuilder().setUser(USER).setPassword(PASSWORD).build());
            //END SNIPPET: Login

            //START SNIPPET: Get FIX connection list
            FIXEngineRequests.GetConnectionsResponse connections = CLIENT.getConnections(FIXEngineRequests.GetConnectionsRequest.getDefaultInstance());
            //END SNIPPET: Get FIX connection list
            LOGGER.log(Level.FINE, "Connection list: ");
            connections.getConnectionList().forEach(connection -> LOGGER.log(Level.FINE, toString(connection)));

            final String clientConnection = "tcl";

            //START SNIPPET: Subscribe for messages and connection state
            FIXEngineRequests.SubscribeRequest messagesRequest = FIXEngineRequests.SubscribeRequest.newBuilder()
                .addConnectionName(clientConnection)
                .putLastConsumedAppMessageFromCounterPartyId(clientConnection, AUTO_DETECT_LAST_CONSUMED_MESSAGE_ID_VALUE)
                .putLastConsumedAppMessageToCounterPartyId(clientConnection, AUTO_DETECT_LAST_CONSUMED_MESSAGE_ID_VALUE)
                .setSubscribeForConnectDisconnect(true)
                .build();
            StreamObserver<FIXEngineRequests.SubscribeRequest> subscribeForFIXMessages = asyncClient.subscribe(new ConnectionEventObserver());
            subscribeForFIXMessages.onNext(messagesRequest);
            //END SNIPPET: Subscribe for messages and connection state

            //START SNIPPET: Get connection state
            FIXEngineCommon.ConnectionState state = CLIENT.getConnectionState(FIXEngineRequests.GetStateRequest.newBuilder()
                .setConnectionName(clientConnection)
                .build());
            //END SNIPPET: Get connection state
            LOGGER.log(Level.FINE, "Connection '" + clientConnection + "' state: " + toString(state));
            
            //START SNIPPET: Start client FIX connection
            CLIENT.connect(FIXEngineRequests.ConnectRequest.newBuilder()
                .setConnectionName(clientConnection)
                .setLogonParameters(FIXFields.newBuilder()
                    .putStringFields(FIXTag_Username_VALUE, "user")
                    .putStringFields(FIXTag_Password_VALUE, "pass")
                    .build())
                .build());
            //END SNIPPET: Start client FIX connection

            LOGGER.log(Level.FINE, "Waiting 10 seconds...");
            Thread.sleep(10_000);

            //START SNIPPET: Disconnect FIX connection
            CLIENT.disconnect(FIXEngineRequests.DisconnectRequest.newBuilder()
                .setConnectionName(clientConnection)
                .setLogoutText("logged out")
                .build());
            //END SNIPPET: Disconnect FIX connection

            //START SNIPPET: Retrieve FIX messages from DB
            final Iterator<FIXFields> fixFieldsIterator = CLIENT.retrieveMessages(FIXEngineRequests.RetrieveMessagesRequest.newBuilder()
                .setConnectionName(clientConnection)
                .addStartMessageId(INITIAL_APP_MESSAGE_FROM_COUNTER_PARTY_ID_VALUE)
                .build());
            LOGGER.log(Level.FINE, "Retrieved messages: ");
            while (fixFieldsIterator.hasNext())
            {
                LOGGER.log(Level.FINE, toString(fixFieldsIterator.next()));
            }
            //END SNIPPET: Retrieve FIX messages from DB

            LOGGER.log(Level.FINE, "Closing channel...");
            subscribeForFIXMessages.onCompleted();
            channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);

            LOGGER.log(Level.FINE, "Exiting...");
            System.exit(0);
        }
        catch (StatusRuntimeException ex)
        {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            final Metadata trailers = ex.getTrailers();
            String exceptionClass = trailers.get(Metadata.Key.of("ExceptionClass", Metadata.ASCII_STRING_MARSHALLER));
            String errorCode = trailers.get(Metadata.Key.of("ErrorCode", Metadata.ASCII_STRING_MARSHALLER));
            if (exceptionClass != null || errorCode != null)
            {
                LOGGER.log(Level.SEVERE, "    Exception class: " + exceptionClass + " errorCode: " + errorCode);
            }
            final String childExceptionCountString = trailers.get(Metadata.Key.of("ChildExceptionsCount", Metadata.ASCII_STRING_MARSHALLER));
            int childExceptionCount = childExceptionCountString != null ? Integer.parseInt(childExceptionCountString) : 0;
            for (int i = 0; i < Math.min(childExceptionCount, 10); i++) //only 10 child exceptions have details sent remotely
            {
                String childException = trailers.get(Metadata.Key.of("ChildExceptionMessage_" + i, Metadata.ASCII_STRING_MARSHALLER));
                LOGGER.log(Level.SEVERE,  "        ChildException " + i + " " + childException);
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private static void sendNewOrderMessage(String connectionName, String clOrdId)
    {
        //START SNIPPET: Send FIX message with subgroups
        FIXFields message = FIXFields.newBuilder()
            .putStringFields(FIXTag_MsgType_VALUE, "D")
            .putStringFields(FIXTag_ClOrdID_VALUE, clOrdId)
            .putNumericFields(FIXTag_OrdType_VALUE, OrdType_Market_VALUE)
            .putStringFields(FIXTag_Symbol_VALUE, "IBM")
            .putNumericFields(FIXTag_Side_VALUE, Side_Buy_VALUE)
            .putNumericFields(FIXTag_OrderQty_VALUE, 100)
            .putNumericFields(FIXTag_TransactTime_VALUE, System.currentTimeMillis())
            .putNumericFields(FIXTag_HandlInst_VALUE, HandlInst_AutomatedExecutionOrderPrivateNoBrokerIntervention_VALUE)
            .putSubgroups(FIXTag_NoAllocs_VALUE, FIXFields.RepeatingGroup.newBuilder()
                .addSubgroup(FIXFields.newBuilder().putStringFields(FIXTag_AllocAccount_VALUE, "ACCOUNT_1"))
                .addSubgroup(FIXFields.newBuilder().putStringFields(FIXTag_AllocAccount_VALUE, "ACCOUNT_2"))
                .build())
            .build();
        FIXEngineRequests.SendMessageRequest request = FIXEngineRequests.SendMessageRequest.newBuilder()
            .setConnectionName(connectionName)
            .setMessage(message)
            .build();
        CLIENT.sendMessage(request);
        //END SNIPPET: Send FIX message with subgroups
    }

    private static String toString(FIXEngineRequests.GetConnectionsResponse.Connection connection)
    {
        return connection.getName() + " [" + toString(connection.getState()) + "]";
    }

    private static String toString(FIXEngineCommon.ConnectionState state)
    {
        return state.getIsConnected() ? "connected" : "disconnected"
               + (state.getIsOnHold() ? ", on hold" : "")
               + (state.getIsMuted() ? ", muted" : "");
    }

    private static String toString(FIXFields fields)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        fields.getStringFieldsMap().forEach((k, v) -> builder.append(k).append("=").append(v).append(", "));
        fields.getNumericFieldsMap().forEach((k, v) -> builder.append(k).append("=").append(v).append(", "));
        fields.getSubgroupsMap().forEach((k, v) -> builder.append(k).append("=").append(toString(v)).append(", "));
        if (builder.length() > 1)
        {
            builder.delete(builder.length() - 2, builder.length());
        }
        builder.append("}");
        return builder.toString();
    }

    private static String toString(FIXFields.RepeatingGroup group)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        group.getSubgroupList().forEach(subgroup -> builder.append(toString(subgroup)).append(", "));
        if (builder.length() > 1)
        {
            builder.delete(builder.length() - 2, builder.length());
        }
        builder.append("]");
        return builder.toString();
    }

    private static boolean isMessageFromCounterparty(FIXFields message)
    {
        final Double value = message.getNumericFieldsMap().get(FIXTag_IsFromCounterParty_VALUE);
        return value != null && value.intValue() == 'Y';
    }

    private static class ConnectionEventObserver extends AbstractStreamObserver<FIXEngineEvents.ConnectionEvent>
    {
        @Override
        public void onNext(FIXEngineEvents.ConnectionEvent engineEvent)
        {
            String connectionName = engineEvent.getConnectionName();

            switch (engineEvent.getEventCase())
            {
                case SUBSCRIBED:
                    LOGGER.log(Level.FINE, "Subscription for connection " + connectionName + " is started");
                    break;

                case MESSAGE:
                    FIXEngineCommon.FIXFields fields = engineEvent.getMessage().getFields();
                    
                    LOGGER.log(Level.FINE, "Message " + (isMessageFromCounterparty(fields) ? "received" : "sent") 
                         + " via connection " + connectionName + ": " + FIXEngineAppGrpc.toString(fields));
                    break;
                
                case CONNECTED:
                    LOGGER.log(Level.FINE, "Connection " + connectionName + " is connected");

                    sendNewOrderMessage(connectionName,  "M" + System.currentTimeMillis());
                    sendNewOrderMessage(connectionName,  "T" + System.currentTimeMillis());
                    sendNewOrderMessage(connectionName,  "M" + System.currentTimeMillis());

                    break;

                case DISCONNECTED:
                    LOGGER.log(Level.FINE, "Connection " + connectionName + " is disconnected. Reason: " + engineEvent.getDisconnected().getReason());
                    break;
            }
        }
    }
}
