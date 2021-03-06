package com.salvo.winsome.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salvo.winsome.RMIClientInterface;
import com.salvo.winsome.RMIServerInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @author Salvatore Guastella
 */
public class WSClient {

    private String loginUsername;
    private volatile AtomicBoolean doNotDisturb; // false -> off true-> on
    private HashMap<String,String[]> followers;
    private RMIClient clientCallback;

    private RMIServerInterface remoteServer;

    private SocketChannel socket;


    ObjectMapper mapper;

    MulticastListener mcastListener;
    Thread mcastListenerThread;


    public WSClient(String serverAddress,int tcpPort, String registryAddr, int registryPort, String serviceName) {

        this.loginUsername = null;
        this.followers = new HashMap<>();
        this.doNotDisturb = new AtomicBoolean();

        try {
            int attempts = 0;
            do {
                try {

                    // individuo il registry sulla porta args[0]
                    Registry r = LocateRegistry.getRegistry(registryAddr, registryPort);

                    // copia serializzata dello stub esposto dal server remoto
                    Remote remoteObject = r.lookup(serviceName);

                    this.remoteServer = (RMIServerInterface) remoteObject;

                    // tcp connection

                    this.socket = SocketChannel.open(new InetSocketAddress(serverAddress, tcpPort));

                    break;

                } catch (IOException e) {
                    System.err.println("Errore: Impossibile connettersi con il server,riprovo tra 1000ms");
                    attempts++;
                    Thread.sleep(1000);

                }
            }while(attempts < 10);

            if(attempts == 10) {
                System.out.println("Connessione fallita");
                System.exit(-1);
            }


            System.out.println("Connessione stabilita con "+serverAddress+':'+tcpPort);

            this.mapper = new ObjectMapper();
            this.mapper.enable(SerializationFeature.INDENT_OUTPUT);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

    public void register(String username, String password, String[] tags) {
        try {
            int ret = remoteServer.registerUser(username, password, tags);

            if(ret == 0)
                System.out.println("Utente '"+username+"' registrato con successo");
            else if(ret == -1)
                System.err.println("Username o Password non validi");
            else if(ret == -2)
                System.err.println("Username '"+username+"' gi?? utilizzato");


        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    public void login(String username, String password) throws IOException{

        if(loginUsername != null){
            System.err.println("Hai gia' effettuato il login come: "+loginUsername);
            return;
        }

        ObjectNode req = mapper.createObjectNode();
        req.put("request-type", "login");
        req.put("username", username);
        req.put("password", password);


        writeRequest(mapper.writeValueAsString(req));

        // leggo risposta del server

        String response = getResponse();

        JsonNode res = mapper.readTree(response);



        int statusCode = getStatusCode(res);


        if (statusCode == HttpURLConnection.HTTP_OK) {



            mcastListener = new MulticastListener(
                    res.get("multicast-address").asText(),res.get("multicast-port").asInt(),doNotDisturb);

            // avvio il thread in ascolto di notifiche sul MulticastSocket

            mcastListenerThread = new Thread(mcastListener);
            mcastListenerThread.start();

            // leggo eventuale lista followers

            retriveFollowers(res.get("followers"));

            loginUsername = username;

            this.clientCallback = new RMIClient(followers,doNotDisturb);

            // esporto stub clientCallback per permettere al server di notificare nuovi follow/unfollow

            try {
                RMIClientInterface stub = (RMIClientInterface) UnicastRemoteObject.exportObject(clientCallback,0);

                // mi registro per ricevere notifiche

                if(remoteServer.registerForCallback(stub,loginUsername) == -1) {
                    System.err.println("registerForCallback fallita");
                    System.exit(-1);
                }

            } catch (RemoteException e) {
                System.err.println("registerForCallback fallita");
//                e.printStackTrace();
                System.exit(-1);
            }

            System.out.println("login effettuato con successo");

        }else
            System.out.println(res.get("message").asText());


    }

    public void logout() throws IOException {

        if(loginUsername == null){
            System.err.println("Effettua il login prima di disconnetterti");
            return;
        }



        ObjectNode req = mapper.createObjectNode();
        req.put("request-type","logout");
        req.put("username",loginUsername);


        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if(statusCode == HttpURLConnection.HTTP_OK) {
            loginUsername = null;
            followers.clear();
            mcastListener.stop();
            try {
                mcastListenerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("logout effettuato con successo");
        } else
            System.out.println(res.get("message").asText());


    }


    public void listUsers() throws IOException {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }



        ObjectNode req = mapper.createObjectNode();
        req.put("request-type","list-users");
        req.put("username",loginUsername);


        writeRequest(mapper.writeValueAsString(req));


        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if(statusCode == HttpURLConnection.HTTP_OK)
            printJsonUserAndTags(res.get("users"));
        else
            System.out.println(res.get("message").asText());


    }


    public void listFollowers() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }

        synchronized (followers) {
            if(followers.isEmpty()){
                System.out.println("Non hai ancora nessun follower");
                return;
            }

            printUserAndTags(followers);
        }
    }

    public void listFollowing() throws IOException {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        ObjectNode req = mapper.createObjectNode();
        req.put("request-type","list-following");
        req.put("username",loginUsername);


        writeRequest(mapper.writeValueAsString(req));


        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if(statusCode == HttpURLConnection.HTTP_OK)
            printJsonUserAndTags(res.get("users"));
        else
            System.out.println(res.get("message").asText());



    }

    public void followUser(String username) throws IOException {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }




        ObjectNode req = mapper.createObjectNode();
        req.put("request-type","follow");
        req.put("username",loginUsername);
        req.put("to-follow",username);


        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if(statusCode == HttpURLConnection.HTTP_CREATED)
            System.out.println("Ora segui "+username);
        else
            System.out.println(res.get("message").asText());


    }

    public void unfollowUser(String username) throws IOException {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }



        ObjectNode req = mapper.createObjectNode();
        req.put("request-type","unfollow");
        req.put("username",loginUsername);
        req.put("to-unfollow",username);


        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if(statusCode == HttpURLConnection.HTTP_OK) {
            JsonNode m = res.get("message");
            System.out.println(m != null ? m.asText() : ("Hai smesso di seguire " + username));
        } else
            System.out.println(res.get("message").asText());

    }

