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
public class ConfigParser {


    public static HashMap<String,String> parseConfigFile(String configPath) {


        HashMap<String,String> result = new HashMap<>();


        try (BufferedReader fileIn =  new BufferedReader(new FileReader(configPath))) {

            String line;

            while ((line = fileIn.readLine())!= null) {
                if(parseLine(line, result) == -1) {
                    System.err.println("file di configurazione non valido");
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


    public static String getStringParameter(HashMap<String,String> result, String name) {

        String parameter = result.get(name);

        if(parameter == null) {
            System.err.println("parsing \""+name+"\" fallito");
            System.exit(-1);
        }

        return parameter;
    }

    public static int getPortParameter(HashMap<String,String> result, String portName) {

        String port = result.get(portName);
        int portnum = 0;

        try {
            if (port != null) {

                portnum = Integer.parseInt(port);

                if (portnum < 1024 || portnum > 65535) {
                    System.err.println("porta \"" + portName + "\"non valida");
                    System.exit(-1);
                }
            } else throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("parsing \""+portName+"\" fallito");
            System.exit(-1);
        }

        return portnum;
    }


    public static int getPositiveIntegerParameter(HashMap<String,String> result, String name) {

        String parameter = result.get(name);
        try {
            if (parameter == null || Integer.parseInt(parameter) <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            System.err.println("parsing \""+name+"\" fallito");
            System.exit(-1);
        }

        return Integer.parseInt(parameter) ;
    }

    public static double getDoubleParameter(HashMap<String,String> result, String name) {

        String parameter = result.get(name);
        try {
            if (parameter != null) {
                double d = Double.parseDouble(parameter);
                return d;
            } else
                throw new NumberFormatException();

        } catch (NumberFormatException e) {
            System.err.println("parsing \""+name+"\" fallito");
            System.exit(-1);
        }

        return -1;
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
