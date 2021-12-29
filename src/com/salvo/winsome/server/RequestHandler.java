package com.salvo.winsome.server;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Salvatore Guastella
 */
public class RequestHandler implements Runnable{

    /*
        riferimento al server per accedere ai suoi metodi
     */
    private WSServer server;

    private String request;
    private Selector selector;
    private SelectionKey key; // todo passare solo channel?
    JsonFactory jfactory = new JsonFactory();
    JsonParser parser ;
    ByteArrayOutputStream responseStream;
    JsonGenerator generator;


    /**
     * @param request la richiesta del client da gestire
     * @param selector il selector dove e' registrato il channel del client
     * @param key selectable channel del client da registrare
     *        per OP_WRITE quando e' pronta una risposta
     * @throws IOException
     */
    public RequestHandler(WSServer server, String request, Selector selector, SelectionKey key) throws IOException {
        this.server = server;
        this.request = request;
        this.selector = selector;
        this.key = key;

        this.parser = jfactory.createParser(request);
//        parser.setCodec(new ObjectMapper())
        responseStream = new ByteArrayOutputStream();
        this.generator = jfactory.createGenerator(responseStream, JsonEncoding.UTF8);
        this.generator.useDefaultPrettyPrinter();
        this.generator.setCodec(new ObjectMapper()); // per la serializzazione di oggetti

    }


    public void run() {
        System.out.println(request);
        try {
            JsonToken token = parser.nextToken();

            while (token != null && token != JsonToken.END_OBJECT) {
                String fieldname = parser.getCurrentName();
                if ("request-type".equals(fieldname)) {
                    if(parser.nextToken() != null){

                        System.out.println("->"+parser.getText());
                        switch (parser.getText()) {
                            case "login":{

                                String username = parseNextTextField("username");
                                if(username != null){


                                    String password = parseNextTextField("password");
                                    if(password != null){

                                        // todo login

                                        int resCode;

                                        SocketChannel clientChannel = (SocketChannel) key.channel();

                                        if(server.login(username,password,clientChannel.getRemoteAddress().hashCode()) == 0)
                                            resCode = HttpURLConnection.HTTP_OK;
                                        else
                                            resCode = HttpURLConnection.HTTP_UNAUTHORIZED;

                                        generator.writeStartObject();

                                        generator.writeNumberField("response-code", resCode);


                                        generator.writeEndObject();

                                        generator.flush();


                                    }
                                }

//                                System.err.println("PARSING");
                            }

                                break;

                            case "logout":{

                                String username;
                                if((username = parseNextTextField("username")) != null){

                                    int resCode;
                                    // todo logout
                                    if(server.logout(username) == 0)
                                        resCode = HttpURLConnection.HTTP_OK;
                                    else
                                        resCode = HttpURLConnection.HTTP_UNAUTHORIZED;

                                    generator.writeStartObject();

                                    generator.writeNumberField("response-code", resCode);

                                    generator.writeEndObject();

                                    generator.flush();
                                }

                                break;

                            }

                            case "follow": {
                                String username = parseNextTextField("username");
                                if(username != null) {


                                    String toFollow = parseNextTextField("to-follow");
                                    if (toFollow != null) {

                                        int ret = server.followUser(username,toFollow);
                                        int resCode = 0;
                                        if(ret == 0)
                                            resCode = HttpURLConnection.HTTP_OK;
                                        else if(ret == -1)
                                            resCode = HttpURLConnection.HTTP_NOT_FOUND;
                                        else if(ret == -2)
                                            resCode = HttpURLConnection.HTTP_FORBIDDEN; // azione rifiutata


                                        generator.writeStartObject();

                                        generator.writeNumberField("response-code", resCode);

                                        generator.writeEndObject();

                                        generator.flush();
                                    }
                                }
                            }
                                break;

                            case "unfollow": {
                                String username = parseNextTextField("username");
                                if(username != null) {


                                    String toUnfollow = parseNextTextField("to-unfollow");
                                    if (toUnfollow != null) {

                                        int ret = server.unfollowUser(username,toUnfollow);
                                        int resCode = 0;
                                        if(ret == 0)
                                            resCode = HttpURLConnection.HTTP_OK;
                                        else if(ret == -1)
                                            resCode = HttpURLConnection.HTTP_NOT_FOUND;
                                        else if(ret == -2)
                                            resCode = HttpURLConnection.HTTP_FORBIDDEN; // azione rifiutata


                                        generator.writeStartObject();

                                        generator.writeNumberField("response-code", resCode);

                                        generator.writeEndObject();

                                        generator.flush();
                                    }
                                }
                            }

                                break;

                            case "list-users":{
                                String username = parseNextTextField("username");
                                if(username != null) {
                                    HashMap<String, String[]> selectedUsers = server.listUsers(username);

                                    generator.writeObject(selectedUsers);
                                    generator.flush();
//                                    for (Map.Entry<String, String[]> entry : selectedUsers.entrySet()) {
//                                        String utente = entry.getKey();
//                                        String[] tags = entry.getValue();
//                                        System.out.print(utente + " ");
//
//                                        for (String tag : tags)
//                                            System.out.print(tag + ",");
//
//
//                                    }
                                }
                            }
                                break;

                            case "list-following":{
                                String username = parseNextTextField("username");
                                if(username != null) {
                                    HashMap<String, String[]> followed = server.listFollowing(username);

                                    generator.writeObject(followed);
                                    generator.flush();

                                }
                            }
                                break;
                            case "create-post":{
                                String username = parseNextTextField("username");

                                String title = parseNextTextField("title");

                                String content = parseNextTextField("content");


                            }

                            break;

                            default: break;

                        }

                    }


                }
                token = parser.nextToken();
            }
            parser.close();




            System.out.println(responseStream.toString());

            sendResponse(responseStream.toString());

            responseStream.reset();


        } catch (IOException e){
            System.err.println("parsing richiesta fallito");
        }
    }




    private void sendResponse(String response) throws IOException {

        SocketChannel clientChannel = (SocketChannel) key.channel();


        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        length.putInt(response.length());
        length.flip();

        // scrivo la prima parte del messaggio (dimensione messaggio) sul channel
        while(length.hasRemaining())
            clientChannel.write(length);


        ByteBuffer req = ByteBuffer.wrap(response.getBytes());

        // scrivo la seconda parte del messaggio (la richiesta vera e propria) sul channel

        while(req.hasRemaining())
            clientChannel.write(req);


//        if(clientChannel.isConnected())
//            System.out.println("CONNECTED");
//
//        if(clientChannel.isRegistered())
//            System.out.println("REGISTERED");
//
//        if(clientChannel.isBlocking())
//            System.out.println("BLOCKING");


        server.registerRead(selector, clientChannel);


    }

    private String parseNextTextField(String fieldName) throws IOException{
        if(parser.nextToken() != null && parser.getCurrentName().equals(fieldName)) {
            parser.nextToken();
            System.out.println(parser.getText());
            return parser.getText();
        }

        return null;
    }

//    private int parseFieldIntValue(String fieldName) {
//
//    }


}



