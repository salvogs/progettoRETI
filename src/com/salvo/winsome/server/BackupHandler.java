package com.salvo.winsome.server;

import lombok.Builder;
import java.io.*;

/**
 * @author Salvatore Guastella
 */
public class BackupHandler implements Runnable{

    private final WSServer server;
    private final int backupPeriod;

    public BackupHandler(WSServer server, int backupPeriod) {
        this.server = server;
        this.backupPeriod = backupPeriod;
    }

    @Override
    public void run() {
        System.out.println("Thread backup avviato");
        try {
            // controllo se esistono i file dove fare il backup (altrimenti li creo)
            server.checkFiles();

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(this.backupPeriod);

                    server.performBackup();
                    System.out.println("BACKUP EFFETTUATO");


                } catch (InterruptedException e) {
                    break;
                }
            }

            // effettuo il backup per l'ultima volta
            server.performBackup();
            System.out.println("Ultimo backup effettuato");
            return;


        } catch (IOException e) {
    //            e.printStackTrace();
            System.err.println("Errore: impossibile effettuare il backup");
            System.exit(-1);
        }
    }

}
