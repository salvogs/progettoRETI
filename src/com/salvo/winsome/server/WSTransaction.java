package com.salvo.winsome.server;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Salvatore Guastella
 */

@Getter @Setter @NoArgsConstructor
public class WSTransaction {

    private int id;
    private String timestamp;
    private double value;

    public WSTransaction(int id, String timestamp, double value) {
        this.id = id;
        this.timestamp = timestamp;
        this.value = value;
    }

}
