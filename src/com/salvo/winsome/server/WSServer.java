package com.salvo.winsome.server;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salvo.winsome.RMIServerInterface;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.PackagePrivate;

import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Salvatore Guastella
 */
public class WSServer implements Runnable{


    private final int tcpPort;
//    private final int udpPort;
    @Getter private final InetAddress multicastAddress;
    @Getter private final int multicastPort;
    private final int regPort;
    private final String regServiceName;




    /**
     * hash table degli utenti registrati
     */

    private ConcurrentHashMap<String, WSUser> registeredUser;

    private ConcurrentHashMap<String, ArrayList<String>> allTags;

    ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    Lock allTagsReadLock = readWriteLock.readLock();
    Lock allTagsWriteLock = readWriteLock.writeLock();

    private RMIServer remoteServer;

    private ConcurrentHashMap<Integer,String> hashUser; // corrispondenza hash ip

    int N_THREAD = 10; // todo config

    private ThreadPoolExecutor pool;

    private static final int MSG_BUFFER_SIZE = 1024;

    Object lock = new Object();

    @Getter private ConcurrentHashMap<Integer, Post> posts;


    ObjectMapper mapper;


    private HashMap<Integer, HashSet<String>> newUpvotes;
    private HashMap<Integer, HashSet<String>> newDownvotes;
    private HashMap<Integer, ArrayList<String>> newComments;

    private int idPostCounter;
    private int rewardsIteration;
    private int idTransactionsCounter;
    private double lastExchangeRate;

    private File backupDir;
    private File usersBackup;
    private File postsBackup;
    private File tagsBackup;
    private File variablesBackup;


    Selector selector = null; // per permettere il multiplexing dei channel
    boolean exit = false;


    public WSServer(int tcpPort, int udpPort, int multicastPort, InetAddress multicastAddress, int regPort, String regServiceName) {

        this.tcpPort = tcpPort;


        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;

        this.regPort = regPort;
        this.regServiceName = regServiceName;


        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);


        this.backupDir = new File("./backup");
        this.usersBackup = new File(backupDir + "/users.json");
        this.postsBackup = new File(backupDir + "/posts.json");
        this.tagsBackup = new File(backupDir + "./tags.json");
        this.variablesBackup = new File(backupDir + "./variables.json");


        if(restoreBackup() == -1) {
            System.err.println("Impossibile ripristinare backup");
            System.out.println("Inizializzazione server...");
            this.registeredUser = new ConcurrentHashMap<>();
            this.posts = new ConcurrentHashMap<>();
            this.allTags = new ConcurrentHashMap<>();
            this.idPostCounter = 0;
            this.rewardsIteration = 0;
            this.idTransactionsCounter = 0;
            this.lastExchangeRate = 0;
        } else System.out.println("Backup ripristinato");

        this.remoteServer = new RMIServer(registeredUser,allTags,allTagsWriteLock);
        this.hashUser = new ConcurrentHashMap<>();

        this.pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(N_THREAD);



