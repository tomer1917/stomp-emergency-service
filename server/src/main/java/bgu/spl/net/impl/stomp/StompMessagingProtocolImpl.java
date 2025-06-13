package bgu.spl.net.impl.stomp;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;


//T is the protocol we are using (TPC/Reactor)
public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    int connectionId;
    Connections<String> connections;
    private boolean shouldTerminate = false;
    private final String HOST = "stomp.cs.bgu.ac.il";//the host of the server
    private final String VERSION = "1.2";//the host of the server


    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public String process(String message) {
        //identify the command type and extract the headers + body

        List<String> messageByCommands = new ArrayList<>(Arrays.asList(message.split("\n")));

        String command = messageByCommands.remove(0);
        
        HashMap<String, String> headers =new HashMap<>();
        boolean bodyReached = false;

        StringBuilder bodyBuiler = new StringBuilder();
        String body = "";

        for (String line : messageByCommands) {
            if (bodyReached) {
                if(line.equals("\u0000"))
                {
                    //end of message
                    break;
                }
                else{
                    if (bodyBuiler.length() == 0) 
                        bodyBuiler.append(line);
                    else
                        bodyBuiler.append("\n").append(line);
                }
            }
            else if (line.equals("")) {
                bodyReached = true;
            }
            else{
                String[] headerLine = line.split(":");
                headers.put(headerLine[0], headerLine[1]);
            }

        }
        body = bodyBuiler.toString();


        //System.out.println("Command: "+command);//for testing client
        
        String response = null;
        boolean shouldLogoutUser = true;
        //the function will return what the server should send back to the client (Error/Receipt)
        switch (command) {
            //client frames
            case "CONNECT":
                response = connectCommand(body, headers, message);
                shouldLogoutUser = false;
                break;
            case "SEND":
                response = sendCommand(body, headers, message);
                break;
            case "SUBSCRIBE":
                response = subscribeCommand(body, headers, message);
                break;
            case "UNSUBSCRIBE":
                response = unsubscribeCommand(body, headers, message);
                break;
            case "DISCONNECT":
                response = disconnectCommand(body, headers, message);
                break;
            default:
                response = generateError(message, "Unknown command", headers.get("receipt"));
                break;
        }
        //detect the response type by the first word
        if (response.equals("")) {
            //do nothing
        }
        else{
            switch(response.split("\n")[0]){
                case "DISCONNECT":
                    //send disconnect and disconnect
                    // connections.send(connectionId, response);
                    // ConnectionHandler<String> handler1 = connections.getHandler(connectionId);
                    // this.shouldTerminate = true;
                    // connections.disconnect(connectionId);
                    // try {
                    //     handler1.close();
                    // } catch (IOException ex) {
                    //     ex.printStackTrace();
                    // }
                    // response = null;


                    //disconnect the user without closing the program
                    connections.disconnect(connectionId);
                    break;

                case "ERROR":
                    //send error and disconnect
                    connections.send(connectionId, response);
                    ConnectionHandler<String> handler = connections.getHandler(connectionId);
                    this.shouldTerminate = true;


                    if (shouldLogoutUser) {// during failed connect the user is not logged in
                        connections.disconnect(connectionId);
                    }

                    try {;
                        handler.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    response = null;
                    break;

                case "RECEIPT":
                    //send receipt
                    //connections.send(connectionId, response);
                    break;

                default:
                    //should not enter here
                    break;
            }
        } 
        return response;       
    }

    
    private String checkStructure(String[] fixedHandlers, HashMap<String,String> headers,String commandType,String message){
        boolean wrongStructure = false;
        String errorDescription = "";
        for (String header : fixedHandlers) {
            if (!headers.containsKey(header)) {
                wrongStructure = true;
                errorDescription = "Did not contain a " +header+"header,\nwhich is REQUIRED for "+commandType.toLowerCase()+" propagation.";
                break;
            }else if (headers.get(header).equals("")){
                wrongStructure = true;
                errorDescription = "The " +header+" header is empty,\nwhich is REQUIRED for "+commandType.toLowerCase()+" propagation.";
                break;
            }
        }
        if(wrongStructure ){
            //send an error
            String errorMSG = "ERROR\n";
            if (headers.containsKey("receipt")) {
                errorMSG+= "receipt-id: " + headers.get("receipt")+"\n";
            }
            errorMSG+= "message: malformed frame received\n\nThe message:\n----\n" +message+"\n----\n"+ errorDescription + "\u0000";
            return errorMSG;
        }
        return "";
        
    }



    private String connectCommand(String body, HashMap<String,String> headers,String message){
        String[] connectStructure = {"accept-version","host", "login","passcode"};
        String errorMSG = checkStructure(connectStructure,headers,"CONNECT",message);
        if(!errorMSG.equals(""))
            return errorMSG;

        String response = "";
        //identify the case: wrong host, new username, wrong password, already connected,wrong version, everything is correct
        if (!headers.get("host").equals(HOST)) {
            //wrong host
            response = generateError(message,"Wrong host",headers.get("receipt"));
        }
        else if (!headers.get("accept-version").equals(VERSION)) {
            //wrong version
            response = generateError(message,"Wrong version",headers.get("receipt"));

        }
        else if (connections.isUniqeUser(headers.get("login"))) {
            //new user
            //change the user to connected, send a connected message
            connections.addUser(connectionId, headers.get("login"), headers.get("passcode"), connections.getHandler(connectionId));
            connections.connect(connectionId,headers.get("login"));
            HashMap<String,String> connected = new HashMap<>();
            connected.put("version", VERSION);
            response = generateMessage(connected, "CONNECTED", "");
        }
        else{
             if (connections.isValidUser(connectionId,headers.get("login"), headers.get("passcode"))) {
                if (connections.isUserConnected(headers.get("login"))) {
                    //already connected
                    response = generateError(message,"User already logged in",headers.get("receipt"));

                }
                else{
                    //everything is correct
                    //change the user to connected, send a connected message
                    connections.connect(connectionId,headers.get("login"));
                    HashMap<String,String> connected = new HashMap<>();
                    connected.put("version", VERSION);
                    response = generateMessage(connected, "CONNECTED", "");
                }
             }
             else{
                //wrong password
                response = generateError(message,"Wrong password",headers.get("receipt"));
             }
        }
        return response;
    }
    

    
    private String disconnectCommand(String body, HashMap<String,String> headers,String message){
        String[] disconnectStructure = {"receipt"};
        String errorMSG = checkStructure(disconnectStructure,headers,"disconnect",message);
        if(!errorMSG.equals(""))
            return errorMSG;


        connections.disconnect(connectionId);
        //send a receipt to the user
        HashMap<String,String> receipt = new HashMap<>();
        receipt.put("receipt-id", headers.get("receipt"));
        return generateMessage(receipt, "RECEIPT", "");      
    }
    
    
    private String sendCommand(String body, HashMap<String,String> headers,String message){
        String[] sendStructure = {"destination"};
        String errorMSG = checkStructure(sendStructure,headers,"send",message);
        if(!errorMSG.equals(""))
            return errorMSG;

        String destination = headers.get("destination").substring(1);//remove the first character which is '/'
        //check if the user is subscribed to the destination
        if (connections.isUserSubscribed(destination, connectionId)) {
            //send the message to the destination

            //get all the subscribers
            ArrayList<Integer> subscribers = new ArrayList<>(connections.getSubscribedId(destination));
            int messageId = connections.getMessageId();
            connections.incrementMessageId();
            for (int subscriber : subscribers) {
                int subscriptionId = connections.getSubscriptionId(destination,subscriber);//get the unique subscription id
                //convert body to MESSAGE frame
                String msg = bodyToMessage(body, destination,subscriptionId, messageId);
                connections.send(subscriber, msg);
            }
            //send a receipt to the user
            if (headers.containsKey("receipt")) {
                String receiptID = headers.get("receipt");
                String receiptMSG = "RECEIPT\nreceipt-id: " + receiptID + "\n";
                return receiptMSG;
            }
        }else{
            //send an error
            errorMSG = generateError(message,"User is not subscribed to the destination",headers.get("receipt"));
            return errorMSG;
        }

        return "";
    }
    


    private String subscribeCommand(String body, HashMap<String,String> headers,String message){
        String[] subscribeStructure = {"destination", "id"};
        String errorMSG = checkStructure(subscribeStructure,headers,"SUBSCRIBE",message);
        if(!errorMSG.equals(""))
            return errorMSG;

        //subscribe the user to the destination and save the uniqe subscribtion id
        String destination = headers.get("destination");
        //destination = destination.substring(1);//remove the first character which is '/'
        int subscriptionId = Integer.parseInt(headers.get("id"));
        String isSubscribed = connections.subscribeUser(connectionId, destination, subscriptionId);

        String response = "";
        if (isSubscribed.equals("")) {
            //send a receipt to the user
            if (headers.containsKey("receipt")) {
                String receiptID = headers.get("receipt");
                response = "RECEIPT\nreceipt-id: " + receiptID + "\n";
            }
        }
        else{
            //send an error
            response = generateError(message,isSubscribed,headers.get("receipt"));
        }
        return response;
    }


    private String unsubscribeCommand(String body, HashMap<String,String> headers,String message){
        String[] unsubscribeStructure = {"id"};
        String errorMSG = checkStructure(unsubscribeStructure,headers,"UNSUBSCRIBE",message);
        if(!errorMSG.equals(""))
            return errorMSG;
        

        String response = "";
        //check if the user is subscribed to the destination
        boolean isUnsubscribed = connections.unsubscribe(connectionId, Integer.parseInt(headers.get("id")));
        if (isUnsubscribed) {
            //unsubscribe successful
            if (headers.containsKey("receipt")) {
                String receiptID = headers.get("receipt");
                response = "RECEIPT\nreceipt-id: " + receiptID + "\n";
            }
        }
        else{
            //send an error
            response = generateError(message,"User is not subscribed to the destination",headers.get("receipt"));
        }
        return response;
        
    }

    
    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }


    private String bodyToMessage(String body, String destination,int subscriptionId, int messageId){ 
        //add / to destination
        String msg =  "MESSAGE\nsubscription:" + subscriptionId + "\nmessage-id:" + messageId + "\ndestination:/" + destination + "\n\n" + body+"\n" ;
        return msg;
    }

    private String generateError(String message,String description, String receipt){
        String errorMSG = "ERROR\n";
        if (receipt != null) {
            errorMSG+= "receipt-id: " + receipt+"\n";
        }
        errorMSG+= "The message: \n-----\n"+message+"\n-----\n"+description;
        return errorMSG;

    }

    private String generateMessage(HashMap<String,String> headers,String commandType,String body){
        String message = "" + commandType + "\n";
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            message = message + entry.getKey() + ":" + entry.getValue() + "\n";
        }
        message = message + "\n" + body;
        return message;
    }
}