package de.tagstock.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TagStock-Server: teilt einen Bestand zwischen mehreren Geraeten, verwaltet
 * Teams und Rollen und nimmt Leih-Anfragen entgegen. Laeuft als einzelner
 * Container mit einer SQLite-Datei im Verzeichnis /data.
 */
@SpringBootApplication
public class TagStockServer {

    public static void main(String[] args) {
        SpringApplication.run(TagStockServer.class, args);
    }
}