        this.newUpvotes = new HashMap<>();
        this.newDownvotes = new HashMap<>();
        this.newComments = new HashMap<>();


    }


    private int restoreBackup() {

        if(backupDir.isDirectory() && usersBackup.isFile() && postsBackup.isFile()
                && tagsBackup.isFile() && variablesBackup.isFile()) {



            try(BufferedReader usersReader = new BufferedReader(new FileReader(usersBackup));
                BufferedReader postsReader = new BufferedReader(new FileReader(postsBackup));
                BufferedReader tagsReader = new BufferedReader(new FileReader(tagsBackup));
                BufferedReader variableReader = new BufferedReader(new FileReader(variablesBackup))
            ) {


                registeredUser = mapper.readValue(usersReader, new TypeReference<ConcurrentHashMap<String, WSUser>>() {});
                posts = mapper.readValue(postsReader, new TypeReference<ConcurrentHashMap<Integer, Post>>() {});
                allTags = mapper.readValue(tagsReader, new TypeReference<ConcurrentHashMap<String, ArrayList<String>>>() {});

                JsonNode variables = mapper.readTree(variableReader);

                idPostCounter = variables.get("idpostcounter").asInt();
                rewardsIteration = variables.get("rewardsiteration").asInt();
                idTransactionsCounter = variables.get("idtransactionscounter").asInt();
                lastExchangeRate = variables.get("lastexchangerate").asDouble();

                return 0;

            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }

        }

        return -1;

    }


    public void incrementWallet(String username, String timestamp, double value) {
        WSUser user = registeredUser.get(username);
        if(user != null) {
            user.lockWrite();
            user.incrementWallet(value);
            user.addTranstaction(new Transaction(idTransactionsCounter++,timestamp,value));
            user.unlockWrite();
        }
    }

    public void setRewardsIteration(int iteration) {
        rewardsIteration = iteration;
    }


    public void run() {


        // creo e avvio thread per la gestione del backup
        Thread backupThread = new Thread(new BackupHandler(this));
        backupThread.start();


        // creo e avvio il thread per il calcolo delle ricompense

        Thread rewardsThread = new Thread(new RewardsHandler(this));
        rewardsThread.start();


        try {
            // esporto oggetto remoteServer
            RMIServerInterface stub = (RMIServerInterface) UnicastRemoteObject.exportObject(remoteServer, 0);

            // creazione di un registry sulla porta parsata dal file di config

            Registry r = LocateRegistry.createRegistry(regPort);

            // pubblicazione dello stub sul registry
//            LocateRegistry.getRegistry(regPort).rebind(regServiceName, stub);
            r.rebind(regServiceName, stub);


        } catch (RemoteException e) {
            e.printStackTrace();
        }


        // istanza di un ServerSocketChannel in ascolto di richieste di connessione

        ServerSocketChannel serverChannel;

        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress("localhost", tcpPort)); //todo togliere commento
