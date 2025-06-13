package bgu.spl.net.srv;
import java.util.List;

public interface Connections<T> {

    void addHandler(int connectionId, ConnectionHandler<T> handler);
    boolean send(int connectionId, T msg);

    void send(String channel, T msg);

    void disconnect(int connectionId);
    boolean isUserSubscribed(String channel, int connectionId);
    int getSubscriptionId(String channel, int connectionId);
    List<Integer> getSubscribedId(String channel);
    void connect(int connectionId,String username);
    boolean isValidUser(int connectionId,String username, String password);
    boolean isUniqeUser(String username);
    void addUser(int connectionId, String username, String password, ConnectionHandler<T> connectionHandler);
    ConnectionHandler<T> getHandler(int connectionId);
    String subscribeUser(int connectionId, String channel, int subscriptionId);

    int getMessageId();
    int incrementMessageId();

    boolean unsubscribe(int connectionId, int subscribeId);
    boolean isUserConnected(String string);




}
