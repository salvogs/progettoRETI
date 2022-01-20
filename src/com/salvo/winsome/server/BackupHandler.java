package com.salvo.winsome.server;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Getter;

import javax.swing.text.Position;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Salvatore Guastella
 */
@Builder(builderMethodName = "newBuilder")
public class BackupHandler implements Runnable{

    WSServer server;

    public BackupHandler(WSServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        try {

            server.checkFiles();


            while (true) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                server.performBackup();
                System.out.println("BACKUP EFFETTUATO");
            }
        } catch (IOException e) {
    //            e.printStackTrace();
            System.err.println("Impossibile effettuare il backup");
            System.exit(-1);
        }
    }

}
