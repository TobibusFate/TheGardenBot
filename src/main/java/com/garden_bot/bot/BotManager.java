package com.garden_bot.bot;

import com.garden_bot.downloeader.MediaDownloader;
import com.garden_bot.listeners.GuildJoinListener;
import com.garden_bot.listeners.SlashCommandListener;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsable de inicializar y configurar la instancia de JDA (Java Discord API).
 * Registra los slash commands, adjunta los listeners de eventos y maneja
 * el cierre ordenado del bot cuando la aplicación es detenida.
 */
public class BotManager {

    private static final Logger log = LoggerFactory.getLogger(BotManager.class);

    private final Dotenv env;

    public BotManager(Dotenv env) {
        this.env = env;
    }

    public void start() throws InterruptedException {
        String token = env.get("DISCORD_TOKEN");
        String allowedGuildId = env.get("ALLOWED_GUILD_ID", "");

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISCORD_TOKEN no está definido en el archivo .env");
        }

        boolean restrictedMode = !allowedGuildId.isBlank();

        if (restrictedMode) {
            log.info("+! Modo restringido: solo servidor {}", allowedGuildId);
        } else {
            log.info("+! Modo abierto: todos los servidores permitidos");
        }

        MediaDownloader downloader = new MediaDownloader(env);

        JDA jda = JDABuilder
                .createDefault(token)
                .addEventListeners(
                        new SlashCommandListener(downloader),
                        new GuildJoinListener(allowedGuildId))
                .build();

        jda.awaitReady();

        log.info("+! Bot conectado como: {}", jda.getSelfUser().getName());
        log.info("+! Servidores activos: {}", jda.getGuilds().size());

        SlashCommandData descargaCommand = Commands.slash("descarga", "Descarga contenido multimedia de un enlace")
                .addOption(OptionType.STRING, "url", "El enlace a descargar", true);

        if (restrictedMode) {
            jda.getGuilds().forEach(guild -> {
                if (!guild.getId().equals(allowedGuildId)) {
                    log.warn("Servidor no autorizado detectado al arrancar: {} ({}), abandonando...",
                            guild.getName(), guild.getId());
                    guild.leave().queue(
                            success -> log.info("Bot abandonó el servidor no autorizado: {}", guild.getName()),
                            error -> log.error("Error al abandonar el servidor: {}", error.getMessage())
                    );
                }
            });

            Guild allowedGuild = jda.getGuildById(allowedGuildId);
            if (allowedGuild != null) {
                allowedGuild.updateCommands().addCommands(descargaCommand).queue();
                log.info("+! Slash commands registrados en: {}", allowedGuild.getName());
            } else {
                log.error("-! No se encontró el servidor con ID: {}", allowedGuildId);
            }
        } else {
            jda.updateCommands().addCommands(descargaCommand).queue();
            log.info("+! Slash commands registrados globalmente");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Cerrando bot...");
            jda.shutdown();
            log.info("Bot desconectado correctamente");
        }));
    }
}