//            serverChannel.bind(new InetSocketAddress("192.168.8.127", tcpPort));
            //imposto il channel come non bloccante
            serverChannel.configureBlocking(false);

            selector = Selector.open();


            // registro il canale relativo alle connessioni sul selector (per ACCEPT)

            serverChannel.register(selector, SelectionKey.OP_ACCEPT);


            System.out.println("In attesa di connessioni sulla porta " + tcpPort);

            while (!exit) {
                // operazione bloccante che aspetta che ci sia un channel ready
                int n = selector.select();

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
                        } else if(key.isWritable()) {
                            sendResponse(selector,key);
                        }

                    } catch (IOException e) { // terminazione improvvisa del client
                        key.cancel(); // tolgo la chiave dal selector
//                        e.printStackTrace();
                        disconnetionHandler((SocketChannel) key.channel());
                    }

                }
            }


            // termino pool
            selector.close();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            // interrompo thread delegato al calcolo delle ricompense
            rewardsThread.interrupt();
            rewardsThread.join();

            // interrompo thread delegato al backup
            backupThread.interrupt();
            backupThread.join();


            return;

        }catch (Exception e){
            e.printStackTrace();
            System.exit(-1);
        }

    }


    public void stop() {
        this.exit = true;
        selector.wakeup();
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


        // se è già registrato in scrittura viene sostituito
        clientChannel.register(selector, SelectionKey.OP_READ, bba);

    }



    private void readMessage(Selector selector, SelectionKey key) throws IOException {

        SocketChannel clientChannel = (SocketChannel) key.channel();

        // recupero l'array di ByteBuffer [length, message] in attachment

        ByteBuffer[] bba = (ByteBuffer[]) key.attachment();


        // leggo dal channel


        if (clientChannel.read(bba) == -1) {// controllo se il client ha chiuso la socket
            key.cancel();
            disconnetionHandler((SocketChannel) key.channel());
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

                /* non cancello la registrazione del channel ma non imposta alcuna
                    operazione di interesse

                    sarà il thread worker a impostare come interestOps la READ

                 */
                key.interestOps(0);

                pool.execute(new RequestHandler(this,new String(request).trim(),selector,key));

            }


        }


    }

    private void sendResponse(Selector selector, SelectionKey key) throws IOException {

        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        clientChannel.write(buffer);

        if (!buffer.hasRemaining()) {
            buffer.clear();
            this.registerRead(selector, clientChannel);
        }



//        // creo il buffer che conterra' la lunghezza del messaggio
//        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
//        // creo il buffer che conterra' il messaggio
//        ByteBuffer message = ByteBuffer.allocate(1024);
//
//        ByteBuffer[] bba = {length, message};
//        // imposto l'interestOps della key OP_READ
//        // e aggiungo l'array di bytebuffer (bba) come attachment
//        key.attach(bba);
//        key.interestOps(SelectionKey.OP_READ);
//        selector.wakeup();


    }


    public ObjectNode login(String username, String password, int sessionId) throws IllegalArgumentException {

        if (password == null || sessionId < 0)
            throw new IllegalArgumentException();

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);

        if (user != null) {
            user.lockWrite();
            if (!user.alreadyLogged()) {
                /* non faccio la lock qui ma prima per evitare che un altro client faccia l'accesso
                    vedendo che nessuno è ancora loggato
                 */

                if (user.checkPassword(password)) {

                    user.setLogged(true);
                    user.setSessionId(sessionId);

                    HashSet<String> followers = new HashSet<>(user.getFollowers());
                    user.unlockWrite();

                    hashUser.put(sessionId, username);

                    response.put("status-code", HttpURLConnection.HTTP_OK);


                    // invio i riferimenti per mettersi in ascolto sulla multicast socket

                    response.put("multicast-address",multicastAddress.getHostAddress());
                    response.put("multicast-port",multicastPort);

                    // invio la lista dei follower

                    if (!followers.isEmpty()) {

                        ArrayNode fArray = mapper.createArrayNode();

                       for(String f : followers) {

                           ObjectNode fObject = mapper.createObjectNode();

                            WSUser follower = registeredUser.get(f);

                            fObject.put("username", f);
                            ArrayNode tArray = mapper.createArrayNode();

                            for (String t : follower.getTags()) {
                                tArray.add(t);
                            }
                            fObject.set("tags",tArray);

                            fArray.add(fObject);

                       }

                       response.set("followers",fArray);
                    }

                } else {
                    user.unlockWrite();
                    response.put("status-code", HttpURLConnection.HTTP_UNAUTHORIZED);
                    response.put("message", "credenziali errate");
                }
            } else {
                user.unlockWrite();

                response.put("status-code", HttpURLConnection.HTTP_UNAUTHORIZED);
                response.put("message",
                        "c'e' un utente gia' collegato, deve essere prima scollegato");
            }
    }


        return response;

    }

    /**
     *
     * @param username
     * @param sessionId per evitare che qualcuno tramite una
     *                 connessione diversa possa disconnettere l'utente
     * @return
     * @throws IllegalArgumentException
     */
    public ObjectNode logout(String username, int sessionId) throws IllegalArgumentException {

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);

        if(user != null) {
            user.lockWrite();
            if(checkStatus(user,response) == 0) {
                if(user.getSessionId() == sessionId) {

                    hashUser.remove(sessionId);
                    user.setLogged(false);
                    user.setSessionId(-1);
                    user.setRemoteClient(null);

                    user.unlockWrite();

                    response.put("status-code",HttpURLConnection.HTTP_OK);

                } else {
                    user.unlockWrite();
                    response.put("status-code", HttpURLConnection.HTTP_FORBIDDEN);
                    response.put("message",
                            "azione non permessa, non e' possibile disconnettere un altro utente");
                }
            }else {
                user.unlockWrite();
            }

        }

        return response;

    }


    public ObjectNode listUsers(String username) throws IllegalArgumentException {

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);


        if(user != null) {
            if(checkStatus(user,response) == 0) {

                String[] tags = user.getTags();  // tag per cui si e' registrato l'utente
                HashSet<String> toWrite = new HashSet<>();
                // i tag non possono cambiare -> non necessarie lock per scorrerli
                for (String tag : tags) {
                    // copia degli utenti registrati con lo stesso tag

                    allTagsReadLock.lock(); // potrebbe registrarsi un nuovo utente con gli stessi tag in comune

                    ArrayList<String> users = new ArrayList<>(allTags.get(tag));

                    allTagsReadLock.unlock();

                    for (String u : users) {
                        if (!u.equals(username)) {
                            toWrite.add(u);
                        }
                    }
                }

                if(toWrite.size() > 0) {
                    response.put("status-code", HttpURLConnection.HTTP_OK);
                    // array degli utenti
                    ArrayNode uArray = mapper.createArrayNode();
                    for(String u : toWrite) {
                        WSUser uToWrite = registeredUser.get(u);

                        ObjectNode uObject = mapper.createObjectNode();
                        uObject.put("username", u);

                        ArrayNode tArray = mapper.createArrayNode();
                        for (String t : uToWrite.getTags()) {
                            tArray.add(t);
                        }
                        uObject.set("tags",tArray);
                        uArray.add(uObject);
                    }
                    response.set("users",uArray);

                } else {
                    response.put("status-code", HttpURLConnection.HTTP_NO_CONTENT);
                    response.put("message","nessun utente con tag in comune");
                }
            } else {
                user.unlockRead();
            }
        }

        return response;

    }

    public ObjectNode listFollowing(String username) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);

        if(user != null) {
            if(checkStatus(user,response) == 0) {

                HashSet<String> followed = user.getFollowed(); // non mi serve alcuna lock

                if(followed.isEmpty()) {
                    response.put("status-code",HttpURLConnection.HTTP_NO_CONTENT);
                    response.put("message", "Non segui ancora nessuno");
                } else {

                    response.put("status-code",HttpURLConnection.HTTP_OK);

                    // array degli utenti
                    ArrayNode uArray = mapper.createArrayNode();

                    for(String u : followed) {

                        WSUser f_user = registeredUser.get(u);
                        ObjectNode uObject = mapper.createObjectNode();
                        /* non faccio alcuna lock perche' gli elementi che leggo (username e tags)
                            non possono essere modificati ( da altri thread )
                        * */
                        uObject.put("username",f_user.getUsername());

                        ArrayNode tArray = mapper.createArrayNode();
                        for (String tag : f_user.getTags()) {
                            tArray.add(tag);
                        }
                        uObject.set("tags",tArray);
                        uArray.add(uObject);

                    }
                    response.set("users",uArray);

                }

            }

        }


        return response;

    }


    public ObjectNode followUser(String username, String toFollow) throws IllegalArgumentException {

        ObjectNode response = mapper.createObjectNode();

        if(!username.equals(toFollow)) {
            WSUser user = checkUser(username,response);
            WSUser userToFollow = checkUser(toFollow,response);

            if (user != null && userToFollow != null) {

                if (checkStatus(user,response) == 0) {
                    user.lockWrite();
                    boolean newFollowed = user.addFollowed(toFollow);
                    user.unlockWrite();
                    if (newFollowed == true) {
                        userToFollow.lockWrite();
                        userToFollow.addFollower(username);
                        userToFollow.unlockWrite();

                        response.put("status-code",HttpURLConnection.HTTP_CREATED);

                        try {
                            userToFollow.notifyNewFollow(username, user.getTags());
                        } catch (RemoteException e) {
                            System.err.println("Impossibile notificare il client");
                            e.printStackTrace();
                        }

                    } else {
                        // se user segue gia' quell'utente non ritorno errori
                        response.put("status-code",HttpURLConnection.HTTP_OK);
                        response.put("message", "segui gia' "+toFollow);
                    }

                }
            }
        } else {
            response.put("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            response.put("message", "non puoi seguire te stesso");
        }


        return response;



    }

    public ObjectNode unfollowUser(String username, String toUnfollow) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        if(!username.equals(toUnfollow)) {
            WSUser user = checkUser(username,response);
            WSUser userToUnfollow = checkUser(toUnfollow,response);

            if (user != null && userToUnfollow != null) {
                if (checkStatus(user,response) == 0) {

                    user.lockWrite();
                    boolean wasFollowed = user.removeFollowed(toUnfollow);
                    user.unlockWrite();
                    if (wasFollowed == true) {
                        userToUnfollow.lockWrite();
                        userToUnfollow.removeFollower(username);
                        userToUnfollow.unlockWrite();

                        response.put("status-code", HttpURLConnection.HTTP_OK);

                        try {
                            userToUnfollow.notifyNewUnfollow(username);
                        } catch (RemoteException e) {
                            System.err.println("Impossibile notificare il client");
                            e.printStackTrace();
                        }
                    } else {
                        // se user non segue quell'utente non ritorno errori
                        response.put("status-code", HttpURLConnection.HTTP_OK);
                        response.put("message", "non segui" + toUnfollow);
                    }

                }

            }
        } else {
            response.put("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            response.put("message", "non puoi smettere di seguire te stesso");
        }

        return response;


    }

    public ObjectNode createPost(String username, String title, String content) throws IllegalArgumentException {

        if(title == null || content == null)
            throw new IllegalArgumentException();


        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);

        if(user != null && checkStatus(user,response) == 0) {

            int id = idPostCounter++;
            Post p = new Post(id,username,title,content,rewardsIteration);
            // aggiungo il post alla Map di tutti i post
            posts.put(id,p);

            user.lockWrite();
            user.newPost(id);
            user.unlockWrite();

            response.put("status-code",HttpURLConnection.HTTP_CREATED);
            response.put("id-post",id);

        }


        return response;

    }

    public ObjectNode deletePost(String username, int idPost) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);


        if(user != null && checkStatus(user,response) == 0) {

            Post post = checkPost(idPost,response);
            if(post != null) {
                post.lockWrite(); // aspetto se un thread sta leggendo/scrivendo il post
                if(post.getAuthor().equals(username)) {

                    post.setDeleted(); // eventuali thread che sono in attesa di fare la rewin si accorgono che il post è già stato cancellato
                    post.unlockWrite();


                    user.lockWrite();
                    // cancello il post dal blog dell'utente
                    user.deletePost(idPost);
                    user.unlockWrite();

                    HashSet<String> rewiners = post.getRewiners();

                    // cancello il post da tutti i blog degli utenti che lo hanno rewinnato
                    for(String r : rewiners) {
                        WSUser rewiner = registeredUser.get(r);
                        rewiner.lockWrite();
                        rewiner.deletePost(idPost);
                        rewiner.unlockWrite();
                    }

                    posts.remove(idPost);

                    response.put("status-code", HttpURLConnection.HTTP_OK);

                } else {
                    post.unlockWrite();
                    response.put("status-code", HttpURLConnection.HTTP_FORBIDDEN);
                    response.put("message",
                            "un post puo' essere cancellato solo dal suo autore");
                }

            }

        }

        return response;
    }


    public ObjectNode showFeed(String username) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);

        if(user != null && checkStatus(user,response) == 0) {

            ArrayNode pArray = mapper.createArrayNode();

            int written = 0;

            /* non mi serve acquisire la lock perchè l'utente non può
               mandare altre richieste di following prima di ricevere la risposta
               */
            for(String u : user.getFollowed()) {

                WSUser fUser = registeredUser.get(u);

                // lock dell'utente per evitare che aggiunga/rimuova posts
                fUser.lockRead();

                HashSet<Integer> blog = fUser.getBlog();

                for(Integer id : blog) {

                    Post p = posts.get(id);
                    ObjectNode pObject = mapper.createObjectNode();

                    pObject.put("id-post",p.getId());
                    pObject.put("author",p.getAuthor());
                    pObject.put("title",p.getTitle());

                    pArray.add(pObject);

                    written++;
                }

                fUser.unlockRead();

            }

            if(pArray.size() == 0) {
                response.put("status-code", HttpURLConnection.HTTP_NO_CONTENT);
                response.put("message","non sono presenti post da visualizzare");
            }else {
                response.put("status-code", HttpURLConnection.HTTP_OK);
                response.set("feed",pArray);
            }


        }

       return response;

    }

    public ObjectNode viewBlog(String username) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);

        if(user != null && checkStatus(user,response) == 0) {

            user.lockRead();

            HashSet<Integer> blog = user.getBlog();

            if(!blog.isEmpty()) {
                response.put("status-code", HttpURLConnection.HTTP_OK);

                ArrayNode pArray = mapper.createArrayNode();

                for(Integer id : blog) {
                    /* non mi serve fare la lock sul post perchè id,autore e titolo non possono cambiare
                    * e per cancellare/aggiungere post sul blog è necessaria la lockWrite sull'utente
                    * N.B. la lockWrite su user può essere acquisita se user ha fatto il rewin del post
                     */
                    Post p = posts.get(id);
                    ObjectNode pObject = mapper.createObjectNode();
                    pObject.put("id-post",p.getId());
                    pObject.put("author",p.getAuthor());
                    pObject.put("title",p.getTitle());

                    pArray.add(pObject);
                }
                response.set("blog",pArray);

            } else {
                response.put("status-code", HttpURLConnection.HTTP_NO_CONTENT);
                response.put("message","non sono presenti post da visualizzare");
            }

            user.unlockRead();

        }


        return response;
    }

    public ObjectNode rewinPost(String username, int idPost) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);

        if(user != null && checkStatus(user,response) == 0) {

            Post post = checkPost(idPost,response);
            if(post != null) {
                post.lockWrite();
                // controllo se tra le due istruzioni precedenti un altro thread ha cancellato il post
                if(!checkDeleted(post,response) && !checkAuthor(username,post,response) && checkFeed(user,post,response)) {
                    user.lockWrite(); // per evitare letture inconsistenti alla showFeed e viewBlog
                    if (user.getBlog().add(idPost) == true) {
                        post.addRewiner(username);
                        response.put("status-code", HttpURLConnection.HTTP_CREATED);
                    } else {
                        response.put("status-code", HttpURLConnection.HTTP_OK);
                        response.put("message","post gia' presente nel blog");
                    }
                    user.unlockWrite();

                }
                post.unlockWrite();
            }


        }


        return response;
    }

    public ObjectNode ratePost(String username, int idPost, int vote) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);

        if(user != null && checkStatus(user,response) == 0) {

            Post post = checkPost(idPost,response);
            if(post != null) {
                post.lockWrite();

                if(!checkDeleted(post,response) && !checkAuthor(username,post,response) && checkFeed(user,post,response) && !alreadyVoted(username,post,response)) {

                    if (vote == 1) {
                        post.newUpvote(username);

                        // memorizzo nuovo voto +
                        synchronized (newUpvotes) {
                            newUpvotes.putIfAbsent(idPost, new HashSet<>());
                            newUpvotes.get(idPost).add(username); // aggiungo l'username al set dei votanti
                        }

                    }
                    else {
                        post.newDownvote(username);

                        // memorizzo nuovo voto -
                        synchronized (newDownvotes) {
                            newDownvotes.putIfAbsent(idPost,new HashSet<>());
                            newDownvotes.get(idPost).add(username); // aggiungo l'username al set dei votanti
                        }
                    }

                    response.put("status-code", HttpURLConnection.HTTP_CREATED);

                }

                post.unlockWrite();
            }

        }


        return response;


    }

    public ObjectNode commentPost(String username, int idPost, String comment) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);


        if(user != null && checkStatus(user,response) == 0) {

            Post post = checkPost(idPost,response);

            if(post != null) {
                post.lockWrite();
                if(!checkDeleted(post,response) && !checkAuthor(username,post,response) && checkFeed(user,post,response)) {
                    post.newComment(username, comment);

                    // memorizzo nuvo commento

                    synchronized (newComments) {
                        newComments.putIfAbsent(idPost,new ArrayList<>());
                        newComments.get(idPost).add(username); // a differenza dei voti possono esserci duplicati
                    }


                    response.put("status-code", HttpURLConnection.HTTP_CREATED);
                }
                post.unlockWrite();
            }

        }

        return response;


    }


    public ObjectNode showPost(String username, int idPost) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);


        if(user != null && checkStatus(user,response) == 0) {

            Post post = checkPost(idPost,response);

            if(post != null) {

                post.lockRead();

                /* va a buon fine se :
                    il post e' di user
                    or il post e' nel blog di user (quindi anche rewinned)
                    or il post e' nel feed di user
                 */
                if(!checkDeleted(post,response) && post.getAuthor().equals(username) || user.getBlog().contains(idPost) || checkFeed(user,post,response)) {
                    response.put("status-code", HttpURLConnection.HTTP_OK);
                    response.put("title",post.getTitle());
                    response.put("content",post.getContent());
                    response.put("upvote",post.getUpvote().size());
                    response.put("downvote",post.getDownvote().size());

                    ArrayNode cArray = mapper.createArrayNode();
                    for(Map.Entry<String,ArrayList<String>> c : post.getComments().entrySet()) {
                        for(String cont : c.getValue()) {
                            ObjectNode cObject = mapper.createObjectNode();
                            cObject.put("comment-author",c.getKey());
                            cObject.put("comment-content",cont);
                            cArray.add(cObject);
                        }
                    }
                    response.set("comments",cArray);
                }
                post.unlockRead();
            }
        }


        return response;

    }


    public ObjectNode getWallet(String username) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();
        WSUser user = checkUser(username,response);

        if(user != null && checkStatus(user,response) == 0) {

            user.lockRead();
            ArrayList<Transaction> transactions = new ArrayList<>(user.getTransactions());
            double wallet = user.getWallet();
            user.unlockRead();

            response.put("status-code", HttpURLConnection.HTTP_OK);
            response.put("wallet",user.getWallet());

            if(!transactions.isEmpty()) {
                ArrayNode tArray = mapper.createArrayNode();

                for (Transaction t : transactions) {
                    ObjectNode tObject = mapper.createObjectNode();
                    tObject.put("id", t.getId());
                    tObject.put("timestamp", t.getTimestamp());
                    tObject.put("value", t.getValue());
                    tArray.add(tObject);
                }

                response.set("transactions",tArray);

            }

        }


        return response;
    }

    public ObjectNode getWalletInBitcoin(String username) throws IllegalArgumentException{

        ObjectNode response = mapper.createObjectNode();

        WSUser user = checkUser(username,response);

        if(user != null && checkStatus(user,response) == 0) {
            user.lockRead();
            double wincoinWallet =  user.getWallet();
            user.unlockRead();

            double walletBtc =  1.638344810037658 * getExchangeRate();
//            double walletBtc =  wincoinWallet * getExchangeRate(); todo remove comment

            response.put("status-code", HttpURLConnection.HTTP_OK);

            response.put("wallet-btc",walletBtc);

        }


        return response;
    }


    private double getExchangeRate() {

        double rate;

        try {
            URL url = new URL("https://www.random.org/decimal-fractions/?num=1&dec=6&col=1&format=plain&rnd=new");
            HttpURLConnection urlCon = (HttpURLConnection) url.openConnection();
            urlCon.setRequestMethod("GET");

            if(urlCon.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(urlCon.getInputStream()));

//                StringBuilder s = new StringBuilder();

                rate = Double.parseDouble(in.readLine());
                in.close();

                lastExchangeRate = rate;
                return rate;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.err.println("errore richiesta http a RANDOM.ORG");
        System.out.println("exchange rate = "+lastExchangeRate);
        return lastExchangeRate;
    }

    /**
     * controlla se un post appartiene al feed di un utente
     * @param user
     * @param post
     * @return true se il post appartiene al feed dell'utente,
     *          false altrimenti
     */
    private boolean checkFeed(WSUser user, Post post,ObjectNode response) {

        /**
         * verifico se l'utente segue l'autore del post
         * oppure se segue almeno uno degli utenti che lo
         * hanno rewinnato
         */

        if((user.getFollowed().contains(post.getAuthor()) ||
        !Collections.disjoint(post.getRewiners(),user.getFollowed())) == false) {
            response.put("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            response.put("message", "post non presente nel tuo feed");
            return false;
        }

        return true;
    }

    /**
     *
     * @param username
     * @param post
     * @return true se il post e' di @username, false altrimenti
     * @throws IOException
     */
    private boolean checkAuthor(String username,Post post,ObjectNode response) {
        if(post.getAuthor().equals(username)){
            response.put("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            response.put("message", "non puoi commentare/votare/rewinnare i tuoi post");
            return true;
        }

        return false;

    }

    private boolean alreadyVoted(String username, Post post,ObjectNode response) {
        if(post.voted(username) == true) {
            response.put("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            response.put("message", "hai gia' votato il post "+post.getId());
            return true;
        }

        return false;
    }



    public void disconnetionHandler(SocketChannel channel) {
        try {
            System.err.println("Disconnesso client :"+channel.getRemoteAddress());

            int sessionId = channel.getRemoteAddress().hashCode();

            // ricavo l'username (se era loggato) dell'utente disconnesso
            String username = hashUser.get(sessionId);

            if(username != null) {
                logout(username,sessionId);
            }
            channel.close(); // chiudo il channel

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private WSUser checkUser(String username,ObjectNode response) throws IllegalArgumentException{

        if(username == null)
            throw new IllegalArgumentException();

        WSUser user = registeredUser.get(username);

        if(user == null) {
            response.put("status-code",HttpURLConnection.HTTP_UNAUTHORIZED);
            response.put("message", "nessun utente registrato come "+username);
            return null;
        }

        return user;

    }


    private int checkStatus(WSUser user,ObjectNode response) {
        if(user.alreadyLogged())
            return 0;

        response.put("status-code",HttpURLConnection.HTTP_UNAUTHORIZED);
        response.put("message", "azione non permessa, login non effettuato");
        return -1;
    }


    private Post checkPost(int id,ObjectNode response) throws IllegalArgumentException{

        if(id < 0)
            throw new IllegalArgumentException();

        Post post = posts.get(id);

        if(post == null) {
            response.put("status-code",HttpURLConnection.HTTP_NOT_FOUND);
            response.put("message", "post "+id+" non trovato");
            return null;
        }

        return post;

    }


    private boolean checkDeleted(Post p,ObjectNode response) {
        if(p.isDeleted()){
            response.put("status-code",HttpURLConnection.HTTP_NOT_FOUND);
            response.put("message", "post "+p.getId()+" non trovato");
        }

        return false;
    }

//    private String jsonResponseToString() throws IOException {
//
//        generator.flush();
//        String response = responseStream.toString().trim();
//        responseStream.reset();
//        return response;
//    }

    public void checkFiles() throws IOException{

        if (!backupDir.exists())
            backupDir.mkdir();

        if (!usersBackup.exists())
            usersBackup.createNewFile();

        if (!postsBackup.exists())
            postsBackup.createNewFile();

        if (!tagsBackup.exists())
            tagsBackup.createNewFile();

        if (!variablesBackup.exists())
            variablesBackup.createNewFile();


    }

    public void performBackup() throws IOException {

        mapper.writeValue(usersBackup,registeredUser);
        mapper.writeValue(postsBackup,posts);
        mapper.writeValue(tagsBackup,allTags);

        ObjectNode variables = mapper.createObjectNode();

        variables.put("idpostcounter",idPostCounter);
        variables.put("rewardsiteration",rewardsIteration);
        variables.put("idtransactionscounter",idTransactionsCounter);
        variables.put("lastexchangerate",lastExchangeRate).asDouble();


        mapper.writeValue(variablesBackup,variables);


    }


    public void go() {

        registeredUser.put("u1", new WSUser("u1", "a", null));
        registeredUser.put("u2", new WSUser("u2", "", null));
        registeredUser.put("u3", new WSUser("u3", "", null));
        registeredUser.put("u4", new WSUser("u4", "", null));
        registeredUser.put("u5", new WSUser("u5", "", null));
        registeredUser.put("u6", new WSUser("u6", "", null));

        // Add some relationships between users

            login("u1", "", 1);
            login("u2", "", 2);
            login("u3", "", 3);
            login("u4", "", 4);
            login("u5", "", 5);
            login("u6", "", 6);


            followUser("u2", "u1");
            followUser("u3", "u1");
            followUser("u4", "u1");
            followUser("u5", "u1");
            followUser("u6", "u1");

            createPost("u1", "post bello", "sugoooo");



            ratePost("u2", 0, 1);
            ratePost("u3", 0, 1);
            ratePost("u4", 0, 1);
            ratePost("u5", 0, -1);
//            ratePost("u6", 0, -1);

            commentPost("u2", 0, "commento1");
            commentPost("u3", 0, "commento1");
            for(int i =0; i < 3; i++)
                commentPost("u4", 0, "commento1");

//            commentPost("u4", 0, "commento2");
//            commentPost("u4", 0, "commento3");
//            commentPost("u5", 0, "commento1");
//            commentPost("u5", 0, "commento2");
//            commentPost("u5", 0, "commento3");
//            commentPost("u5", 0, "commento4");
//            commentPost("u5", 0, "commento5");
//            commentPost("u6", 0, "commento1");
//            commentPost("u6", 0, "commento2");





    }

    public int getRewardsIteration() {
        return rewardsIteration;
    }

    public HashMap<Integer, HashSet<String>> replaceAndGetNewUpvotes() {
        synchronized (newUpvotes) {
            HashMap<Integer, HashSet<String>> tmpUpvotes = newUpvotes;
            newUpvotes = new HashMap<>();
            return tmpUpvotes;
        }

    }

    public HashMap<Integer, HashSet<String>> replaceAndGetNewDownvotes() {
        synchronized (newDownvotes) {
            HashMap<Integer, HashSet<String>> tmpDownvotes = newDownvotes;
            newDownvotes = new HashMap<>();
            return tmpDownvotes;
        }
    }

    public HashMap<Integer, ArrayList<String>> replaceAndGetNewComments() {
        synchronized (newComments) {
            HashMap<Integer, ArrayList<String>> tmpComments = newComments;
            newComments = new HashMap<>();
            return tmpComments;
        }
    }
}
