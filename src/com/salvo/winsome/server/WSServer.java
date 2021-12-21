package com.salvo.winsome.server;

import com.salvo.winsome.RMIServerInterface;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Salvatore Guastella
 */
public class WSServer {

    /**
     * hash table degli utenti registrati
     */
    private HashMap<String, WSUser> registeredUser;




    private RMIServer remoteServer;

    int N_THREAD = 10;

    private ThreadPoolExecutor pool;

    private static final int MSG_BUFFER_SIZE = 1024;

    public WSServer() {
        this.registeredUser = new HashMap<>();
        this.remoteServer = new RMIServer(registeredUser);
        this.pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(N_THREAD);
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


            System.out.println("In attesa di connessioni sulla porta " + socketPort);

            while (true) {

                // operazione bloccante che aspetta che ci sia un channel ready

                if (selector.select() == 0)
                    continue;


                Set<SelectionKey> readyKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = readyKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove(); // rimuove la chiave dal Selected Set, ma non dal registered Set

                    try {
                        if (key.isAcceptable()) { //nuova connessione di un client

                            ServerSocketChannel listenSocket = (ServerSocketChannel) key.channel();
                            SocketChannel client = listenSocket.accept();

                            // rendo il canale non bloccante
                            client.configureBlocking(false);

                            System.out.println("Accettata nuova connessione dal client: " + client.getRemoteAddress());

                            // registro il canale per operazioni di lettura

                            registerRead(selector, client);


                        } else if (key.isReadable()) {
                            readMessage(selector,key);

                        } else if (key.isWritable()) {

                        }

                    } catch (IOException e) { // terminazione improvvisa del client
                        System.err.println("Terminazione improvvisa client");
                        key.channel().close(); // chiudo il channel associato alla chiave
                        key.cancel(); // tolgo la chiave dal selector
                    }

                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }

    }

    private void registerRead(Selector selector, SocketChannel clientChannel) throws IOException {
        // creo il buffer che conterra' la lunghezza del messaggio
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        // creo il buffer che conterra' il messaggio
        ByteBuffer message = ByteBuffer.allocate(MSG_BUFFER_SIZE);

        ByteBuffer[] bba = {length, message};
        // aggiungo il channel del client al selector con l'operazione OP_READ
        // e aggiungo l'array di bytebuffer (bba) come attachment
        clientChannel.register(selector, SelectionKey.OP_READ, bba);
    }

    private void readMessage(Selector selector, SelectionKey key) throws IOException {

        SocketChannel clientChannel = (SocketChannel) key.channel();

        // recupero l'array di ByteBuffer [length, message] in attachment

        ByteBuffer[] bba = (ByteBuffer[]) key.attachment();


        // leggo dal channel

        clientChannel.read(bba);

        if(!bba[0].hasRemaining()) { // se con la read ho riempito il primo buffer
            bba[0].flip(); // buffer [length] in lettura

            int msg_l = bba[0].getInt(); // recupero l'intero memorizzato sul buffer

            // controllo di aver letto effettivamente la lunghezza specificata

            if(bba[1].position() == msg_l){
                bba[1].flip();


                pool.execute(new RequestHandler(new String(bba[1].array()).trim()));


                // TODO registrare per WRITE

            }


        }




    }





}
