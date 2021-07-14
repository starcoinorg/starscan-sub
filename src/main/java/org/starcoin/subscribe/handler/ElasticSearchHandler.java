package org.starcoin.subscribe.handler;

import com.alibaba.fastjson.JSON;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.starcoin.bean.PendingTransaction;

import java.io.IOException;
import java.util.List;

@Service
public class ElasticSearchHandler {

    public static final String TRANSACTION_INDEX = "txn_infos";
    private static final String PENDING_TXN_INDEX = "pending_txns";
    private static final int ELASTICSEARCH_MAX_HITS = 10000;
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

    public Result<PendingTransaction> getPendingTransaction(String network, int count) {
        SearchRequest searchRequest = new SearchRequest(ServiceUtils.getIndex(network, PENDING_TXN_INDEX));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        //page size
        searchSourceBuilder.size(count);
        searchSourceBuilder.from(0);
        searchSourceBuilder.sort("timestamp", SortOrder.DESC);
        searchSourceBuilder.trackTotalHits(true);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse;
        try {
            searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            LOG.error("get pending transactions error:", e);
            return null;
        }
        return ServiceUtils.getSearchResult(searchResponse, PendingTransaction.class);
    }

    public void deletePendingTransaction(String network, List<PendingTransaction> pendingTxns) {
        if (pendingTxns.size() <= 0) {
            return;
        }
        BulkRequest bulkRequest = new BulkRequest();
        String index = ServiceUtils.getIndex(network, PENDING_TXN_INDEX);
        for (PendingTransaction pending : pendingTxns) {
            DeleteRequest delete = new DeleteRequest(index);
            delete.id(pending.getTransactionHash());
            bulkRequest.add(delete);
        }
        try {
            client.bulk(bulkRequest, RequestOptions.DEFAULT);
            LOG.info("delete pending transaction ok");
        } catch (IOException e) {
            LOG.error("delete pending transaction error:", e);
        }
    }

    private boolean checkExists(String network, PendingTransaction transaction) {
        try {
            GetRequest getRequest = new GetRequest(ServiceUtils.getIndex(network, TRANSACTION_INDEX), transaction.getTransactionHash());
            GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);
            return getResponse.isExists();
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
