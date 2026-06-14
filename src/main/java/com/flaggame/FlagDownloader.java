package com.flaggame;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

final class FlagDownloader {

    private final Path imagesDirectory;
    private final String urlTemplate;
    private final int width;
    private final int height;

    FlagDownloader(Path imagesDirectory, String urlTemplate, int width, int height) {
        this.imagesDirectory = imagesDirectory;
        this.urlTemplate = urlTemplate;
        this.width = width;
        this.height = height;
    }

    DownloadedFlag load(Country country) throws IOException {
        Files.createDirectories(imagesDirectory);
        String fileName = "flaggame-" + country.code().toLowerCase() + ".png";
        Path target = imagesDirectory.resolve(fileName);

        BufferedImage source = Files.isRegularFile(target) ? ImageIO.read(target.toFile()) : null;
        if (source == null) {
            download(country, target);
            source = ImageIO.read(target.toFile());
        }
        if (source == null) {
            throw new IOException("Downloaded flag is not a readable PNG: " + target);
        }

        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return new DownloadedFlag(fileName, resized);
    }

    private void download(Country country, Path target) throws IOException {
        URI uri = URI.create(String.format(urlTemplate, country.code().toLowerCase()));
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "FlagGame/1.0");

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("Flag CDN returned HTTP " + status + " for " + country.code());
        }

        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
            connection.disconnect();
        }
    }

    record DownloadedFlag(String fileName, BufferedImage image) {
    }
}
