package com.garden_bot.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Clase utilitaria para operaciones de gestión de archivos.
 * Maneja la eliminación de archivos temporales descargados y valida
 * si un archivo supera el límite de tamaño de subida de Discord.
 */
public class FileHelper {

    private static final Logger log = LoggerFactory.getLogger(FileHelper.class);

    //Eliminador de archivo local
    public static void delete(File file) {
        if (file != null && file.exists()) {
            if (file.delete()) {
                log.info("Archivo eliminado: {}", file.getName());
            } else {
                log.warn("No se pudo eliminar el archivo: {}", file.getName());
            }
        }
    }

    //Eliminador de todos los archivos
    public static void deleteAll(File[] files) {
        if (files == null) return;
        for (File file : files) {
            delete(file);
        }
    }

    //Evaluador de exceso de tamaño
    public static boolean exceedsDiscordLimit(File file, int maxSizeMb) {
        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        return file.length() > maxBytes;
    }
}
