package com.garden_bot.listeners;

import com.garden_bot.downloeader.MediaDownloader;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener de eventos que maneja las interacciones de slash commands de Discord.
 * Escucha el comando /descarga, extrae la URL proporcionada
 * e inicia el proceso de descarga por medio de MediaDownloader.
 */
public class SlashCommandListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SlashCommandListener.class);

    private final MediaDownloader downloader;

    public SlashCommandListener(MediaDownloader downloader) {
        this.downloader = downloader;
    }

    //Recibe el slash command /descarga, valida la URL y delega la descarga
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("descarga")) return;
        if (event.getGuild() == null) return;

        String url = event.getOption("url").getAsString().trim();

        log.info("Slash command /descarga recibido de {}: {}", event.getUser().getName(), url);

        event.deferReply(false).queue();

        downloader.downloadFromSlash(url, event);
    }
}