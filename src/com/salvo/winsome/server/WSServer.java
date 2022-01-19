package com.salvo.winsome.server;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salvo.winsome.RMIServerInterface;

import java.io.*;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Salvatore Guastella
 */
public class WSServer {


    private final int tcpPort;
    private final int udpPort;
    private final InetAddress multicastAddress;
    private final int multicastPort;
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

    int N_THREAD = 10;

    private ThreadPoolExecutor pool;

    private static final int MSG_BUFFER_SIZE = 1024;

    Object lock = new Object();

    private ConcurrentHashMap<Integer, Post> posts;
    private int idPostCounter = 0;


    ObjectMapper mapper = new ObjectMapper();
    JsonFactory jfactory = new JsonFactory();
    ByteArrayOutputStream responseStream;
    JsonGenerator generator;


    // todo concurrent

    private HashMap<Integer, HashSet<String>> newUpvotes;
    private HashMap<Integer, HashSet<String>> newDownvotes;
    private HashMap<Integer, ArrayList<String>> newComments;

    private int rewardsIteration;
    private int idTransactionsCounter;
    private double lastExchangeRate;

    private File backupDir;
    private File usersBackup;
    private File postsBackup;



    public WSServer(int tcpPort, int udpPort, int multicastPort, InetAddress multicastAddress, int regPort, String regServiceName) {

        this.tcpPort = tcpPort;
        this.udpPort = udpPort;


        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.regPort = regPort;
        this.regServiceName = regServiceName;

        this.registeredUser = new ConcurrentHashMap<>();
        this.posts = new ConcurrentHashMap<>();

        this.backupDir = new File("./backup"); // todo parse
        this.usersBackup = new File(backupDir + "/users.json");
        this.postsBackup = new File(backupDir + "/posts.json");

        restoreBackup(usersBackup,postsBackup);

        this.allTags = new ConcurrentHashMap<>();
        this.remoteServer = new RMIServer(registeredUser,allTags,allTagsWriteLock);
        this.hashUser = new ConcurrentHashMap<>();

        this.pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(N_THREAD);

        mapper = new ObjectMapper();
        responseStream = new ByteArrayOutputStream();
        try {
            this.generator = jfactory.createGenerator(responseStream, JsonEncoding.UTF8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.generator.useDefaultPrettyPrinter();


        this.newUpvotes = new HashMap<>();
        this.newDownvotes = new HashMap<>();
        this.newComments = new HashMap<>();


        this.rewardsIteration = 0;
        this.idTransactionsCounter = 0;

        this.lastExchangeRate = 0;
    }


    private void restoreBackup(File usersBackup,File postsBackup) {

        if(!backupDir.exists()) // se la directory non esiste la creo
            backupDir.mkdir();



        try {

            if(!usersBackup.exists()) {
                usersBackup.createNewFile();

            } else if(usersBackup.length() > 0){

                BufferedReader usersReader = new BufferedReader(new FileReader(usersBackup));

                registeredUser = mapper.readValue(usersReader,new TypeReference<HashMap<String,WSUser>>() {});

            }




            if(!postsBackup.exists()) {
                postsBackup.createNewFile();

            } else if(postsBackup.length() > 0){
                BufferedReader postsReader = new BufferedReader(new FileReader(postsBackup));
                posts = mapper.readValue(postsReader,new TypeReference<HashMap<Integer,Post>>() {});
//                postsReader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }






    public HashMap<Integer, HashSet<String>> getNewUpvotes() {
        return newUpvotes;
    }

    public HashMap<Integer, HashSet<String>> getNewDownvotes() {
        return newDownvotes;
    }

    public HashMap<Integer, ArrayList<String>> getNewComments() {
        return newComments;
    }

    public ConcurrentHashMap<Integer, Post> getPosts() {
        return posts;
    }

    public void incrementWallet(String username, String timestamp, double value) {
        WSUser user = registeredUser.get(username);
        if(user != null) {
            user.incrementWallet(value);
            user.addTranstaction(new Transaction(idTransactionsCounter++,timestamp,value));
        }
    }

    public void setRewardsIteration(int iteration) {
        rewardsIteration = iteration;
    }


    public void start() {


        // creo e avvio thread per la gestione del backup


        Thread backupThread = new Thread(new BackupHandler(registeredUser,usersBackup,posts,postsBackup));
        backupThread.start();


        try {
            // esporto oggetto remoteServer
            RMIServerInterface stub = (RMIServerInterface) UnicastRemoteObject.exportObject(remoteServer, 0);

            // creazione di un registry sulla porta parsata dal file di config

            Registry r = LocateRegistry.createRegistry(regPort);

            // pubblicazione dello stub sul registry

            r.rebind(regServiceName, stub);


        } catch (RemoteException e) {
            e.printStackTrace();
        }


        // istanza di un ServerSocketChannel in ascolto di richieste di connessione

        ServerSocketChannel serverChannel;
        Selector selector = null; // per permettere il multiplexing dei canali
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress("localhost", tcpPort));

            //imposto il channel come non bloccante
            serverChannel.configureBlocking(false);

            selector = Selector.open();


            // registro il canale relativo alle connessioni sul selector (per ACCEPT)

            serverChannel.register(selector, SelectionKey.OP_ACCEPT);


            System.out.println("In attesa di connessioni sulla porta " + tcpPort);

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

                        } catch (IOException e) { // terminazione improvvisa del client
                            key.cancel(); // tolgo la chiave dal selector
                            disconnetionHandler((SocketChannel) key.channel());
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

                /* cancello la registrazione del channel, ritorno in modalita' bloccante
                    e faccio elaborare da un thread del pool la richiesta
                 */

                key.cancel();
                key.channel().configureBlocking(true);
                pool.execute(new RequestHandler(this,new String(request).trim(),selector,key));




            }


        }


    }



    public String login(String username, String password, int sessionId) throws IllegalArgumentException, IOException {

        if (password == null || sessionId < 0)
            throw new IllegalArgumentException();

        generator.writeStartObject();

        WSUser user = checkUser(username);


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

                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

                    // invio la lista dei follower

                    if (!followers.isEmpty()) {

                        generator.writeArrayFieldStart("followers");

                       for(String f : followers) {
                            generator.writeStartObject();

                            WSUser follower = registeredUser.get(f);

                            generator.writeStringField("username", f);
                            generator.writeArrayFieldStart("tags");

                            for (String t : follower.getTags()) {
                                generator.writeString(t);
                            }

                            generator.writeEndArray();

                            generator.writeEndObject();
                        }

                        generator.writeEndArray();

                    }

                } else {
                    user.unlockWrite();
                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_UNAUTHORIZED);
                    generator.writeStringField("message", "credenziali errate");
                }
            } else {
                user.unlockWrite();

                generator.writeNumberField("status-code", HttpURLConnection.HTTP_UNAUTHORIZED);
                generator.writeStringField("message",
                        "c'e' un utente gia' collegato, deve essere prima scollegato");
            }
    }



        generator.writeEndObject();

        return jsonResponseToString();


    }

    /**
     *
     * @param username
     * @param sessionId per evitare che qualcuno tramite una
     *                 connessione diversa possa disconnettere l'utente
     * @return
     * @throws IllegalArgumentException
     */
    public String logout(String username, int sessionId) throws IllegalArgumentException, IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null) {
            user.lockWrite();
            if(checkStatus(user) == 0) {
                if(user.getSessionId() == sessionId) {

                    hashUser.remove(sessionId);
                    user.setLogged(false);
                    user.setSessionId(-1);
                    user.setRemoteClient(null);

                    user.unlockWrite();

                    generator.writeNumberField("status-code",HttpURLConnection.HTTP_OK);


                } else {
                    user.unlockWrite();
                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_FORBIDDEN);
                    generator.writeStringField("message",
                            "azione non permessa, non e' possibile disconnettere un altro utente");
                }
            }else {
                user.unlockWrite();
            }

        }
        generator.writeEndObject();
        return jsonResponseToString();

    }


    public String listUsers(String username) throws IllegalArgumentException, IOException {

        WSUser user = checkUser(username);
        generator.writeStartObject();

        if(user != null) {
            if(checkStatus(user) == 0) {

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
                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);
                    // array degli utenti
                    generator.writeArrayFieldStart("users");

                    for(String u : toWrite) {
                        WSUser uToWrite = registeredUser.get(u);
                        generator.writeStartObject();
                        generator.writeStringField("username", u);
                        generator.writeArrayFieldStart("tags");

                        for (String t : uToWrite.getTags()) {
                            generator.writeString(t);
                        }
                        generator.writeEndArray();
                        generator.writeEndObject();
                    }
                    generator.writeEndArray();
                } else {
                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_NO_CONTENT);
                    generator.writeStringField("message","nessun utente con tag in comune");
                }
            } else {
                user.unlockRead();
            }
        }

        generator.writeEndObject();
        return jsonResponseToString();

    }

    public String listFollowing(String username) throws IllegalArgumentException, IOException {

        WSUser user = checkUser(username);
        generator.writeStartObject();
        if(user != null) {
            if(checkStatus(user) == 0) {
                user.lockRead();
                HashSet<String> followed = new HashSet<>(user.getFollowed()); // copia degli utenti seguiti
                user.unlockRead();

                if(followed.isEmpty()) {
                    generator.writeNumberField("status-code",HttpURLConnection.HTTP_NO_CONTENT);
                    generator.writeStringField("message", "Non segui ancora nessuno");
                } else {

                    generator.writeNumberField("status-code",HttpURLConnection.HTTP_OK);

                    // array degli utenti
                    generator.writeArrayFieldStart("users");

                    for(String u : followed) {

                        WSUser f_user = registeredUser.get(u);

                        /* non faccio alcuna lock perche' gli elementi che leggo (username e tags)
                            non possono essere modificati ( da altri thread )
                        * */

                        generator.writeStartObject();
                        generator.writeStringField("username",f_user.getUsername());

                        generator.writeArrayFieldStart("tags");

                        for(String tag : f_user.getTags()) {
                            generator.writeString(tag);
                        }

                        generator.writeEndArray();

                        generator.writeEndObject();
                    }

                    generator.writeEndArray();
                }

            }

        }


        generator.writeEndObject();
        return jsonResponseToString();

    }


    public String followUser(String username, String toFollow) throws IllegalArgumentException, IOException {

        generator.writeStartObject();

        if(!username.equals(toFollow)) {
            WSUser user = checkUser(username);
            WSUser userToFollow = checkUser(toFollow);

            if (user != null && userToFollow != null) {

                if (checkStatus(user) == 0) {
                    user.lockWrite();
                    boolean newFollowed = user.addFollowed(toFollow);
                    user.unlockWrite();
                    if (newFollowed == true) {
                        userToFollow.lockWrite();
                        userToFollow.addFollower(username);
                        userToFollow.unlockWrite();

                        generator.writeNumberField("status-code",HttpURLConnection.HTTP_CREATED);

                        try {
                            if (userToFollow.getSessionId() != -1)
                                userToFollow.notifyNewFollow(username, user.getTags());
                        } catch (RemoteException e) {
                            System.err.println("Impossibile notificare il client");
                            e.printStackTrace();
                        }

                    } else {
                        // se user segue gia' quell'utente non ritorno errori
                        generator.writeNumberField("status-code",HttpURLConnection.HTTP_OK);
                        generator.writeStringField("message", "segui gia' "+toFollow);
                    }

                }
            }
        } else {
            generator.writeNumberField("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            generator.writeStringField("message", "non puoi seguire te stesso");
        }



        generator.writeEndObject();
        return jsonResponseToString();



    }

    public String unfollowUser(String username, String toUnfollow) throws IllegalArgumentException, IOException {

        generator.writeStartObject();

        if(!username.equals(toUnfollow)) {
            WSUser user = checkUser(username);
            WSUser userToUnfollow = checkUser(toUnfollow);

            if (user != null && userToUnfollow != null) {
                if (checkStatus(user) == 0) {

                    user.lockWrite();
                    boolean wasFollowed = user.removeFollowed(toUnfollow);
                    user.unlockWrite();
                    if (wasFollowed == true) {
                        userToUnfollow.lockWrite();
                        userToUnfollow.removeFollower(username);
                        userToUnfollow.unlockWrite();

                        generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

                        try {
                            if (userToUnfollow.getSessionId() != -1)
                                userToUnfollow.notifyNewUnfollow(username);
                        } catch (RemoteException e) {
                            System.err.println("Impossibile notificare il client");
                            e.printStackTrace();
                        }
                    } else {
                        // se user non segue quell'utente non ritorno errori
                        generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);
                        generator.writeStringField("message", "non segui" + toUnfollow);
                    }

                }

            }
        } else {
            generator.writeNumberField("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            generator.writeStringField("message", "non puoi smettere di seguire te stesso");
        }

        generator.writeEndObject();
        return jsonResponseToString();


    }

    public String createPost(String username, String title, String content) throws IllegalArgumentException, IOException {

        if(title == null || content == null)
            throw new IllegalArgumentException();

        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            int id = idPostCounter++;
            Post p = new Post(id,username,title,content,rewardsIteration);
            // aggiungo il post alla Map di tutti i post
            posts.put(id,p);

            user.lockWrite();
            user.newPost(id);
            user.unlockWrite();

            generator.writeNumberField("status-code",HttpURLConnection.HTTP_CREATED);
            generator.writeNumberField("id-post",id);

        }

        generator.writeEndObject();

        return jsonResponseToString();

    }

    public String deletePost(String username, int idPost) throws IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            Post post = checkPost(idPost);
            if(post != null) {
                post.lockWrite(); // aspetto se un thread sta leggendo/scrivendo il post
                if(post.getAuthor().equals(username)) {
                    user.deletePost(idPost);
                    post.setDeleted(); // eventuali thread che sono in attesa di fare la rewin si accorgono che il post è già stato cancellato
                    post.unlockWrite();
                    // cancello il post dal blog dell'utente
                    user.lockWrite();
                    posts.remove(idPost);
                    user.unlockWrite();

                    HashSet<String> rewiners = post.getRewiners();

                    // cancello il post da tutti i blog degli utenti che lo hanno rewinnato
                    for(String r : rewiners) {
                        WSUser rewiner = registeredUser.get(r);
                        rewiner.lockWrite();
                        rewiner.deletePost(idPost);
                        rewiner.unlockWrite();
                    }


                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

                } else {
                    post.unlockWrite();
                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_FORBIDDEN);
                    generator.writeStringField("message",
                            "un post puo' essere cancellato solo dal suo autore");
                }

            }

        }

        generator.writeEndObject();
        return jsonResponseToString();
    }


    public String showFeed(String username) throws IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            generator.writeArrayFieldStart("feed");

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


                    generator.writeStartObject();
                    generator.writeNumberField("id-post",p.getId());
                    generator.writeStringField("author",p.getAuthor());
                    generator.writeStringField("title",p.getTitle());
                    generator.writeEndObject();

                    written++;
                }

                fUser.unlockRead();

            }

            generator.writeEndArray();

            if(written == 0) {
                generator.writeNumberField("status-code", HttpURLConnection.HTTP_NO_CONTENT);
                generator.writeStringField("message","non sono presenti post da visualizzare");
            }else
                generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);


        }

        generator.writeEndObject();

        return jsonResponseToString();

    }

    public String viewBlog(String username) throws IOException {
        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            user.lockRead();

            HashSet<Integer> blog = user.getBlog();

            if(!blog.isEmpty()) {
                generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

                generator.writeArrayFieldStart("blog");

                for(Integer id : blog) {

                    /* non mi serve fare la lock sul post perchè id,autore e titolo non possono cambiare
                    * e per cancellare/aggiungere post sul blog è necessaria la lockWrite sull'utente
                    * N.B. la lockWrite su user può essere acquisita se user ha fatto il rewin del post
                     */
                    Post p = posts.get(id);

                    generator.writeStartObject();
                    generator.writeNumberField("id-post",p.getId());
                    generator.writeStringField("author",p.getAuthor());
                    generator.writeStringField("title",p.getTitle());
                    generator.writeEndObject();

                }
                generator.writeEndArray();

            } else {
                generator.writeNumberField("status-code", HttpURLConnection.HTTP_NO_CONTENT);
                generator.writeStringField("message","non sono presenti post da visualizzare");
            }

            user.unlockRead();

        }

        generator.writeEndObject();

        return jsonResponseToString();
    }

    public String rewinPost(String username, int idPost) throws IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            Post post = checkPost(idPost);
            if(post != null) {
                post.lockWrite();
                // controllo se tra le due istruzioni precedenti un altro thread ha cancellato il post
                if(!checkDeleted(post) && !checkAuthor(username,post) && checkFeed(user,post)) {

                    user.lockWrite(); // per evitare letture inconsistenti alla showFeed e viewBlog
                    if (user.getBlog().add(idPost) == true) {
                        generator.writeNumberField("status-code", HttpURLConnection.HTTP_CREATED);
                    } else {
                        generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);
                        generator.writeStringField("message","post gia' presente nel blog");
                    }
                    user.unlockWrite();

                }
                post.unlockWrite();
            }


        }

        generator.writeEndObject();

        return jsonResponseToString();
    }

    public String ratePost(String username, int idPost, int vote) throws IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            Post post = checkPost(idPost);
            if(post != null) {
                post.lockWrite();

                if(!checkDeleted(post) && !checkAuthor(username,post) && checkFeed(user,post) && !alreadyVoted(username,post)) {


                    if (vote == 1) {
                        post.newUpvote(username);

                        // memorizzo nuovo voto +

                        if(!newUpvotes.containsKey(idPost))
                            newUpvotes.put(idPost,new HashSet<>());

                        newUpvotes.get(idPost).add(username); // aggiungo l'username al set dei votanti

                    }
                    else {
                        post.newDownvote(username);

                        // memorizzo nuovo voto -

                        if(!newDownvotes.containsKey(idPost))
                            newDownvotes.put(idPost,new HashSet<>());

                        newDownvotes.get(idPost).add(username); // aggiungo l'username al set dei votanti
                    }

                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_CREATED);

                }

                post.unlockWrite();
            }

        }

        generator.writeEndObject();

        return jsonResponseToString();


    }

    public String commentPost(String username, int idPost, String comment) throws IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            Post post = checkPost(idPost);

            if(post != null) {
                post.lockWrite();
                if(!checkDeleted(post) && !checkAuthor(username,post) && checkFeed(user,post)) {
                    post.newComment(username, comment);

                    // memorizzo nuvo commento

                    if(!newComments.containsKey(idPost))
                        newComments.put(idPost,new ArrayList<>());

                    newComments.get(idPost).add(username); // a differenza dei voti possono esserci duplicati

                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_CREATED);
                }
                post.lockWrite();
            }

        }

        generator.writeEndObject();

        return jsonResponseToString();


    }


    public String showPost(String username, int idPost) throws IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            Post post = checkPost(idPost);

            if(post != null) {

                post.lockRead();

                /* va a buon fine se :
                    il post e' di user
                    or il post e' nel blog di user (quindi anche rewinned)
                    or il post e' nel feed di user
                 */
                if(!checkDeleted(post) && post.getAuthor().equals(username) || user.getBlog().contains(idPost) || checkFeed(user,post)) {
                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);
                    generator.writeStringField("title",post.getTitle());
                    generator.writeStringField("content",post.getContent());
                    generator.writeNumberField("upvote",post.getUpvote().size());
                    generator.writeNumberField("downvote",post.getDownvote().size());

                    generator.writeArrayFieldStart("comments");
                    for(Map.Entry<String,ArrayList<String>> c : post.getComments().entrySet()) {
                        for(String cont : c.getValue()) {
                            generator.writeStartObject();
                            generator.writeStringField("comment-author",c.getKey());
                            generator.writeStringField("comment-content",cont);
                            generator.writeEndObject();
                        }
                    }
                    generator.writeEndArray();
                }
                post.unlockRead();
            }
        }

        generator.writeEndObject();

        return jsonResponseToString();

    }


    public String getWallet(String username) throws IOException {
        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            user.lockRead();

            generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

            generator.writeNumberField("wallet",user.getWallet());

            ArrayList<Transaction> transactions = user.getTransactions();

            if(!transactions.isEmpty()) {
                generator.writeArrayFieldStart("transactions");

                for (Transaction t : transactions) {
                    generator.writeStartObject();
                    generator.writeNumberField("id", t.getId());
                    generator.writeStringField("timestamp", t.getTimestamp());
                    generator.writeNumberField("value", t.getValue());
                    generator.writeEndObject();
                }

                generator.writeEndArray();
            }
            user.unlockRead();

        }

        generator.writeEndObject();

        return jsonResponseToString();
    }

    public String getWalletInBitcoin(String username) throws IOException {
        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {
            user.lockRead();
            double wincoinWallet =  user.getWallet();
            user.unlockRead();

            double walletBtc =  1.638344810037658 * getExchangeRate();
//            double walletBtc =  wincoinWallet * getExchangeRate(); todo remove comment

            generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

            generator.writeNumberField("wallet-btc",walletBtc);



        }

        generator.writeEndObject();

        return jsonResponseToString();
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
    private boolean checkFeed(WSUser user, Post post) throws IOException {

        /**
         * verifico se l'utente segue l'autore del post
         * oppure se segue almeno uno degli utenti che lo
         * hanno rewinnato
         */

        if((user.getFollowed().contains(post.getAuthor()) ||
        !Collections.disjoint(post.getRewiners(),user.getFollowed())) == false) {
            generator.writeNumberField("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            generator.writeStringField("message", "post non presente nel tuo feed");
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
    private boolean checkAuthor(String username,Post post) throws IOException {
        if(post.getAuthor().equals(username)){
            generator.writeNumberField("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            generator.writeStringField("message", "non puoi commentare/votare/rewinnare i tuoi post");
            return true;
        }

        return false;

    }

    private boolean alreadyVoted(String username, Post post) throws IOException {
        if(post.voted(username) == true) {
            generator.writeNumberField("status-code",HttpURLConnection.HTTP_FORBIDDEN);
            generator.writeStringField("message", "hai gia' votato il post "+post.getId());
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


    private WSUser checkUser(String username) throws IOException {

        if(username == null)
            throw new IllegalArgumentException();

        WSUser user = registeredUser.get(username);

        if(user == null) {
            generator.writeNumberField("status-code",HttpURLConnection.HTTP_UNAUTHORIZED);
            generator.writeStringField("message", "nessun utente registrato come "+username);
            return null;
        }

        return user;

    }

    private int checkStatus(WSUser user) throws IOException {
        if(user.alreadyLogged())
            return 0;

        generator.writeNumberField("status-code",HttpURLConnection.HTTP_UNAUTHORIZED);
        generator.writeStringField("message", "azione non permessa, login non effettuato");
        return -1;
    }


    private Post checkPost(int id) throws IOException {

        if(id < 0)
            throw new IllegalArgumentException();

        Post post = posts.get(id);

        if(post == null) {
            generator.writeNumberField("status-code",HttpURLConnection.HTTP_NOT_FOUND);
            generator.writeStringField("message", "post "+id+" non trovato");
            return null;
        }

        return post;

    }


    private boolean checkDeleted(Post p) throws IOException {
        if(p.isDeleted()){
            generator.writeNumberField("status-code",HttpURLConnection.HTTP_NOT_FOUND);
            generator.writeStringField("message", "post "+p.getId()+" non trovato");
        }

        return false;
    }

    private String jsonResponseToString() throws IOException {

        generator.flush();
        String response = responseStream.toString().trim();
        responseStream.reset();
        return response;
    }


    public void go() {

        registeredUser.put("u1", new WSUser("u1", "a", null));
        registeredUser.put("u2", new WSUser("u2", "", null));
        registeredUser.put("u3", new WSUser("u3", "", null));
        registeredUser.put("u4", new WSUser("u4", "", null));
        registeredUser.put("u5", new WSUser("u5", "", null));
        registeredUser.put("u6", new WSUser("u6", "", null));

        // Add some relationships between users
        try {
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

        } catch (IOException e) {
            e.printStackTrace();


        }
    }


}
