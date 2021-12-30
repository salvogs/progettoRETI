package com.salvo.winsome.server;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
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
    ObjectMapper mapper;

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
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
//        this.generator.setCodec(new ObjectMapper()); // per la serializzazione di oggetti

    }


    public void run() {
        System.out.println(request);
        int resCode = HttpURLConnection.HTTP_BAD_REQUEST; // codice di risposta di default
        try {
//            JsonToken token = parser.nextToken();


            JsonNode req = mapper.readTree(request);

            JsonNode op = req.get("request-type");




            switch (op.asText()) {
                case "login":{

                    String username = parseNextTextField(req,"username");
                    if(username != null){

                        String password = parseNextTextField(req,"password");
                        if(password != null){

                            SocketChannel clientChannel = (SocketChannel) key.channel();
                            int ret = server.login(username,password,clientChannel.getRemoteAddress().hashCode());
                            if(ret == 0)
                                resCode = HttpURLConnection.HTTP_OK;
                            else if(ret == -1)
                                resCode = HttpURLConnection.HTTP_UNAUTHORIZED;
                            else if(ret == -2)
                                resCode = HttpURLConnection.HTTP_FORBIDDEN;
                        }
                    }

                }

                    break;

                case "logout":{

                    String username = parseNextTextField(req,"username");
                    if(username != null){

                        SocketChannel clientChannel = (SocketChannel) key.channel();

                        int ret = server.logout(username,clientChannel.getRemoteAddress().hashCode());

                        if(ret == 0)
                            resCode = HttpURLConnection.HTTP_OK;
                        else if(ret == -1)
                            resCode = HttpURLConnection.HTTP_UNAUTHORIZED;
                        else if(ret == -2)
                            resCode = HttpURLConnection.HTTP_FORBIDDEN;

                    }
                }
                    break;

                case "follow": {
                    String username = parseNextTextField(req,"username");
                    if(username != null) {

                        String toFollow = parseNextTextField(req,"to-follow");
                        if (toFollow != null) {

                            int ret = server.followUser(username,toFollow);

                            if(ret == 0)
                                resCode = HttpURLConnection.HTTP_OK;
                            else if(ret == -1)
                                resCode = HttpURLConnection.HTTP_NOT_FOUND;
                            else if(ret == -2)
                                resCode = HttpURLConnection.HTTP_UNAUTHORIZED;
                            else if(ret == -3)
                                resCode = HttpURLConnection.HTTP_CONFLICT;

                        }
                    }
                }
                    break;

                case "unfollow": {
                    String username = parseNextTextField(req,"username");
                    if(username != null) {

                        String toUnfollow = parseNextTextField(req,"to-unfollow");
                        if (toUnfollow != null) {

                            int ret = server.unfollowUser(username,toUnfollow);

                            if(ret == 0)
                                resCode = HttpURLConnection.HTTP_OK;
                            else if(ret == -1)
                                resCode = HttpURLConnection.HTTP_NOT_FOUND;
                            else if(ret == -2)
                                resCode = HttpURLConnection.HTTP_UNAUTHORIZED;
                            else if(ret == -3)
                                resCode = HttpURLConnection.HTTP_CONFLICT;

                        }
                    }
                }

                    break;

                case "list-users":{
                    String username = parseNextTextField(req,"username");
                    if(username != null) {
                        HashMap<String, String[]> selectedUsers = server.listUsers(username);

                        if(selectedUsers == null)
                            resCode = HttpURLConnection.HTTP_UNAUTHORIZED;
                        else if(selectedUsers.isEmpty()){
                            resCode = HttpURLConnection.HTTP_NOT_FOUND;
                        }else{
                            resCode = HttpURLConnection.HTTP_OK;

                            usersAndTagsToJson(selectedUsers);

                        }
                    }
                }
                    break;

                case "list-following":{
                    String username = parseNextTextField(req,"username");
                    if(username != null) {
                        HashMap<String, String[]> followed = server.listFollowing(username);
                        if(followed == null)
                            resCode = HttpURLConnection.HTTP_UNAUTHORIZED;
                        else if(followed.isEmpty()){
                            resCode = HttpURLConnection.HTTP_NOT_FOUND;
                        }else {
                            resCode = HttpURLConnection.HTTP_OK;

                            usersAndTagsToJson(followed);

                        }

                    }
                }
                    break;
                case "create-post":{
                    String username = parseNextTextField(req,"username");

                    String title = parseNextTextField(req,"title");

                    String content = parseNextTextField(req,"content");


                }

                break;

                default: break;

            }


        } catch (IOException e) {
            System.err.println("parsing richiesta fallito");
        }

        try {

            if (responseStream.size() != 0) {
                sendResponse(resCode + "\r\n" + responseStream.toString());
                responseStream.reset();
            } else
                sendResponse(Integer.toString(resCode)); // potrebbe anche inviare 400 BAD_REQUEST

            System.out.println(resCode + "\r\n" + responseStream.toString());


        } catch (IOException e) {
            server.disconnetionHandler((SocketChannel) key.channel());
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


        server.registerRead(selector, clientChannel);

    }

    private String parseNextTextField(JsonNode req, String fieldName) throws IOException{
        JsonNode field = req.get(fieldName);

        return field != null ? field.asText() : null;
    }

//    private int parseFieldIntValue(String fieldName) {
//
//    }

    private void usersAndTagsToJson(HashMap<String,String[]> users) throws IOException {

        if(users == null)
            return;

        generator.writeStartArray();

        for (Map.Entry<String,String[]> entry : users.entrySet()){
            generator.writeStartObject();
            generator.writeStringField("username",entry.getKey());

            generator.writeArrayFieldStart("tags");

            for(String tag : entry.getValue()) {
                generator.writeString(tag);
            }

            generator.writeEndArray();

            generator.writeEndObject();
        }


        generator.writeEndArray();


        generator.flush();

    }

}



