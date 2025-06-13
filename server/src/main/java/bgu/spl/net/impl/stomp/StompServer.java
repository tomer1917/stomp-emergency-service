package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        if (args == null || args.length ==0){
            //just for testing
            args = new String[2];
            args[0] = "7777";
            args[1] = "tpc";
        }


        int port = Integer.parseInt(args[0]);
        String serverType = args[1];
        Server<String> server;
        if (serverType.equals("tpc")) {
            server = Server.threadPerClient(port, StompMessagingProtocolImpl::new, StompEncoderDecoder::new);
        } else if (serverType.equals("reactor")){
            server = Server.reactor(Runtime.getRuntime().availableProcessors(), port, StompMessagingProtocolImpl::new, StompEncoderDecoder::new);
        }
        else {
            System.out.println("Invalid server type");
            return;
        }
        server.serve();
    }
}