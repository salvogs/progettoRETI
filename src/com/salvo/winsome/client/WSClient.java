package com.salvo.winsome.client;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salvo.winsome.RMIClientInterface;
import com.salvo.winsome.RMIServerInterface;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;


/**
 * @author Salvatore Guastella
 */
public class WSClient {

    private String loginUsername;
    private HashMap<String,String[]> followers;
    private RMIClient clientCallback;

    private RMIServerInterface remoteServer;

    private SocketChannel socket;

    ByteArrayOutputStream requestStream;
    JsonGenerator generator;

    JsonFactory jfactory;
    JsonParser parser;
    ObjectMapper mapper;

    public WSClient(String serverAddress,int tcpPort, String registryAddr, int registryPort, String serviceName) {

        this.loginUsername = null;
        this.followers = new HashMap<>();


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
                    System.err.println("ERRORE: Impossibile connettersi con il server,riprovo tra 1000ms");
                    attempts++;
                    Thread.sleep(1000);
                }
            }while(attempts < 10);

            if(attempts == 10) {
                System.out.println("connessione fallita");
                System.exit(-1);
            }


            System.out.println("Connessione stabilita con "+serverAddress+':'+tcpPort);


            requestStream = new ByteArrayOutputStream();
            this.jfactory = new JsonFactory();
            this.generator = jfactory.createGenerator(requestStream, JsonEncoding.UTF8);
            this.generator.useDefaultPrettyPrinter();
            this.mapper = new ObjectMapper();

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
                System.err.println("Username '"+username+"' gia' utilizzato");
            else if(ret == -2)
                System.err.println("Password non valida");



        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    public void login(String username, String password) {

        if(loginUsername != null){
            System.err.println("Hai gia' effettuato il login come: "+loginUsername);
            return;
        }

        try {

            generator.writeStartObject();
            generator.writeStringField("request-type", "login");
            generator.writeStringField("username", username);
            generator.writeStringField("password", password);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            System.out.println(requestStream.toString());
            writeRequest(request, request.length);

            requestStream.reset();

            // leggo risposta del server

            String response = getResponse();

            JsonNode res = mapper.readTree(response);



            int statusCode = getStatusCode(res);


            if (statusCode == HttpURLConnection.HTTP_OK) {

                // leggo eventuale lista followers

                retriveFollowers(res.get("followers"));

                loginUsername = username;

                this.clientCallback = new RMIClient(followers,loginUsername);

                // esporto stub clientCallback per permettere al server di notificare nuovi follow/unfollow

                try {
                    RMIClientInterface stub = (RMIClientInterface) UnicastRemoteObject.exportObject(clientCallback,0);

                    // mi registro per ricevere notifiche

                    if(remoteServer.registerForCallback(stub) == -1) {
                        System.err.println("registerForCallback fallita");
                        System.exit(-1);
                    }

                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                System.out.println("login effettuato con successo");

            }else
                System.out.println(res.get("message").asText());


        } catch (IOException e) {
            e.printStackTrace();// todo bad response
        }


    }

    public void logout() {

        if(loginUsername == null){
            System.err.println("Effettua il login prima di disconnetterti");
            return;
        }

        try {
            generator.writeStartObject();
            generator.writeStringField("request-type","logout");
            generator.writeStringField("username",loginUsername);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if(statusCode == HttpURLConnection.HTTP_OK) {
                loginUsername = null;
                followers.clear();
                System.out.println("logout effettuato con successo");
            } else
                System.out.println(res.get("message").asText());


        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public void listUsers() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }

        try {

            generator.writeStartObject();
            generator.writeStringField("request-type","list-users");
            generator.writeStringField("username",loginUsername);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);


            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if(statusCode == HttpURLConnection.HTTP_OK)
                printJsonUserAndTags(res.get("users"));
            else
                System.out.println(res.get("message").asText());



        } catch (IOException e) {
            e.printStackTrace();
        }

        }


    public void listFollowers() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        if(followers.isEmpty()){
            System.out.println("Non hai ancora nessun follower");
            return;
        }

        printUserAndTags(followers);
    }

    public void listFollowing() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }

        try {
            generator.writeStartObject();
            generator.writeStringField("request-type","list-following");
            generator.writeStringField("username",loginUsername);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);


            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if(statusCode == HttpURLConnection.HTTP_OK)
                printJsonUserAndTags(res.get("users"));
            else
                System.out.println(res.get("message").asText());


            } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void followUser(String username) {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        try {

            //            generator.setCodec(new ObjectMapper());

            generator.writeStartObject();
            generator.writeStringField("request-type","follow");
            generator.writeStringField("username",loginUsername);
            generator.writeStringField("to-follow",username);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if(statusCode == HttpURLConnection.HTTP_CREATED)
                System.out.println("Ora segui "+username);
            else
                System.out.println(res.get("message").asText());


        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void unfollowUser(String username) {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        try {

            generator.writeStartObject();
            generator.writeStringField("request-type","unfollow");
            generator.writeStringField("username",loginUsername);
            generator.writeStringField("to-unfollow",username);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if(statusCode == HttpURLConnection.HTTP_OK) {
                JsonNode m = res.get("message");
                System.out.println(m != null ? m.asText() : ("Hai smesso di seguire " + username));
            } else
                System.out.println(res.get("message").asText());




        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void createPost(String title, String content) {

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


        try {

            generator.writeStartObject();
            generator.writeStringField("request-type","create-post");
            generator.writeStringField("username",loginUsername);
            generator.writeStringField("title",title);
            generator.writeStringField("content",content);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if(statusCode == HttpURLConnection.HTTP_CREATED) {
                int idPost = res.get("id-post").asInt();
                System.out.println("Nuovo post creato (id=" + idPost + ")");
            }else
                System.out.println(res.get("message").asText());


        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    public void deletePost(int idPost) {

        if (loginUsername == null) {
            System.err.println("Effettua prima il login");
            return;
        }

        if(wantDelete()) return;

        try {

            generator.writeStartObject();
            generator.writeStringField("request-type", "delete-post");
            generator.writeStringField("username", loginUsername);
            generator.writeNumberField("id-post", idPost);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request, request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if (statusCode == HttpURLConnection.HTTP_OK)
                System.out.println("Post "+idPost+" cancellato");
            else
                System.out.println(res.get("message").asText());

        } catch (IOException e) {
            e.printStackTrace();
        }

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

    public void showFeed() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        try {

            generator.writeStartObject();
            generator.writeStringField("request-type","show-feed");
            generator.writeStringField("username",loginUsername);
            generator.writeEndObject();

            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if(statusCode == HttpURLConnection.HTTP_OK)
                printJsonPostList(res.get("feed"));
            else
                System.out.println(res.get("message").asText());


        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void blog() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }

        try {

            generator.writeStartObject();
            generator.writeStringField("request-type","view-blog");
            generator.writeStringField("username",loginUsername);
            generator.writeEndObject();

            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if(statusCode == HttpURLConnection.HTTP_OK) {
                printJsonPostList(res.get("blog"));
            }else
                System.out.println(res.get("message").asText());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void rewinPost(int idPost) {
        if(idPost < 0)
            throw new IllegalArgumentException();

        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }

        try {

            generator.writeStartObject();
            generator.writeStringField("request-type", "rewin-post");
            generator.writeStringField("username",loginUsername);
            generator.writeNumberField("id-post", idPost);
            generator.writeEndObject();

            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request, request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if (statusCode == HttpURLConnection.HTTP_CREATED)
                System.out.println("Rewin del post "+idPost+" effettuato");
            else
                System.out.println(res.get("message").asText());
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void ratePost(int idPost, String vote) {

        if(idPost < 0 || !vote.equals("+1") && !vote.equals("-1"))
            throw new IllegalArgumentException();

        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        try {

            generator.writeStartObject();
            generator.writeStringField("request-type", "rate-post");
            generator.writeStringField("username",loginUsername);
            generator.writeNumberField("id-post", idPost);
            generator.writeNumberField("vote", Integer.parseInt(vote));
            generator.writeEndObject();

            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request, request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if (statusCode == HttpURLConnection.HTTP_CREATED)
                System.out.println("Il post "+idPost+" e' stato votato "+
                        (vote.equals("+1") ? "positivamente" : "negativamente"));
            else
                System.out.println(res.get("message").asText());

        } catch (IOException e) {
            e.printStackTrace();
        }



    }

    public void addComment(int idPost, String comment) {

        if(idPost < 0 || comment == null)
            throw new IllegalArgumentException();

        comment = comment.trim();

        if(comment.length() == 0)
            throw new IllegalArgumentException();

        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        try {

            generator.writeStartObject();
            generator.writeStringField("request-type", "comment-post");
            generator.writeStringField("username",loginUsername);
            generator.writeNumberField("id-post", idPost);
            generator.writeStringField("comment",comment);
            generator.writeEndObject();

            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request, request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if (statusCode == HttpURLConnection.HTTP_CREATED)
                System.out.println("Commento pubblicato");
            else
                System.out.println(res.get("message").asText());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void showPost(int idPost) {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }


        try {

            generator.writeStartObject();
            generator.writeStringField("request-type", "show-post");
            generator.writeStringField("username",loginUsername);
            generator.writeNumberField("id-post", idPost);
            generator.writeEndObject();

            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request, request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if (statusCode == HttpURLConnection.HTTP_OK) {
                printJsonPost(res);
            } else if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED)
                System.err.println("Accesso non eseguito");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getWallet() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }

        try {

            generator.writeStartObject();
            generator.writeStringField("request-type", "wallet");
            generator.writeStringField("username",loginUsername);
            generator.writeEndObject();
            generator.flush();

            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request, request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if (statusCode == HttpURLConnection.HTTP_OK) {
                printJsonWallet(res);
            } else if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED)
                System.err.println("Accesso non eseguito");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getWalletInBitcoin() {
        if(loginUsername == null){
            System.err.println("Effettua prima il login");
            return;
        }

        try {

            generator.writeStartObject();
            generator.writeStringField("request-type", "wallet-btc");
            generator.writeStringField("username",loginUsername);
            generator.writeEndObject();
            generator.flush();

            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request, request.length);

            String response = getResponse();

            JsonNode res = mapper.readTree(response);

            int statusCode = getStatusCode(res);

            if (statusCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Wallet BTC: "+res.get("wallet-btc").asText());
            } else if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED)
                System.err.println("Accesso non eseguito");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private void writeRequest(byte[] request, int size) throws IllegalArgumentException, IOException{

        if(request == null || size != request.length)
            throw new IllegalArgumentException();

        // todo scrivere tutto insieme

        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        length.putInt(size);
        length.flip();

        // scrivo la prima parte del messaggio (dimensione messaggio) sul channel
        while(length.hasRemaining())
            socket.write(length);


        ByteBuffer req = ByteBuffer.wrap(request);

        // scrivo la seconda parte del messaggio (la richiesta vera e propria) sul channel

        while(req.hasRemaining()){
            socket.write(req);
        }

//        length.clear();



    }

    private String getResponse() throws IOException {
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);


        // leggo prima parte payload [length]

        socket.read(length);
        length.flip();
        int res_l = length.getInt();


        ByteBuffer response = ByteBuffer.allocate(res_l);

        socket.read(response);



        String res = new String(response.array());


        System.out.println(res);

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
        System.out.println("Wallet->>> "+wallet.get("wallet").asText() + " wincoins");

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





}
