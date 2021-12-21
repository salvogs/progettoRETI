package com.salvo.winsome.client;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salvo.winsome.RMIClientInterface;
import com.salvo.winsome.RMIServerInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Salvatore Guastella
 */
public class WSClient {

    private String loginName;
    private ConcurrentHashMap<String,String> followers;
    private RMIClient clientCallback;

    private RMIServerInterface remoteServer;

    private SocketChannel socket;



    public WSClient(String registryAddr, int registryPort, String serviceName) {

        this.loginName = null;
        this.followers = new ConcurrentHashMap<String, String>();


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


        } catch (NotBoundException e) {
            System.err.println("ERRORE: Nessuna corrispondenza sul registry per "+serviceName);
            System.exit(-1);
        } catch (RemoteException e) {
            e.printStackTrace();
            System.exit(-1);
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
        String s = "login " + username + password;
        int l = s.length();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonFactory factory = new JsonFactory();
        try (JsonGenerator generator =
                     factory.createGenerator(stream, JsonEncoding.UTF8)) {
            generator.useDefaultPrettyPrinter();
//            generator.setCodec(new ObjectMapper());

            generator.writeStartObject();
            generator.writeStringField("username",username);
            generator.writeStringField("password",password);
            generator.writeEndObject();

        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            writeRequest(stream.toByteArray(),stream.size());
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

    public int logout() {
        return 0;
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


    private void readResponse() {

    }













}
