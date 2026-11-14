package com.duperknight.client.gui.widgets;

import com.duperknight.DMLS;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Opens the operating system's own file chooser for PNG images.
 * <p>
 * Minecraft's Java runs in headless mode, so Swing/AWT dialogs cannot open. Instead this starts a tiny
 * helper process that shows a native dialog: PowerShell on Windows, osascript on macOS and zenity on Linux.
 * The dialog runs on its own thread so the game keeps running, and the result is delivered on the game thread.
 */
public final class MiniMeHudFilePicker {
    private static final AtomicBoolean OPEN = new AtomicBoolean();

    private MiniMeHudFilePicker() {
    }

    /**
     * Shows the dialog. {@code onPicked} is called on the game thread with the chosen files in natural
     * filename order (frame_2 before frame_10), or an empty list if the dialog was cancelled or failed.
     * Ignored if a dialog is already open.
     */
    public static void pickPngs(String title, boolean allowMultiple, Consumer<List<Path>> onPicked) {
        if (!OPEN.compareAndSet(false, true)) return;

        Thread thread = new Thread(() -> {
            List<Path> files = List.of();
            try {
                files = runDialog(title, allowMultiple);
            } catch (Throwable error) {
                // Throwable on purpose: a missing helper program should not crash the game.
                DMLS.LOGGER.warn("Could not open the file picker", error);
            } finally {
                OPEN.set(false);
            }
            List<Path> result = files;
            Minecraft.getInstance().execute(() -> onPicked.accept(result));
        }, "MiniMe HUD file picker");
        thread.setDaemon(true);
        thread.start();
    }

    private static List<Path> runDialog(String title, boolean multiple) throws IOException, InterruptedException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command;
        if (os.contains("win")) {
            command = windowsCommand(title, multiple);
        } else if (os.contains("mac")) {
            command = macCommand(title, multiple);
        } else {
            command = linuxCommand(title, multiple);
        }

        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        process.waitFor();

        // Windows PowerShell can prepend a byte-order mark to UTF-8 output.
        if (!output.isEmpty() && output.charAt(0) == '﻿') output = output.substring(1);

        List<Path> files = new ArrayList<>();
        for (String part : output.trim().split("\\|")) {
            String text = part.trim();
            if (text.isEmpty()) continue;
            Path path = Path.of(text);
            if (Files.isRegularFile(path)) files.add(path);
        }
        files.sort(Comparator.comparing((Path path) -> path.getFileName().toString(),
                MiniMeHudFilePicker::naturalCompare));
        return files;
    }

    private static List<String> windowsCommand(String title, boolean multiple) {
        // A hidden, always-on-top owner form keeps the dialog from opening behind the game window.
        String script = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
                + "Add-Type -AssemblyName System.Windows.Forms; "
                + "$owner = New-Object System.Windows.Forms.Form -Property @{TopMost = $true}; "
                + "$dialog = New-Object System.Windows.Forms.OpenFileDialog; "
                + "$dialog.Filter = 'PNG images (*.png)|*.png'; "
                + "$dialog.Multiselect = " + (multiple ? "$true" : "$false") + "; "
                + "$dialog.Title = '" + title.replace("'", "''") + "'; "
                + "if ($dialog.ShowDialog($owner) -eq 'OK') { $dialog.FileNames -join '|' }; "
                + "$owner.Dispose()";
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-STA",
                "-WindowStyle", "Hidden", "-Command", script);
    }

    private static List<String> macCommand(String title, boolean multiple) {
        String prompt = title.replace("\\", "\\\\").replace("\"", "\\\"");
        if (multiple) {
            return List.of("osascript",
                    "-e", "set chosen to choose file with prompt \"" + prompt
                            + "\" of type {\"public.png\"} with multiple selections allowed",
                    "-e", "set out to \"\"",
                    "-e", "repeat with f in chosen",
                    "-e", "set out to out & POSIX path of f & \"|\"",
                    "-e", "end repeat",
                    "-e", "return out");
        }
        return List.of("osascript",
                "-e", "set f to choose file with prompt \"" + prompt + "\" of type {\"public.png\"}",
                "-e", "return POSIX path of f");
    }

    private static List<String> linuxCommand(String title, boolean multiple) {
        List<String> command = new ArrayList<>(List.of("zenity", "--file-selection",
                "--title=" + title, "--file-filter=PNG images | *.png"));
        if (multiple) {
            command.add("--multiple");
            command.add("--separator=|");
        }
        return command;
    }

    private static int naturalCompare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String numberA = a.substring(startA, i).replaceFirst("^0+(?=.)", "");
                String numberB = b.substring(startB, j).replaceFirst("^0+(?=.)", "");
                if (numberA.length() != numberB.length()) {
                    return Integer.compare(numberA.length(), numberB.length());
                }
                int comparison = numberA.compareTo(numberB);
                if (comparison != 0) return comparison;
            } else {
                int comparison = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (comparison != 0) return comparison;
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}