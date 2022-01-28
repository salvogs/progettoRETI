# Prerequisiti	
 Il progetto è stato compilato e testato sia su Windows che su Unix, in entrambi i casi era installata un JDK versione 1.8.0_321 (Java 8).
 Tutti i comandi seguenti devono essere eseguiti dalla directory principale del progetto.

 Attenzione: fare copia incolla sul terminale di comandi su più righe potrebbe portare a errori.

# Compilazione

Il risultato della compilazione saranno dei file .class contenuti nella directory **bin**



```
javac -d bin -cp .\lib\* .\src\com\salvo\winsome\*.java .\src\com\salvo\winsome\client\*.java .\src\com\salvo\winsome\server\*.java 
```


# Creazione archivio jar

La directory principale contiene gia' i file Server.jar e Client.jar ma nel caso si volessero creare nuovamente a seguito di modifiche al codice e' possibile lanciare i comandi: 

```
cd bin  # vanno creati dalla directory bin

jar cvfm ../Server.jar ../META-INF/smanifest.mf com/salvo/winsome/*.class com/salvo/winsome/server/*.class

jar cvfm ../Client.jar ../META-INF/cmanifest.mf com/salvo/winsome/*.class com/salvo/winsome/client/*.class

cd ..

```


# Esecuzione
Comandi per l'esecuzione del server e del client. In entrambi i casi possono essere terminati in maniera corretta con il comando 'stop'.
## Server

```
java -cp "lib\*;bin" com.salvo.winsome.server.MainServer .\serverconfig.txt
```
oppure

```
java -jar Server.jar .\serverconfig.txt
```

Oltre all'argomento ```config_path``` e' possibile passare il parametro ```n_workers``` per personalizzare il numero thread del pool delle richieste

## Client
```
java -cp ".\lib\*;.\bin" com.salvo.winsome.client.MainClient .\clientconfig.txt  
```

oppure

```
java -jar Client.jar .\clientconfig.txt  
```