    public void createPost(String title, String content) throws IOException{

        if(title == null || content == null)
            throw new IllegalArgumentException();

        title = title.trim();
        content = content.trim();

        if(title.length() == 0 || content.length() == 0)
            throw new IllegalArgumentException();

        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        boolean ret = false;
        if(title.length() > 20)
        { System.err.println("Il titolo deve essere lungo al massimo 20 caratteri"); ret = true; }

        if(content.length() > 500)
        { System.err.println("Il contenuto deve essere lungo al massimo 500 caratteri"); ret = true; }

        if(ret) return;


        ObjectNode req = mapper.createObjectNode();
        req.put("request-type","create-post");
        req.put("username",loginUsername);
        req.put("title",title);
        req.put("content",content);


        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if(statusCode == HttpURLConnection.HTTP_CREATED) {
            int idPost = res.get("id-post").asInt();
            System.out.println("Nuovo post creato (id=" + idPost + ")");
        }else
            System.out.println(res.get("message").asText());


    }


    public void deletePost(int idPost) throws IOException{

        if (loginUsername == null) {
            System.err.println("Effettua prima il login");
            return;
        }

        if(wantDelete()) return;


        ObjectNode req = mapper.createObjectNode();
        req.put("request-type", "delete-post");
        req.put("username", loginUsername);
        req.put("id-post", idPost);


        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if (statusCode == HttpURLConnection.HTTP_OK)
            System.out.println("Post "+idPost+" cancellato");
        else
            System.out.println(res.get("message").asText());

    }

