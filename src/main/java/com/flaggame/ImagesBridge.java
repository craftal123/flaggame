package com.flaggame;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class ImagesBridge {

    private static final String OWNED_IMAGE_PREFIX = "flaggame-";

    private final Constructor<?> imageConstructor;
    private final Method getImagesDirectory;
    private final Method addImage;
    private final Method removeImage;
    private final Method getMatchingImages;
    private final Method getImageName;
    private final Method refreshImage;
    private final Method destroyImage;

    ImagesBridge() throws ReflectiveOperationException {
        Plugin imagesPlugin = Bukkit.getPluginManager().getPlugin("Images");
        if (imagesPlugin == null || !imagesPlugin.isEnabled()) {
            throw new IllegalStateException("Images is not installed or enabled");
        }

        ClassLoader loader = imagesPlugin.getClass().getClassLoader();
        Class<?> imagesClass = loader.loadClass("com.andavin.images.Images");
        Class<?> customImageClass = loader.loadClass("com.andavin.images.image.CustomImage");

        this.imageConstructor = customImageClass.getConstructor(
                String.class, Location.class, BlockFace.class, BufferedImage.class);
        this.getImagesDirectory = imagesClass.getMethod("getImagesDirectory");
        this.addImage = imagesClass.getMethod("addImage", customImageClass);
        this.removeImage = imagesClass.getMethod("removeImage", customImageClass);
        this.getMatchingImages = imagesClass.getMethod("getMatchingImages", Predicate.class);
        this.getImageName = customImageClass.getMethod("getImageName");
        this.refreshImage = customImageClass.getMethod("refresh", Player.class, Location.class);
        this.destroyImage = customImageClass.getMethod("destroy");
    }

    Path imagesDirectory() throws ReflectiveOperationException {
        return ((File) invoke(getImagesDirectory, null)).toPath();
    }

    Object replaceImage(Object previous, String imageName, Location location,
                        BlockFace facing, BufferedImage pixels) throws ReflectiveOperationException {
        Object next = imageConstructor.newInstance(imageName, location, facing, pixels);
        boolean added = (boolean) invoke(addImage, null, next);
        if (!added) {
            throw new IllegalStateException("Images refused to add the flag at " + location);
        }

        if (previous != null) {
            remove(previous);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            invoke(refreshImage, next, player, player.getLocation());
        }
        return next;
    }

    void removeImage(Object image) throws ReflectiveOperationException {
        if (image != null) {
            remove(image);
        }
    }

    @SuppressWarnings("unchecked")
    void removeStaleImages() throws ReflectiveOperationException {
        Predicate<Object> ownedByFlagGame = image -> {
            try {
                return ((String) invoke(getImageName, image)).startsWith(OWNED_IMAGE_PREFIX);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        };

        List<Object> images = (List<Object>) invoke(getMatchingImages, null, ownedByFlagGame);
        for (Object image : images) {
            remove(image);
        }
    }

    private void remove(Object image) throws ReflectiveOperationException {
        boolean removed = (boolean) invoke(removeImage, null, image);
        if (removed) {
            invoke(destroyImage, image);
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) {
                throw reflective;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        }
    }
}
