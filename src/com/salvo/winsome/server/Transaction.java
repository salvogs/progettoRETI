package com.salvo.winsome.server;

import lombok.Getter;

/**
 * @author Salvatore Guastella
 */

@Getter
public class Transaction {

    private int id;
    private String timestamp;
    private double value;

    public Transaction(int id, String timestamp, double value) {
        this.id = id;
        this.timestamp = timestamp;
        this.value = value;
    }

}
