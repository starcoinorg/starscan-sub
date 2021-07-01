package org.starcoin.subscribe.handler;

import com.alibaba.fastjson.JSON;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.starcoin.subscribe.bean.PendingTransaction;

import java.io.IOException;

@Service
public class ElasticSearchHandler {

    public static final String TRANSACTION_INDEX = "txn_infos";
    private static final String PENDING_TXN_INDEX = "pending_txns";
    private static Logger LOG = LoggerFactory.getLogger(ElasticSearchHandler.class);
    @Autowired
    private RestHighLevelClient client;

    public void saveTransaction(String network, PendingTransaction transaction) {
        if (transaction == null) {
            return;
        }
        if (!checkExists(network, transaction)) {
            addToEs(network, transaction);
        } else {
            LOG.warn("transaction exist: {}", transaction.getTransactionHash());
        }

    }

    private boolean checkExists(String network, PendingTransaction transaction) {
        try {
            GetRequest getRequest = new GetRequest(ServiceUtils.getIndex(network, TRANSACTION_INDEX), transaction.getTransactionHash());
            GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);
            if (getResponse.isExists()) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            LOG.warn("access es failed", e);
            return false;
        }
    }

    private void addToEs(String network, PendingTransaction transaction) {
        try {
            IndexRequest request = new IndexRequest(ServiceUtils.getIndex(network, PENDING_TXN_INDEX));
            request.id(transaction.getTransactionHash());

            String doc = JSON.toJSONString(transaction);
            LOG.info("doc to es is "+doc);
            request.source(doc, XContentType.JSON);

            IndexResponse indexResponse = null;
            try {
                indexResponse = client.index(request, RequestOptions.DEFAULT);
            } catch (ElasticsearchException e) {
                if (e.status() == RestStatus.CONFLICT) {
                    LOG.error("duplicate entry\n" + e.getDetailedMessage());
                }
                LOG.error("index error", e);
            }

            if (indexResponse != null) {
                if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
                    LOG.info("add transaction success: {}", transaction.getTransactionHash());
                } else if (indexResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                    LOG.info("update transaction success:{}", transaction.getTransactionHash());
                }
                ReplicationResponse.ShardInfo shardInfo = indexResponse.getShardInfo();
                if (shardInfo.getTotal() != shardInfo.getSuccessful()) {
                    LOG.info("sharding info is " + shardInfo);
                }
            }
        } catch (IOException e) {
            LOG.warn("save transaction error", e);
        }
    }
}
