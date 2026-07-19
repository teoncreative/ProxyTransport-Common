/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.util;

import java.io.InputStream;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.nethergames.proxytransport.common.transport.TransportLogger;

/**
 * Adds embedded QUIC jars to netty-common's classloader at runtime.
 * <p>
 * Netty loads the QUIC native through {@code netty-common} and resolves the native's JNI classes via the
 * classloader that loaded {@code netty-common}. Shading QUIC into a plugin's child classloader therefore fails
 * the native load, so the jars are injected onto that classloader instead; everything else reaches them by
 * normal parent delegation.
 * <p>
 * The reflective injection needs an {@code --add-opens} on Java 17+: {@code java.base/jdk.internal.loader} for
 * the system classloader, or {@code java.base/java.net} for a URLClassLoader.
 * <p>
 * Hosts differ in which QUIC flavour they use, so the probe class and jar resources are supplied by the caller.
 */
public final class QuicLibraryInstaller {

    private static final String[] NETTY_COMMON_PROBES = {
        "io.netty.util.internal.NativeLibraryLoader",
        "io.netty.util.internal.NativeLibraryUtil",
        "io.netty.util.internal.PlatformDependent",
    };

    private QuicLibraryInstaller() {
    }

    public static final class InstallException extends Exception {
        public InstallException(String message) {
            super(message);
        }

        public InstallException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Ensures the QUIC libraries are present on netty-common's classloader. Call before referencing any QUIC
     * class.
     *
     * @param probeClass    a QUIC class used to detect whether the flavour is already loadable
     * @param embeddedJars  classpath resources to inject, in load order
     * @param stagingDir    directory the jars are extracted to
     */
    public static synchronized void install(String probeClass, String[] embeddedJars, Path stagingDir)
        throws InstallException {
        ClassLoader nettyClassLoader = nettyCommonClassLoader();
        if (isAvailable(probeClass, nettyClassLoader)) {
            return;
        }

        try {
            Files.createDirectories(stagingDir);
        } catch (Exception e) {
            throw new InstallException("Failed to create staging directory " + stagingDir, e);
        }

        for (String resource : embeddedJars) {
            appendToClassLoader(nettyClassLoader, extract(resource, stagingDir));
        }

        if (!isAvailable(probeClass, nettyClassLoader)) {
            throw new InstallException("QUIC classes not visible after injection (missing --add-opens flag?).");
        }
    }

    /** Installs QUIC, logging rather than throwing so it never aborts startup. */
    public static boolean tryInstall(String probeClass, String[] embeddedJars, Path stagingDir,
                                     TransportLogger logger) {
        try {
            install(probeClass, embeddedJars, stagingDir);
            return true;
        } catch (InstallException e) {
            logger.error("Could not load the QUIC native; QUIC is unavailable (TCP unaffected). "
                + e.getMessage(), e);
            return false;
        }
    }

    private static void appendToClassLoader(ClassLoader loader, Path jar) throws InstallException {
        // A URLClassLoader takes addURL; the system classloader takes appendClassPath. Each needs a different
        // --add-opens to reflect into.
        if (loader instanceof URLClassLoader urlClassLoader) {
            Method addUrl = accessible(() -> URLClassLoader.class.getDeclaredMethod("addURL", URL.class),
                "java.base/java.net");
            invoke(() -> addUrl.invoke(urlClassLoader, jar.toUri().toURL()), jar, loader);
            return;
        }

        Method append = findAppendMethod(loader.getClass());
        if (append == null) {
            throw new InstallException("Classloader " + loader.getClass().getName() +
                " is not a URLClassLoader and has no classpath-append method.");
        }
        try {
            append.setAccessible(true);
        } catch (InaccessibleObjectException e) {
            throw missingOpens("java.base/jdk.internal.loader", e);
        }
        invoke(() -> append.invoke(loader, jar.toAbsolutePath().toString()), jar, loader);
    }

    private interface MethodSupplier {
        Method get() throws NoSuchMethodException;
    }

    private interface Invocation {
        void run() throws Exception;
    }

    private static Method accessible(MethodSupplier supplier, String module) throws InstallException {
        try {
            Method method = supplier.get();
            method.setAccessible(true);
            return method;
        } catch (InaccessibleObjectException e) {
            throw missingOpens(module, e);
        } catch (NoSuchMethodException e) {
            throw new InstallException("Expected method not found on this JVM.", e);
        }
    }

    private static void invoke(Invocation invocation, Path jar, ClassLoader loader) throws InstallException {
        try {
            invocation.run();
        } catch (Exception e) {
            throw new InstallException("Failed to add " + jar + " to " + loader, e);
        }
    }

    private static InstallException missingOpens(String module, Throwable cause) {
        return new InstallException("Add this JVM flag and restart: --add-opens " + module + "=ALL-UNNAMED", cause);
    }

    private static Method findAppendMethod(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String name : new String[]{"appendToClassPathForInstrumentation", "appendClassPath"}) {
                try {
                    return current.getDeclaredMethod(name, String.class);
                } catch (NoSuchMethodException ignored) {
                    // try the next candidate
                }
            }
        }
        return null;
    }

    private static ClassLoader nettyCommonClassLoader() throws InstallException {
        for (String probe : NETTY_COMMON_PROBES) {
            try {
                ClassLoader loader = Class.forName(probe, false, QuicLibraryInstaller.class.getClassLoader())
                    .getClassLoader();
                if (loader != null) {
                    return loader;
                }
            } catch (Throwable ignored) {
                // try the next probe
            }
        }
        throw new InstallException("Could not locate netty-common's classloader.");
    }

    private static boolean isAvailable(String probeClass, ClassLoader classLoader) {
        try {
            Class.forName(probeClass, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static Path extract(String resource, Path dir) throws InstallException {
        Path target = dir.resolve(resource.substring(resource.lastIndexOf('/') + 1));
        try (InputStream in = QuicLibraryInstaller.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new InstallException("Embedded library " + resource + " is missing from the plugin jar.");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InstallException e) {
            throw e;
        } catch (Exception e) {
            throw new InstallException("Failed to extract " + resource, e);
        }
        return target;
    }
}