package org.starcoin.subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.starcoin.subscribe.handler.ElasticSearchHandler;
import org.starcoin.subscribe.handler.SubscribeHandler;

@SpringBootApplication
public class SubscribeApplication implements CommandLineRunner {

    private static Logger LOG = LoggerFactory.getLogger(SubscribeApplication.class);

    @Value("${starcoin.seeds}")
    private String[] seeds;

    @Value("${starcoin.network}")
    private String network;

    @Autowired
    private ElasticSearchHandler elasticSearchHandler;

    public static void main(String[] args) {
        LOG.info("STARTING THE APPLICATION");
        SpringApplication.run(SubscribeApplication.class, args);
        LOG.info("APPLICATION FINISHED");
    }

    @Override
    public void run(String... args) {
        LOG.info("EXECUTING : command line runner");
        for (String seed : seeds) {
            Thread handlerThread = new Thread(new SubscribeHandler(seed, network, elasticSearchHandler));
            handlerThread.start();
        }
    }

}