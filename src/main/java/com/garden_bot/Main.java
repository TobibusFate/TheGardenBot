package com.garden_bot;


import com.garden_bot.bot.BotManager;
import io.github.cdimascio.dotenv.Dotenv;

/**
 * Punto de entrada de la aplicación del Bot.
 * Carga las variables de entorno desde el archivo .env e inicializa el BotManager.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        Dotenv env = Dotenv.load();
        new BotManager(env).start();
    }
}