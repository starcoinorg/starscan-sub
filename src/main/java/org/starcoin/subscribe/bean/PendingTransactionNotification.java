package org.starcoin.subscribe.bean;

import org.web3j.protocol.websocket.events.Notification;

import java.util.List;

public class PendingTransactionNotification extends Notification<List<String>> {}
