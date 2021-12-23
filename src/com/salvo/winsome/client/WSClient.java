package com.salvo.winsome.client;

import com.fasterxml.jackson.core.*;
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
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Salvatore Guastella
 */
public class WSClient {

    private String loginUsername;
    private HashSet<String> followers;
    private RMIClient clientCallback;

    private RMIServerInterface remoteServer;

    private SocketChannel socket;

    ByteArrayOutputStream requestStream;
    JsonGenerator generator;

    JsonFactory jfactory;



    public WSClient(String registryAddr, int registryPort, String serviceName) {

        this.loginUsername = null;
        this.followers = new HashSet<String>();


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

        public int register(String username, String password, String[] tags) {


        try {
            remoteServer.registerUser(username, password, tags);
        } catch (RemoteException e) {
            e.printStackTrace();
        }


        return 0;
    }

    public int login(String username, String password) {

        // implement login TODO
        try {

    //            generator.setCodec(new ObjectMapper());

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



        } catch (IOException e) {
            e.printStackTrace();
        }



        this.clientCallback = new RMIClient(followers,"SUGO USER");

        // esporto stub clientCallback per permettere al server di notificare nuovi follow/unfollow

        try {
            RMIClientInterface stub = (RMIClientInterface) UnicastRemoteObject.exportObject(clientCallback,0);

            // mi registro per ricevere notifiche

            remoteServer.registerForCallback(stub);



        } catch (RemoteException e) {
            e.printStackTrace();
        }



        return 0;
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


//            if(getResponseCode() == HttpURLConnection.HTTP_OK) {
//                remoteServer.unregisterForCallback(loginUsername);
//                loginUsername = null;
//                System.out.println("Logout effettuato con successo");
//            }else{
//
//            }



        } catch (IOException e) {
            e.printStackTrace();
        }



    }



    public int listFollowers() {
        return 0;
    }

    public int listFollowing() {
        return 0;
    }

    public int followUser() {
        return 0;
    }

    public int unfollowUser() {
        return 0;
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



//    private int
//
//        JsonParser parser = jfactory.createParser(res);
//
//        int resCode = 0;
//
//        while(parser.nextToken() != JsonToken.END_OBJECT) {
//            String name = parser.getCurrentName();
//            if ("response-code".equals(name)) {
//                parser.nextToken();
//                resCode = parser.getIntValue();
//            }else if ("".equals(name)) {
//                parser.nextToken();
//                resCode = parser.getIntValue();
//            }
//        }
//
//    }












}
