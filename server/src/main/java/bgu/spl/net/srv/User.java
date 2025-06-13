package bgu.spl.net.srv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class User<T>{
    private int connectionId;
    private final String username;
    private final String password;
    private boolean isLoggedIn;
    private ConnectionHandler<T> connectionHandler;
    private ConcurrentHashMap<String, Integer> subscribedchannels = new ConcurrentHashMap<>();//channel, subscriptionId. this map is store the uniqe subscribe id for each user

    public User(int connectionId,String username, String password, ConnectionHandler<T> connectionHandler) {
        this.username = username;
        this.password = password;
        this.isLoggedIn = false;
        this.connectionId = connectionId;
        this.connectionHandler = connectionHandler;
        
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public void logIn(int connectionId, ConnectionHandler<T> connectionHandler) {
        this.isLoggedIn = true;
        this.connectionId = connectionId;
        this.connectionHandler = connectionHandler;
    }

    public void logOut() {
        this.isLoggedIn = false;
        connectionId = -999;
        connectionHandler = null;
        subscribedchannels = new ConcurrentHashMap<>();
    }
    public boolean isSubscribed(String channel){
        return subscribedchannels.containsKey(channel);
    }

    public int getSubscriptionId(String channel) {
        return subscribedchannels.get(channel);
    }

    public void subscribe(String channel, int subscriptionId) {
        subscribedchannels.put(channel, subscriptionId);
    }

    public String unsubscribe(int subscribeId) {
        for (Map.Entry<String, Integer> entry : subscribedchannels.entrySet()) {
            if (entry.getValue() == subscribeId) {
                subscribedchannels.remove(entry.getKey());
                //return the channel name
                return entry.getKey();
            }
        }
        return null;

    }

    public int getConnectionId() {
        return connectionId;
    }

}
