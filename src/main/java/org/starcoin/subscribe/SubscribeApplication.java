package org.starcoin.subscribe;

import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import io.reactivex.Flowable;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starcoin.subscribe.api.StarcoinSubscriber;
import org.starcoin.subscribe.api.TransactionRPCClient;
import org.starcoin.subscribe.bean.PendingTransactionNotification;
import org.web3j.protocol.websocket.WebSocketService;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;

@SpringBootApplication
public class SubscribeApplication implements CommandLineRunner {

    private static Logger LOG = LoggerFactory.getLogger(SubscribeApplication.class);

    public static void main(String[] args) {
        LOG.info("STARTING THE APPLICATION");
        SpringApplication.run(SubscribeApplication.class, args);
        LOG.info("APPLICATION FINISHED");
    }

    @Override
    public void run(String... args) throws ConnectException, MalformedURLException, JSONRPC2SessionException {
        LOG.info("EXECUTING : command line runner");
        WebSocketService service = new WebSocketService("ws://localhost:9870",true);
        service.connect();
        StarcoinSubscriber subscriber = new StarcoinSubscriber(service);
        Flowable<PendingTransactionNotification> flowableTxns = subscriber.newPendingTransactionsNotifications();
        TransactionRPCClient rpc = new TransactionRPCClient(new URL("http://localhost:9850"));

        for(PendingTransactionNotification notifications:flowableTxns.blockingIterable()){
            for(String notification:notifications.getParams().getResult()){
                rpc.getTransaction(notification);
            }
        }
    }

}