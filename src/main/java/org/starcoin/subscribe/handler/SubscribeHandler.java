package org.starcoin.subscribe.handler;

import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starcoin.api.StarcoinSubscriber;
import org.starcoin.api.TransactionRPCClient;
import org.starcoin.bean.PendingTransaction;
import org.starcoin.bean.PendingTransactionNotification;
import org.web3j.protocol.websocket.WebSocketService;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;

public class SubscribeHandler implements Runnable {

    private static Logger LOG = LoggerFactory.getLogger(SubscribeHandler.class);

    private String host;

    private String network;

    private ElasticSearchHandler elasticSearchHandler;

    public SubscribeHandler(String host, String network, ElasticSearchHandler elasticSearchHandler) {
        this.host = host;
        this.network = network;
        this.elasticSearchHandler = elasticSearchHandler;
    }

    @Override
    public void run() {
        try {
            WebSocketService service = new WebSocketService("ws://" + host + ":9870", true);
            service.connect();
            StarcoinSubscriber subscriber = new StarcoinSubscriber(service);
            Flowable<PendingTransactionNotification> flowableTxns = subscriber.newPendingTransactionsNotifications();
            TransactionRPCClient rpc = new TransactionRPCClient(new URL("http://" + host + ":9850"));

            for (PendingTransactionNotification notifications : flowableTxns.blockingIterable()) {
                for (String notification : notifications.getParams().getResult()) {
                    LOG.info("notification: {}", notification);
                    PendingTransaction transaction = rpc.getPendingTransaction(notification);
                    elasticSearchHandler.saveTransaction(network, transaction);
                }
            }
        } catch (ConnectException | MalformedURLException | JSONRPC2SessionException e) {
            LOG.info("handle subscribe exception", e);
        }
    }
}