    private boolean wantDelete() {
        System.out.println("Sei sicuro di voler cancellare il post? [y/n]");

        String res;
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            try {
                res = input.readLine();
            if ("y".equalsIgnoreCase(res)) {
                return false;
            }
            else if ("n".equalsIgnoreCase(res)) {
                System.out.println("Operazione cancellata");
                return true;
            }

            } catch (IOException e) {}
                System.out.println("Inserisci \"y\" o \"n\":");
        }
    }

    public void showFeed() throws IOException {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        ObjectNode req = mapper.createObjectNode();
        req.put("request-type","show-feed");
        req.put("username",loginUsername);

        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if(statusCode == HttpURLConnection.HTTP_OK)
            printJsonPostList(res.get("feed"));
        else
            System.out.println(res.get("message").asText());

    }

    public void blog() throws IOException {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        ObjectNode req = mapper.createObjectNode();
        req.put("request-type","view-blog");
        req.put("username",loginUsername);


        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if(statusCode == HttpURLConnection.HTTP_OK) {
            printJsonPostList(res.get("blog"));
        }else
            System.out.println(res.get("message").asText());


    }

    public void rewinPost(int idPost) throws IOException {
        if(idPost < 0)
            throw new IllegalArgumentException();

        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        ObjectNode req = mapper.createObjectNode();
        req.put("request-type", "rewin-post");
        req.put("username",loginUsername);
        req.put("id-post", idPost);

        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if (statusCode == HttpURLConnection.HTTP_CREATED)
            System.out.println("Rewin del post "+idPost+" effettuato");
        else
            System.out.println(res.get("message").asText());

    }

    public void ratePost(int idPost, String vote) throws IOException {

        if(idPost < 0 || !vote.equals("+1") && !vote.equals("-1"))
            throw new IllegalArgumentException();

        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        ObjectNode req = mapper.createObjectNode();
        req.put("request-type", "rate-post");
        req.put("username",loginUsername);
        req.put("id-post", idPost);
        req.put("vote", Integer.parseInt(vote));

        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if (statusCode == HttpURLConnection.HTTP_CREATED)
            System.out.println("Il post "+idPost+" e' stato votato "+
                    (vote.equals("+1") ? "positivamente" : "negativamente"));
        else
            System.out.println(res.get("message").asText());

    }

    public void addComment(int idPost, String comment) throws IOException {

        if(idPost < 0 || comment == null)
            throw new IllegalArgumentException();

        comment = comment.trim();

        if(comment.length() == 0)
            throw new IllegalArgumentException();

        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }

        ObjectNode req = mapper.createObjectNode();
        req.put("request-type", "comment-post");
        req.put("username",loginUsername);
        req.put("id-post", idPost);
        req.put("comment",comment);

        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if (statusCode == HttpURLConnection.HTTP_CREATED)
            System.out.println("Commento pubblicato");
        else
            System.out.println(res.get("message").asText());

    }


    public void showPost(int idPost) throws IOException {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }




        ObjectNode req = mapper.createObjectNode();
        req.put("request-type", "show-post");
        req.put("username",loginUsername);
        req.put("id-post", idPost);

        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if (statusCode == HttpURLConnection.HTTP_OK)
            printJsonPost(res);
        else
            System.out.println(res.get("message").asText());

    }

    public void getWallet() throws IOException {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        ObjectNode req = mapper.createObjectNode();
        req.put("request-type", "wallet");
        req.put("username",loginUsername);


        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if (statusCode == HttpURLConnection.HTTP_OK)
            printJsonWallet(res);
        else
            System.out.println(res.get("message").asText());

    }

    public void getWalletInBitcoin() throws IOException {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        ObjectNode req = mapper.createObjectNode();
        req.put("request-type", "wallet-btc");
        req.put("username",loginUsername);


        writeRequest(mapper.writeValueAsString(req));

        String response = getResponse();

        JsonNode res = mapper.readTree(response);

        int statusCode = getStatusCode(res);

        if (statusCode == HttpURLConnection.HTTP_OK)
            System.out.println("Wallet BTC: "+res.get("wallet-btc").asText());
        else
            System.out.println(res.get("message").asText());

    }


    public void enableDoNotDisturb() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }
        doNotDisturb.set(true);

        System.out.println("Notifiche disabilitate");
    }

    public void disableDoNotDisturb() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }
        doNotDisturb.set(false);

        System.out.println("Notifiche abilitate");
    }
    
    private void writeRequest(String request) throws IllegalArgumentException, IOException {

        if(request == null)
            throw new IllegalArgumentException();
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES+request.length());
        buffer.putInt(request.length());
        buffer.put(ByteBuffer.wrap(request.getBytes()));

        buffer.flip();

        try {
            while (buffer.hasRemaining())
                socket.write(buffer);

        } catch (IOException e) {
            System.err.println("Errore: Impossibile inviare richiesta");
            socket.close();
            System.exit(-1);
        }

    }

    private String getResponse() throws IOException {

        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer response = null;
        try {
            // leggo prima parte payload [length]
            socket.read(length);
            length.flip();
            int res_l = length.getInt();


            response = ByteBuffer.allocate(res_l);
            socket.read(response);


        } catch (IOException e) {
            System.err.println("Errore: Impossibile leggere risposta");
            socket.close();
            System.exit(-1);
        }

        String res = new String(response.array());
//        System.out.println(res);
        return res;

    }


    private int getStatusCode(JsonNode response) {

        JsonNode jn = response.get("status-code");

        return jn.asInt();



    }


    private String centerString (int width, String s) {
        return String.format("%-" + width  + "s", String.format("%" + (s.length() + (width - s.length()) / 2) + "s", s));
    }


    private void printUserAndTags(HashMap<String,String[]> users) {

        System.out.println(centerString(20,"Utente")+"|"+centerString(20,"Tag"));
        String line = new String(new char[40]).replace('\0', '-');
        System.out.println(line);


        for(Map.Entry<String,String[]> entry : users.entrySet()){
            String username = entry.getKey();
            String[] tags = entry.getValue();

            System.out.print(centerString(20,username)+"|  ");

            if(tags.length != 0){
                String s = Arrays.toString(tags);
                System.out.print(s.substring(1,s.length()-1));
            }

            System.out.println();
        }

    }

    private void printJsonUserAndTags(JsonNode users) {

        if(!users.isArray()) { System.err.println("bad response"); System.exit(-1); }


        System.out.println(centerString(20,"Utente")+"|"+centerString(20,"Tag"));

        String line = new String(new char[40]).replace('\0', '-');
        System.out.println(line);


        for(JsonNode user : users) {
            String username = user.get("username").asText();
            System.out.print(centerString(20,username)+"|  ");
            JsonNode tags = user.get("tags");
            if(tags.isArray() && tags.size() != 0 ){
//                    System.out.println(tags);
                for(int i = 0; i < tags.size(); i++)
                    System.out.print(tags.get(i).asText()+ (i < tags.size()-1 ? ", " : "\r\n"));
            }else
                System.out.println();

        }

    }



    private void printJsonPostList(JsonNode posts) {

        if (!posts.isArray()) {
            System.err.println("bad response");
            System.exit(-1);
        }


        System.out.println(centerString(20, "Id") + "|" + centerString(20, "Autore") + "|  Titolo");

        String line = new String(new char[60]).replace('\0', '-');
        System.out.println(line);


        for (JsonNode post : posts) {
            int idPost = post.get("id-post").asInt();
            System.out.print(centerString(20, String.valueOf(idPost)) + "|");

            String author = post.get("author").asText();
            System.out.print(centerString(20, author) + "|");

            String title = post.get("title").asText();
            System.out.print(centerString(20, "\""+title +"\"")+"\n");

        }

    }

    private void printJsonPost(JsonNode post) {

        if (!post.isObject()) {
            System.err.println("bad response");
            System.exit(-1);
        }



        String line = new String(new char[40]).replace('\0', '-');
        System.out.println(line);


        String title = post.get("title").asText();
        String content = post.get("content").asText();

        int upvote = post.get("upvote").asInt();
        int downvote = post.get("downvote").asInt();

        JsonNode comments = post.get("comments");

        if(!comments.isArray()) {
            System.err.println("bad response");
            System.exit(-1);
        }


        System.out.println("Titolo: "+title);
        System.out.println("Contenuto: "+content);
        System.out.println("Voti positivi: "+upvote);
        System.out.println("Voti negativi: "+downvote);
        System.out.print("Commenti:");

        if(comments.size() == 0)
            System.out.println(0);
        else {
            System.out.println();
            for (JsonNode c : comments)
                System.out.println("\t"+c.get("comment-author").asText()+": \""+c.get("comment-content").asText()+"\"");
        }

    }

    private void printJsonWallet(JsonNode wallet) {
        System.out.println("Wallet: "+wallet.get("wallet").asText() + " wincoins");

        JsonNode transactions = wallet.get("transactions");

        if(transactions != null && transactions.size() > 0 && transactions.isArray()) {

            System.out.println(centerString(10, "Id") + "|" + centerString(22, "Data") + "|  Valore");

            System.out.println(new String(new char[55]).replace('\0', '-'));
            for(JsonNode t : transactions){

                System.out.print(centerString(10, t.get("id").asText()) + "|");


                System.out.print(centerString(22,t.get("timestamp").asText())+ "|");

                System.out.println(centerString(22, t.get("value").asText()));

            }
        }
    }


    private void retriveFollowers(JsonNode fnode) {

        if(fnode != null && fnode.isArray()) {
            for (JsonNode follower : fnode) {
                String username = follower.get("username").asText();
                JsonNode tnode = follower.get("tags");

                String[] tags = new String[tnode.size()];

                int i = 0;
                for(JsonNode tag : tnode) {
                    tags[i] = tag.asText();
                    i++;
                }

                followers.put(username,tags);
            }
        }
    }


    public void stop() throws IOException {
        if(loginUsername != null) this.logout();
        socket.close();
    }


}
