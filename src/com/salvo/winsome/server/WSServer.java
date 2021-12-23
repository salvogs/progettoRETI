package com.salvo.winsome.server;

import com.salvo.winsome.RMIServerInterface;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
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

    private HashMap<String, ArrayList<WSUser>> allTags;


    private RMIServer remoteServer;

    int N_THREAD = 10;

    private ThreadPoolExecutor pool;

    private static final int MSG_BUFFER_SIZE = 1024;

    Object lock = new Object();

    public WSServer() {
        this.registeredUser = new HashMap<>();
        this.allTags = new HashMap<>();
        this.remoteServer = new RMIServer(registeredUser,allTags);
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

                int n = selector.select();

                synchronized (lock) {

                    if(n == 0)
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



                                System.out.println("Accettata nuova connessione dal client: " + client.getRemoteAddress());

                                // registro il canale per operazioni di lettura

                                registerRead(selector, client);


                            } else if (key.isReadable()) {
                                readMessage(selector, key);
                            }
//                       } else if (key.isWritable()) {
//                            sendResponse(selector,key);
//                        }

                        } catch (IOException e) { // terminazione improvvisa del client
                            System.err.println("Terminazione improvvisa client");
                            key.channel().close(); // chiudo il channel associato alla chiave
                            key.cancel(); // tolgo la chiave dal selector
                        }

                    }
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }

    }



    public void registerRead(Selector selector, SocketChannel clientChannel) throws IOException {

        // rendo il canale non bloccante
        clientChannel.configureBlocking(false);

        // creo il buffer che conterra' la lunghezza del messaggio
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        // creo il buffer che conterra' il messaggio
        ByteBuffer message = ByteBuffer.allocate(MSG_BUFFER_SIZE);

        ByteBuffer[] bba = {length, message};
        // aggiungo il channel del client al selector con l'operazione OP_READ
        // e aggiungo l'array di bytebuffer (bba) come attachment



        synchronized (lock) {
            selector.wakeup();
            clientChannel.register(selector, SelectionKey.OP_READ, bba);
        }

    }

    private void readMessage(Selector selector, SelectionKey key) throws IOException {

        SocketChannel clientChannel = (SocketChannel) key.channel();

        // recupero l'array di ByteBuffer [length, message] in attachment

        ByteBuffer[] bba = (ByteBuffer[]) key.attachment();


        // leggo dal channel



        if (clientChannel.read(bba) == -1) {// controllo se il client ha chiuso la socket
            key.channel().close();
            key.cancel();
            return;
        }

        if(!bba[0].hasRemaining()) { // se con la read ho riempito il primo buffer
            bba[0].flip(); // buffer [length] in lettura

            int msg_l = bba[0].getInt(); // recupero l'intero memorizzato sul buffer

            // controllo di aver letto effettivamente la lunghezza specificata

            if(bba[1].position() == msg_l){
                bba[1].flip();

                byte[] request = new byte[msg_l];

                bba[1].get(request);

                /* cancello la registrazione del channel, ritorno in modalita' bloccante // todo no
                    e faccio elaborare da un thread del pool la richiesta
                 */

                key.cancel();
                key.channel().configureBlocking(true);
                pool.execute(new RequestHandler(this,new String(request).trim(),selector,key));




            }


        }


    }



    public int login(String username, String password) throws IllegalArgumentException {
        if(username == null || password == null)
            throw new IllegalArgumentException();

        WSUser user = registeredUser.get(username);

        if(user == null || user.checkPassword(password))
            return -1;

        if(user.alreadyLogged())
            return -2;

        // aggiungere agli utenti loggati per invio ricompense

        user.setLogged(true);

        return 0;


    }

    public int logout(String username) throws IllegalArgumentException {
        if(username == null)
            throw new IllegalArgumentException();

        WSUser user = registeredUser.get(username);

        if(user == null || !user.alreadyLogged())
           return -1;

        user.setLogged(false);

        return 0;

    }


    public String[] listUsers(String username) throws IllegalArgumentException {
        if(username == null)
            throw new IllegalArgumentException();

        WSUser user = registeredUser.get(username);

        if(user == null || !user.alreadyLogged())
            return null;

        String[] tags = user.getTags();

        // se l'utente si e' registrato senza specificare tag
        if(tags.length == 0) return null;

        HashSet<String> selectedUsers = new HashSet<>();

        for(String tag : tags) {

            // gli utenti registrati con lo stesso tag
            ArrayList<WSUser> users = allTags.get(tag);

            for(WSUser u : users){
                selectedUsers.add(u.getUsername());
            }

        }

        return selectedUsers.isEmpty() ? null : (String[]) selectedUsers.toArray();


    }


    /**
     * @return i follower di @username sotto forma di String[]
     */
    public String[] listFollowers(String username) throws IllegalArgumentException {
        if(username == null)
            throw new IllegalArgumentException();

        WSUser user = registeredUser.get(username);

        if(user == null || !user.alreadyLogged())
            return null;

        HashSet<String> followers = user.getFollowers();


        return followers.isEmpty() ? null : (String[]) followers.toArray();

    }

    public String[] listFollowing(String username) throws IllegalArgumentException {
        if(username == null)
            throw new IllegalArgumentException();

        WSUser user = registeredUser.get(username);

        if(user == null || !user.alreadyLogged())
            return null;

        HashSet<String> followed = user.getFollowed();

        if(followed.isEmpty())
            return null;

        return followed.isEmpty() ? null : (String[]) followed.toArray();

    }


    public int followUser(String username, String toFollow) throws IllegalArgumentException{
        if(username == null || toFollow == null)
            throw new IllegalArgumentException();

        if(username.equals(toFollow))
            return -1;

        WSUser user = registeredUser.get(username);
        WSUser userToFollow = registeredUser.get(toFollow);

        if(user == null || userToFollow == null)
            return -2;

        // segue gia' quell'utente
       if(user.getFollowed().contains(toFollow)) // todo potrei fragarmene
           return -3;


        user.addFollowed(toFollow);
        userToFollow.addFollower(username);

        try {
            userToFollow.notifyNewFollow(username);
        } catch (RemoteException e) {
            e.printStackTrace();  // todo client termination
        }

        return 0;

    }

    public int unfollowUser(String username, String toUnfollow) {
        if(username == null || toUnfollow == null)
            throw new IllegalArgumentException();

        if(username.equals(toUnfollow))
            return -1;

        WSUser user = registeredUser.get(username);
        WSUser userToUnfollow = registeredUser.get(toUnfollow);

        if(user == null || userToUnfollow == null)
            return -2;

        // NON segue quell'utente
        if(!user.getFollowed().contains(toUnfollow)) // todo potrei fragarmene
            return -3;


        user.removeFollowed(toUnfollow);
        userToUnfollow.removeFollower(username);

        try {
            userToUnfollow.notifyNewUnfollow(username);
        } catch (RemoteException e) {
            e.printStackTrace(); // todo client termination
        }

        return 0;
    }





}
