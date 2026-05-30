package com.garden_bot.downloeader;

import com.garden_bot.util.FileHelper;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Clase principal encargada de descargar contenido multimedia de URLs externas
 * y enviarlo al canal de Discord donde fue solicitado.
 * Utiliza yt-dlp para descarga de videos y gallery-dl como alternativa para imágenes.
 * Soporta autenticación con cookies por plataforma para contenido restringido
 * y reintenta automáticamente con cookies cuando el acceso anónimo es denegado.
 */
public class MediaDownloader {

    private static final Logger log = LoggerFactory.getLogger(MediaDownloader.class);

    private final String downloadPath;
    private final int maxSizeMb;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public MediaDownloader(Dotenv env) {
        this.downloadPath = env.get("DOWNLOAD_PATH", "downloads/");
        this.maxSizeMb = Integer.parseInt(env.get("MAX_FILE_SIZE_MB", "8"));
        ensureDownloadFolder();
    }


    // Crea la carpeta de descargas si no existe al iniciar
    private void ensureDownloadFolder() {
        File folder = new File(downloadPath);
        if (!folder.exists()) {
            folder.mkdirs();
            log.info("Carpeta de descargas creada: {}", downloadPath);
        }
    }

    // Procesa una descarga iniciada desde el slash command /descarga
    public void downloadFromSlash(String url, SlashCommandInteractionEvent event) {
        executor.submit(() -> {
            try {
                log.info("Iniciando descarga: {}", url);

                List<File> downloaded = attemptDownload(url);

                if (downloaded.isEmpty()) {
                    event.getHook().sendMessage("No se encontró contenido multimedia en ese enlace.").queue();
                    return;
                }

                sendFiles(downloaded, event);

            } catch (Exception e) {
                log.error("-! Error al descargar {}: {}", url, e.getMessage());
                event.getHook().sendMessage("No pude descargar el contenido.").queue();
            }
        });
    }

    /** Inicio de descarga */

    // Intenta descargar con yt-dlp primero, usa gallery-dl como fallback
    private List<File> attemptDownload(String url) throws IOException, InterruptedException {
        Optional<String> cookies = getCookiesFile(url);
        Optional<String> initialCookies = requiresCookiesByDefault(url) && cookies.isPresent()
                ? cookies
                : Optional.empty();

        try {
            List<File> files = runWithRetry("yt-dlp", url, initialCookies, this::buildYtDlpCommand);
            if (!files.isEmpty()) return files;
        } catch (Exception e) {
            log.info("yt-dlp no pudo descargar, intentando con gallery-dl: {}", e.getMessage());
        }

        return runWithRetry("gallery-dl", url, initialCookies, this::buildGalleryDlCommand);
    }

    /** Lógica común de reintentos */

    @FunctionalInterface
    private interface CommandBuilder {
        List<String> build(String url, Optional<String> cookies);
    }


    //Ejecuta el comando dado y si falla por requerir login, reintenta automáticamente con las cookies de la plataforma
    private List<File> runWithRetry(String tool, String url, Optional<String> initialCookies,
                                    CommandBuilder commandBuilder) throws IOException, InterruptedException {
        try {
            return executeProcess(tool, commandBuilder.build(url, initialCookies));
        } catch (RuntimeException e) {
            if (requiresLogin(e.getMessage().toLowerCase()) && initialCookies.isEmpty()) {
                log.info("{}: contenido requiere login, reintentando con cookies...", tool);
                Optional<String> cookies = getCookiesFile(url);
                if (cookies.isEmpty()) {
                    throw new RuntimeException("El contenido requiere login pero no hay cookies configuradas.");
                }
                return executeProcess(tool, commandBuilder.build(url, cookies));
            }
            throw e;
        }
    }

    /** Construcción de comandos */

    // Construye el comando de yt-dlp con las opciones necesarias para video
    private List<String> buildYtDlpCommand(String url, Optional<String> cookiesFile) {
        List<String> command = new ArrayList<>(List.of(
                "bin/yt-dlp.exe",
                "--ffmpeg-location", "bin/ffmpeg.exe",
                "--no-playlist",
                "--no-check-formats",
                "--merge-output-format", "mp4",
                "-o", downloadPath + "%(id)s.%(ext)s"
        ));

        cookiesFile.ifPresentOrElse(
                cookies -> {
                    command.add("--cookies");
                    command.add(cookies);
                    log.info("[yt-dlp] Usando cookies: {}", cookies);
                },
                () -> log.info("[yt-dlp] Intentando sin cookies...")
        );

        command.add(url);
        return command;
    }

