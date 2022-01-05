package com.salvo.winsome.server;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salvo.winsome.RMIServerInterface;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
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


    private final int tcpPort;
    private final int udpPort;
    private final InetAddress multicastAddress;
    private final int multicastPort;
    private final int regPort;
    private final String regServiceName;




    /**
     * hash table degli utenti registrati
     */
    private HashMap<String, WSUser> registeredUser;

    private HashMap<String, ArrayList<WSUser>> allTags;


    private RMIServer remoteServer;

    private HashMap<Integer,String> hashUser; // corrispondenza hash ip

    int N_THREAD = 10;

    private ThreadPoolExecutor pool;

    private static final int MSG_BUFFER_SIZE = 1024;

    Object lock = new Object();

    private HashMap<Integer, Post> posts;
    private int idPostCounter = 0;


    ObjectMapper mapper = new ObjectMapper();
    JsonFactory jfactory = new JsonFactory();
    ByteArrayOutputStream responseStream;
    JsonGenerator generator;




    private HashMap<Integer, HashSet<String>> newUpvotes;
    private HashMap<Integer, HashSet<String>> newDownvotes;
    private HashMap<Integer, ArrayList<String>> newComments;


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

        this.registeredUser = new HashMap<>();
        this.posts = new HashMap<>();

        this.backupDir = new File("./backup"); // todo parse
        this.usersBackup = new File(backupDir + "/users.json");
        this.postsBackup = new File(backupDir + "/posts.json");

        restoreBackup(usersBackup,postsBackup);

        this.allTags = new HashMap<>();
        this.remoteServer = new RMIServer(registeredUser,allTags);
        this.hashUser = new HashMap<>();

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

    public HashMap<Integer, Post> getPosts() {
        return posts;
    }

    public void incrementWallet(String username, double value) {
        WSUser user = registeredUser.get(username);
        if(user != null) {
            user.incrementWallet(value);
        }
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
            if (!user.alreadyLogged()) {

                if (user.checkPassword(password)) { // ok

                    user.setLogged(true);
                    user.setSessionId(sessionId);

                    hashUser.put(sessionId, username);
                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

                    // invio la lista dei follower

                    if (!user.getFollowers().isEmpty()) {

                        Iterator it = user.getFollowers().iterator();
                        generator.writeArrayFieldStart("followers");

                        while (it.hasNext()) {
                            generator.writeStartObject();

                            WSUser follower = registeredUser.get(it.next());

                            generator.writeStringField("username", follower.getUsername());
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
                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_UNAUTHORIZED);
                    generator.writeStringField("message", "credenziali errate");
                }
            } else {
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

        if(user != null && checkStatus(user) == 0) {
            if(user.getSessionId() == sessionId) {

                hashUser.remove(user.getSessionId());
                user.setLogged(false);
                user.setSessionId(-1);

                user.setRemoteClient(null);
                generator.writeNumberField("status-code",HttpURLConnection.HTTP_OK);


            } else {
                generator.writeNumberField("status-code", HttpURLConnection.HTTP_FORBIDDEN);
                generator.writeStringField("message",
                        "azione non permessa, non e' possibile disconnettere un altro utente");
            }

        }
        generator.writeEndObject();
        return jsonResponseToString();

    }


    public String listUsers(String username) throws IllegalArgumentException, IOException {

        WSUser user = checkUser(username);
        generator.writeStartObject();
        if(user != null && checkStatus(user) == 0) {

            // array degli utenti
            generator.writeArrayFieldStart("users");

            String[] tags = user.getTags();
            HashSet<String> written = new HashSet<>();
            for (String tag : tags) {

                // gli utenti registrati con lo stesso tag
                ArrayList<WSUser> users = allTags.get(tag);
                
                for (WSUser u : users) {

                    if (!u.getUsername().equals(username) && !written.contains(u.getUsername())) {
                        generator.writeStartObject();

                        generator.writeStringField("username",u.getUsername());
                        generator.writeArrayFieldStart("tags");

                        for(String t : u.getTags()) {
                            generator.writeString(t);
                        }

                        generator.writeEndArray();

                        generator.writeEndObject();
                        written.add(u.getUsername());
                    }

                }
            }

            generator.writeEndArray();

            if(written.isEmpty()) {
                generator.writeNumberField("status-code", HttpURLConnection.HTTP_NO_CONTENT);
                generator.writeStringField("message","nessun utente con tag in comune");
            }else
                generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

        }


        generator.writeEndObject();
        return jsonResponseToString();

    }

    public String listFollowing(String username) throws IllegalArgumentException, IOException {

        WSUser user = checkUser(username);
        generator.writeStartObject();
        if(user != null && checkStatus(user) == 0) {


            HashSet<String> followed = user.getFollowed(); // utenti seguiti

            if(followed.isEmpty()) {
                generator.writeNumberField("status-code",HttpURLConnection.HTTP_NO_CONTENT);
                generator.writeStringField("message", "Non segui ancora nessuno");
            } else {

                generator.writeNumberField("status-code",HttpURLConnection.HTTP_OK);

                // array degli utenti
                generator.writeArrayFieldStart("users");

                for(String u : followed) {

                    WSUser f_user = registeredUser.get(u);

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


        generator.writeEndObject();
        return jsonResponseToString();

    }


    public String followUser(String username, String toFollow) throws IllegalArgumentException, IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();
        if(user != null && checkStatus(user) == 0) {

            if(!username.equals(toFollow)) {

                WSUser userToFollow = checkUser(toFollow);

                if(userToFollow != null) {
                    // se user segue gia' quell'utente non ritorno errori
                    if(user.getFollowed().contains(toFollow))
                    {
                        generator.writeNumberField("status-code",HttpURLConnection.HTTP_OK);
                        generator.writeStringField("message", "segui gia' "+toFollow);
                    } else {

                        user.addFollowed(toFollow);
                        userToFollow.addFollower(username);

                        try {
                            if(userToFollow.getSessionId() != -1)
                                userToFollow.notifyNewFollow(username,user.getTags()); // todo togliere commento
                        } catch (RemoteException e) {
                            e.printStackTrace();  // todo client termination
                        }

                        generator.writeNumberField("status-code",HttpURLConnection.HTTP_CREATED);
                    }


                }


            } else {
                generator.writeNumberField("status-code",HttpURLConnection.HTTP_FORBIDDEN);
                generator.writeStringField("message", "non puoi seguire te stesso");
            }

        }

        generator.writeEndObject();
        return jsonResponseToString();



    }

    public String unfollowUser(String username, String toUnfollow) throws IllegalArgumentException, IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();
        if(user != null && checkStatus(user) == 0) {

            if(!username.equals(toUnfollow)) {

                WSUser userToUnfollow = checkUser(toUnfollow);

                if(userToUnfollow != null) {
                    // se user non segue quell'utente non ritorno errori
                    if(!user.getFollowed().contains(toUnfollow))
                    {
                        generator.writeNumberField("status-code",HttpURLConnection.HTTP_OK);
                        generator.writeStringField("message", "non segui "+toUnfollow);
                    } else {

                        user.removeFollowed(toUnfollow);
                        userToUnfollow.removeFollower(username);

                        try {
                            if(userToUnfollow.getSessionId() != -1)
                                userToUnfollow.notifyNewUnfollow(username);
                        } catch (RemoteException e) {
                            e.printStackTrace();  // todo client termination
                        }

                        generator.writeNumberField("status-code",HttpURLConnection.HTTP_OK);
                    }


                }


            } else {
                generator.writeNumberField("status-code",HttpURLConnection.HTTP_FORBIDDEN);
                generator.writeStringField("message", "non puoi smettere di seguire te stesso");
            }

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

            Post p = new Post(id,username, title,content);

            user.newPost(p);

            posts.put(id,p);

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

                if(post.getAuthor().equals(username)) {

                    // cancello il post dal blog di user

                    user.getBlog().remove(idPost);

                    // cancello il post da tutti i blog degli utenti che lo hanno rewinnato
                    Iterator<String> it = post.getRewiners().iterator();

                    while(it.hasNext()) {
                        WSUser rewiner = registeredUser.get(it.next());
                        rewiner.getBlog().remove(idPost);
                    }

                    posts.remove(idPost);

                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

                } else {
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

            for(String u : user.getFollowed()) {

                HashSet<Integer> blog = registeredUser.get(u).getBlog();

                for(Integer id : blog) {

                    Post p = posts.get(id);

                    generator.writeStartObject();
                    generator.writeNumberField("id-post",p.getId());
                    generator.writeStringField("author",p.getAuthor());
                    generator.writeStringField("title",p.getTitle());
                    generator.writeEndObject();

                    written++;
                }

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

            HashSet<Integer> blog = user.getBlog();

            if(!blog.isEmpty()) {
                generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);

                generator.writeArrayFieldStart("blog");

                for(Integer id : blog) {

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

        }

        generator.writeEndObject();

        return jsonResponseToString();
    }

    public String rewinPost(String username, int idPost) throws IOException {

        WSUser user = checkUser(username);

        generator.writeStartObject();

        if(user != null && checkStatus(user) == 0) {

            Post post = checkPost(idPost);

            if(post != null && !checkAuthor(username,post) && checkFeed(user,post)) {
                if(!checkAuthor(username,post) && checkFeed(user,post)) {


                    if (user.getBlog().add(idPost) == true) {

                        post.addRewiner(username);
                        generator.writeNumberField("status-code", HttpURLConnection.HTTP_CREATED);
                    } else {
                        generator.writeNumberField("status-code", HttpURLConnection.HTTP_OK);
                        generator.writeStringField("message","post gia' presente nel blog");
                    }
                }
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


                if(!checkAuthor(username,post) && checkFeed(user,post) && !alreadyVoted(username,post)) {


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
                if(!checkAuthor(username,post) && checkFeed(user,post)) {
                    post.newComment(username, comment);

                    // memorizzo nuvo commento

                    if(!newComments.containsKey(idPost))
                        newComments.put(idPost,new ArrayList<>());

                    newComments.get(idPost).add(username); // a differenza dei voti possono esserci duplicati

                    generator.writeNumberField("status-code", HttpURLConnection.HTTP_CREATED);
                }
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
                /* va a buon fine se :
                    il post e' di user
                    or il post e' nel blog di user (quindi anche rewinned)
                    or il post e' nel feed di user
                 */

                if(post.getAuthor().equals(username) || user.getBlog().contains(idPost) || checkFeed(user,post)) {
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
            }
        }

        generator.writeEndObject();

        return jsonResponseToString();

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


    private String jsonResponseToString() throws IOException {

        generator.flush();
        String response = responseStream.toString().trim();
        responseStream.reset();
        return response;
    }


    public void go() {

        registeredUser.put("u1", new WSUser("u1", "", null));
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

//            ratePost("u2", 0, 1);
//            ratePost("u3", 0, -1);
            ratePost("u4", 0, -1);
            ratePost("u5", 0, -1);
//            ratePost("u6", 0, -1);

//            commentPost("u2", 0, "commento1");
//            commentPost("u3", 0, "commento1");
            for(int i =0; i < 33; i++)
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
