package com.garden_bot.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener que detecta cuando el bot es agregado a un servidor no autorizado.
 * Si el servidor no coincide con el ID permitido, el bot abandona el servidor automáticamente.
 * Si no hay servidor restringido configurado, permite todos los servidores.
 */
public class GuildJoinListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GuildJoinListener.class);

    private final String allowedGuildId;

    public GuildJoinListener(String allowedGuildId) {
        this.allowedGuildId = allowedGuildId;
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        // Si allowedGuildId está vacío permite todos los servidores
        if (allowedGuildId.isBlank()) return;

        Guild guild = event.getGuild();

        if (!guild.getId().equals(allowedGuildId)) {
            log.warn("Bot agregado a servidor no autorizado: {} ({}), abandonando...",
                    guild.getName(), guild.getId());
            guild.leave().queue(
                    success -> log.info("Bot abandonó el servidor no autorizado: {}", guild.getName()),
                    error -> log.error("Error al abandonar el servidor: {}", error.getMessage())
            );
        }
    }
}