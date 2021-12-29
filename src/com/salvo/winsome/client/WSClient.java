package com.salvo.winsome.client;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salvo.winsome.RMIClientInterface;
import com.salvo.winsome.RMIServerInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


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


    public WSClient(String registryAddr, int registryPort, String serviceName) {

        this.loginUsername = null;
        this.followers = new HashMap<>();


        try {
            // individuo il registry sulla porta args[0]
            Registry r = LocateRegistry.getRegistry(registryAddr, registryPort);

            // copia serializzata dello stub esposto dal server remoto
            Remote remoteObject = r.lookup(serviceName);

            this.remoteServer = (RMIServerInterface) remoteObject;

            // tcp connection

            try {
                this.socket = SocketChannel.open(new InetSocketAddress("localhost", 9000));
            } catch (IOException e) {
                System.err.println("ERRORE: Impossibile connettersi con il server");
                System.exit(-1);
            }

            System.out.println("Connessione stabilita con il server - localhost:9000");


            requestStream = new ByteArrayOutputStream();
            this.jfactory = new JsonFactory();
            this.generator = jfactory.createGenerator(requestStream, JsonEncoding.UTF8);
            this.generator.useDefaultPrettyPrinter();




        } catch (NotBoundException e) {
            System.err.println("ERRORE: Nessuna corrispondenza sul registry per "+serviceName);
            System.exit(-1);
        } catch (RemoteException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (IOException ex) {
            ex.printStackTrace();
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
            generator.writeStringField("request-type","login");
            generator.writeStringField("username",username);
            generator.writeStringField("password",password);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);

            requestStream.reset();

            // leggo risposta del server

            String response = getResponse();

            int resCode = getResponseCode(response);

            if(resCode == HttpURLConnection.HTTP_OK) {
                loginUsername = username;
                System.out.println("login effettuato con successo");
            }else if(resCode == HttpURLConnection.HTTP_UNAUTHORIZED)
                System.err.println("login fallito: "+resCode);


        } catch (IOException e) {
            e.printStackTrace();
        }



        this.clientCallback = new RMIClient(followers,loginUsername);

        // esporto stub clientCallback per permettere al server di notificare nuovi follow/unfollow

        try {
            RMIClientInterface stub = (RMIClientInterface) UnicastRemoteObject.exportObject(clientCallback,0);

            // mi registro per ricevere notifiche

            remoteServer.registerForCallback(stub);



        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    public void logout() {

        if(loginUsername == null){
            System.err.println("Effettua il login prima di disconnetterti");
            return;
        }

        try {

            //            generator.setCodec(new ObjectMapper());

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

            int resCode = getResponseCode(response);

            if(resCode == HttpURLConnection.HTTP_OK) {

                loginUsername = null;

                System.out.println("logout effettuato con successo");



            }else if(resCode == HttpURLConnection.HTTP_UNAUTHORIZED)
                System.err.println("errore logout");
            //



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

            //            generator.setCodec(new ObjectMapper());

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

//            int resCode = getResponseCode(response);

//            if(resCode == HttpURLConnection.HTTP_OK) {

                parser = jfactory.createParser(response);
                this.parser.setCodec(new ObjectMapper());
                TypeReference<HashMap<String, String[]>> typeRef
                    = new TypeReference<HashMap<String, String[]>>() {};

                HashMap<String,String[]> users = parser.readValueAs(typeRef);

                printUserAndTags(users);
//            }



        } catch (IOException e) {
            e.printStackTrace();
        }

        }

    private void printUserAndTags(HashMap<String, String[]> users) {
        System.out.println(centerString(20,"Utente")+"|"+centerString(20,"Tag"));

        String line = new String(new char[40]).replace('\0', '-');
        System.out.println(line);


        for(Map.Entry<String,String[]> entry : users.entrySet()){
            String utente = entry.getKey();
            String[] tags = entry.getValue();

            System.out.print(centerString(20,utente)+"|  ");

            if(tags.length != 0){
                String s = Arrays.toString(tags);
                System.out.println(s.substring(1,s.length()-1));
            }

            System.out.println();


        }
    }


    public void listFollowers() {
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

            //            generator.setCodec(new ObjectMapper());

            generator.writeStartObject();
            generator.writeStringField("request-type","list-following");
            generator.writeStringField("username",loginUsername);
            generator.writeEndObject();
            generator.flush();
            byte[] request = requestStream.toByteArray();
            requestStream.reset();
            System.out.println(requestStream.toString());
            writeRequest(request,request.length);

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

            int resCode = getResponseCode(response);

            if(resCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Ora segui "+username);



            }else if(resCode == HttpURLConnection.HTTP_NOT_FOUND)
                System.err.println("L'utente "+username+" non esiste");
            //



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

            //            generator.setCodec(new ObjectMapper());

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

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public int viewBlog() {
        return 0;
    }

    public int createPost() {
        return 0;
    }



    private void writeRequest(byte[] request, int size) throws IllegalArgumentException, IOException{

        if(request == null || size != request.length)
            throw new IllegalArgumentException();



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


    private int getResponseCode(String response) throws IOException {
        parser = jfactory.createParser(response);

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldname = parser.getCurrentName();
            if ("response-code".equals(fieldname)) {
                parser.nextToken();
                return parser.getIntValue();
            }

        }

        return -1;

    }






    private String parseNextTextField(String fieldName) throws IOException{
        if(parser.nextToken() != null && parser.getCurrentName().equals(fieldName)) {
            parser.nextToken();
            System.out.println(parser.getText());
            return parser.getText();
        }

        return null;
    }




    private String centerString (int width, String s) {
        return String.format("%-" + width  + "s", String.format("%" + (s.length() + (width - s.length()) / 2) + "s", s));
    }




}
