package com.salvo.winsome.server;

import com.salvo.winsome.RMIClientInterface;
import com.salvo.winsome.RMIServerInterface;
import com.salvo.winsome.WSUser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Salvatore Guastella
 */
public class WSServer {

    /**
     * hash table degli utenti registrati
     */
    private HashMap<String, WSUser> registeredUser;
    private RMIServer remoteServer;


    public WSServer() {
        this.registeredUser = new HashMap<>();
        this.remoteServer = new RMIServer(registeredUser);
    }

    public void start() {
        // esporto oggetto remoteServer

        //        int port = Integer.parseInt(args[0]);
        int port = 6789;
        String SERVICE_NAME = "REGISTRATION-SERVICE";
        try {
            RMIServerInterface stub = (RMIServerInterface) UnicastRemoteObject.exportObject(remoteServer, 0);

            // creazione di un registry sulla porta args[0]

            Registry r = LocateRegistry.createRegistry(port);

            // pubblicazione dello stub sul registry

            r.rebind(SERVICE_NAME, stub);


        } catch (RemoteException e) {
            e.printStackTrace();
        }

        int socketPort = 9000;

        // istanza di un ServerSocketChannel in ascolto di richieste di connessione

        ServerSocketChannel serverChannel;
        Selector selector = null; // per permettere il multiplexing dei canali
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress("localhost", 9000));

            //imposto il channel come non bloccante
            serverChannel.configureBlocking(false);

            selector = Selector.open();


            // registro il canale relativo alle connessioni sul selector (per ACCEPT)

            serverChannel.register(selector, SelectionKey.OP_ACCEPT);


        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("In attesa di connessioni sulla porta " + socketPort);

        while (true) {

            // operazione bloccante che aspetta che ci sia un channel ready
            try {
                selector.select();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }

            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove(); // rimuove la chiave dal Selected Set, ma non dal registered Set

                try {
                    if (key.isAcceptable()) { //nuova connessione

                        ServerSocketChannel listenSocket = (ServerSocketChannel) key.channel();
                        SocketChannel client = listenSocket.accept();
                        System.out.println(client.getRemoteAddress() + " connesso");

                        // rendo il canale non bloccante
                        client.configureBlocking(false);

                    } else if (key.isReadable()) {


                    } else if (key.isWritable()) {

                    }

                } catch (IOException e) {
                    key.cancel(); // tolgo la chiave dal selector

                    try {
                        key.channel().close(); // chiudo il channel associato alla chiave
                    } catch (IOException ex) {
                    }

                }
            }
        }

    }

}
