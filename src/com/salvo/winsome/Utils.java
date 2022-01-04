package com.salvo.winsome;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * @author Salvatore Guastella
 */
public class Utils {


    public static HashMap<String,String> parsingConfigFile(String configPath) {


        HashMap<String,String> result = new HashMap<>();


        try (BufferedReader fileIn =  new BufferedReader(new FileReader(configPath))) {

            String line;

            while ((line = fileIn.readLine())!= null) {
                if(parseLine(line, result) == -1) {
                    System.err.println("file non configurazione non valido");
                    return null;
                }
            }

            if(result.isEmpty()){
                System.err.println("file di configurazione vuoto");
                return null;
            }


        } catch (FileNotFoundException e) {
            System.err.println("path del file di configurazione non valido");
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }


        return result;

    }

    private static int parseLine(String line, HashMap<String, String> result) {

        if(line.trim().equals("") || line.startsWith("#"))
            return 0;

        StringTokenizer st = new StringTokenizer(line,"=");

        try {

            String key = st.nextToken().trim().toLowerCase(); // puo' essere sia maiuscolo che minuscolo
            //System.out.println("key: "+key);
            String value = st.nextToken().trim();
            //System.out.println("value: "+value);

            if(key.equals("") || value.trim().equals("")) {
                return -1;
            }

            // se un parametro viene inserito piu' volte prendo sempre il primo
            result.putIfAbsent(key,value);


        } catch (NullPointerException e) {
            return -1;
        }


        return 0;

    }

}
