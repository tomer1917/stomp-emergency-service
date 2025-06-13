package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.User;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


//there will be only one instance of this class
public class ConnectionsImpl<T> implements Connections<T> {
    private Map<Integer, ConnectionHandler<T>> connections = new ConcurrentHashMap<>();//connectionId, connectionHandler
    Map<String, Set<Integer>> channelSubscriptions = new ConcurrentHashMap<>();//channel, connectionId
    private ConcurrentHashMap<String, User<T>> users = new ConcurrentHashMap<>();//username, user.  
    private AtomicInteger messageId = new AtomicInteger(0);


    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> connectionHandler = connections.get(connectionId);
        if (connectionHandler != null) {
            connectionHandler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        Set<Integer> subscribers = channelSubscriptions.get(channel);
        if (subscribers != null) {
            for (int connectionId : subscribers) {
                send(connectionId, msg);
            }
        }
    }


    @Override
    public void addHandler(int connectionId, ConnectionHandler<T> handler) {
        connections.put(connectionId, handler);
    }




    @Override
    public void disconnect(int connectionId) {
        //remove the handler, the channel subscriptions and log out the user
        connections.remove(connectionId);
        for (Set<Integer> subscribers : channelSubscriptions.values()) {
            subscribers.remove(connectionId);
        }
        if (users.containsKey(getUsernameById(connectionId))) {
            users.get(getUsernameById(connectionId)).logOut();
        }
    }

    @Override
    public boolean isUserSubscribed(String channel, int connectionId) {
        //check if the subscription exists in the map and if the user is subscribed to the channel
        return channelSubscriptions.containsKey(channel) && users.get(getUsernameById(connectionId)).isSubscribed(channel);
    }

    @Override
    public int getSubscriptionId(String channel, int connectionId) {
        return users.get(getUsernameById(connectionId)).getSubscriptionId(channel);
    }

    @Override
    public List<Integer> getSubscribedId(String channel) {
        return new ArrayList<>(channelSubscriptions.get(channel));
    }

    @Override
    public boolean isUserConnected(String username) {
        // for (User<T> user : users.values()) {
        //     if (user.getUsername().equals(username)) {
        //         return user.isLoggedIn();
        //     }
        // }
        //return true;//should not get here, if it does - it will generate an error just in case
        return users.containsKey(username) && users.get(username).isLoggedIn();
    }

    @Override
    public boolean isValidUser(int connectionId,String username, String password) {
        //check if there is a user with the given username and password
        // for (User<T> user : users.values()) {
        //     if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
        //         return true;
        //     }
        // }
        //return false;

        return users.containsKey(username) && users.get(username).getPassword().equals(password);
    }

    @Override
    public boolean isUniqeUser(String username) {
        // for (User<T> user : users.values()) {
        //     if (user.getUsername().equals(username)) {
        //         return false;
        //     }
        // }
        // return true;
        return !users.containsKey(username);
    }

    @Override
    public void connect(int connectionId,String username) {
        //set the User with username = username to have the new connectionId
        users.get(username).logIn(connectionId, connections.get(connectionId));
        
    }

    @Override
    public void addUser(int connectionId, String username, String password, ConnectionHandler<T> connectionHandler) {
        User<T> user = new User<>(connectionId, username, password, connectionHandler);
        users.put(username,user);
    }

    @Override
    public ConnectionHandler<T> getHandler(int connectionId) {
        return connections.get(connectionId);
    }

    @Override
    public String subscribeUser(int connectionId, String channel, int subscriptionId) {
        String username = getUsernameById(connectionId);
        if (channelSubscriptions.containsKey(channel)) {  
            //the channel exists. check if the user is already subscribed to the channel
            if (users.get(username).isSubscribed(channel)) {
                //the user is already subscribed to the channel
                return "The user is already subscribed to the channel";
            } else {
                //the user is not subscribed to the channel
                users.get(username).subscribe(channel, subscriptionId);
                channelSubscriptions.get(channel).add(connectionId);
            }
        }else{
            //the channel does not exist, add it to the map with subscriptionId
            users.get(username).subscribe(channel, subscriptionId);
            channelSubscriptions.put(channel, ConcurrentHashMap.newKeySet());//create a new set for the channel
            channelSubscriptions.get(channel).add(connectionId);
        }
        return "";
        
    }

    @Override
    public int getMessageId() {
        return messageId.get();

    }

    @Override
    public int incrementMessageId() {
        return messageId.incrementAndGet();
    }

    @Override
    public boolean unsubscribe(int connectionId, int subscribeId) {
        String response = users.get(getUsernameById(connectionId)).unsubscribe(subscribeId);
        if (response == null) {
            return false;
        }
        channelSubscriptions.get(response).remove(connectionId);
        return true;
    }

    
    private String getUsernameById(int connectionId) {
        for (User<T> user : users.values()) {
            if (user.getConnectionId() == connectionId) {
                return user.getUsername();
            }
        }
        return null;
    }
}