    // Construye el comando de gallery-dl con las opciones necesarias para imágenes
    private List<String> buildGalleryDlCommand(String url, Optional<String> cookiesFile) {
        List<String> command = new ArrayList<>(List.of(
                "bin/gallery-dl.exe",
                "--config", "gallery-dl.conf",
                "--no-mtime"
        ));

        cookiesFile.ifPresentOrElse(
                cookies -> {
                    command.add("--cookies");
                    command.add(cookies);
                    log.info("[gallery-dl] Usando cookies: {}", cookies);
                },
                () -> log.info("[gallery-dl] Intentando sin cookies...")
        );

        command.add(url);
        return command;
    }

    /** Ejecución de proceso externo */

    // Ejecuta un proceso externo, loguea su salida y retorna los archivos descargados
    private List<File> executeProcess(String tool, List<String> command)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[{}] {}", tool, line);
                output.append(line.toLowerCase()).append(" ");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(tool + " error: " + output);
        }

        return collectDownloadedFiles();
    }


    /** Archivos*/

    // Recoge los archivos multimedia descargados y elimina cualquier archivo no multimedia
    private List<File> collectDownloadedFiles() {
        File folder = new File(downloadPath);

        File[] nonMedia = folder.listFiles(f -> f.isFile() && !isMediaFile(f.getName()));
        if (nonMedia != null) {
            for (File f : nonMedia) {
                log.debug("Eliminando archivo no multimedia: {}", f.getName());
                f.delete();
            }
        }

        File[] files = folder.listFiles(f -> f.isFile() && isMediaFile(f.getName()));

        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        return List.of(files);
    }

    // Verifica si un archivo tiene una extensión multimedia soportada
    private boolean isMediaFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".mp4") ||
                lower.endsWith(".mov") ||
                lower.endsWith(".webm") ||
                lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".png") ||
                lower.endsWith(".gif") ||
                lower.endsWith(".webp") ||
                lower.endsWith(".jfif");
    }


    /** Envío a Discord */


    // Envía los archivos descargados al canal de Discord verificando el límite de tamaño
    private void sendFiles(List<File> files, SlashCommandInteractionEvent event) {
        List<FileUpload> uploads = new ArrayList<>();

        for (File file : files) {
            if (FileHelper.exceedsDiscordLimit(file, maxSizeMb)) {
                log.warn("Archivo demasiado grande, omitiendo: {}", file.getName());
                event.getHook().sendMessage("- " + file.getName() + "` supera el límite de " + maxSizeMb + "MB de Discord.").queue();
                continue;
            }
            uploads.add(FileUpload.fromData(file));
        }

        if (uploads.isEmpty()) {
            log.info("No hay archivos dentro del límite, limpiando temporales...");
            FileHelper.deleteAll(files.toArray(new File[0]));
            return;
        }

        event.getChannel().sendFiles(uploads)
                .queue(
                        success -> {
                            event.getHook().deleteOriginal().queue();
                            uploads.forEach(upload -> {
                                try { upload.close(); } catch (Exception ignored) {}
                            });
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            log.info("Eliminando archivos temporales...");
                            FileHelper.deleteAll(files.toArray(new File[0]));
                        },
                        error -> {
                            log.error("Error al enviar archivos a Discord: {}", error.getMessage());
                            uploads.forEach(upload -> {
                                try { upload.close(); } catch (Exception ignored) {}
                            });
                            FileHelper.deleteAll(files.toArray(new File[0]));
                        }
                );
    }


    /** Cookies y plataformas */

    // Retorna el archivo de cookies correspondiente a la plataforma de la URL
    private Optional<String> getCookiesFile(String url) {
        if (url.contains("twitter.com") || url.contains("x.com")) {
            return cookiesFileIfExists("cookies-twitter.txt");
        } else if (url.contains("instagram.com")) {
            return cookiesFileIfExists("cookies-instagram.txt");
        } else if (url.contains("pinterest.com")) {
            return cookiesFileIfExists("cookies-pinterest.txt");
        }
        return Optional.empty();
    }

    // Verifica si el archivo de cookies existe y no está vacío
    private Optional<String> cookiesFileIfExists(String filename) {
        File file = new File(filename);
        if (file.exists() && file.length() > 0) {
            log.debug("Cookies encontradas: {}", filename);
            return Optional.of(filename);
        }
        log.debug("Sin cookies para {}, modo anónimo", filename);
        return Optional.empty();
    }

    // Indica si la plataforma requiere autenticación desde el primer intento
    private boolean requiresCookiesByDefault(String url) {
        return url.contains("instagram.com");
    }

    // Detecta si un mensaje de error indica que el contenido requiere autenticación
    private boolean requiresLogin(String errorMessage) {
        return errorMessage.contains("login")
                || errorMessage.contains("authentication")
                || errorMessage.contains("private")
                || errorMessage.contains("age-restricted")
                || errorMessage.contains("sensitive")
                || errorMessage.contains("not available")
                || errorMessage.contains("unauthorized")
                || errorMessage.contains("403")
                || errorMessage.contains("unavailable")
                || errorMessage.contains("keyerror");
    }
}