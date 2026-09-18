package dev.ivchenko.webview.gradle;

import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Renders one image - a PNG of any size is the usual input - into a Windows {@code .ico} holding it at every size
 * Explorer, the taskbar and the title bar ask for.
 *
 * <p>Entries up to 128 pixels are stored as uncompressed 32-bit bitmaps, which every Windows version reads; the 256
 * pixel entry is stored as PNG, the form Windows expects for that size.</p>
 */
public abstract class GenerateWindowsIcon extends DefaultTask {
    private static final int HEADER_SIZE = 6;
    private static final int ENTRY_SIZE = 16;
    private static final int BITMAP_INFO_HEADER_SIZE = 40;
    private static final int PNG_FROM_SIZE = 256;

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSource();

    @Input
    public abstract ListProperty<Integer> getSizes();

    @OutputFile
    public abstract RegularFileProperty getIcon();

    @TaskAction
    @SneakyThrows
    public void generate() {
        File source = this.getSource().get().getAsFile();
        BufferedImage image = ImageIO.read(source);
        if (image == null) throw new GradleException("Not an image ImageIO can read: " + source);

        List<byte[]> entries = new ArrayList<>();
        List<Integer> sizes = this.getSizes().get();
        for (int size : sizes) {
            BufferedImage scaled = scale(image, size);
            entries.add(size >= PNG_FROM_SIZE ? png(scaled) : bitmap(scaled));
        }

        ByteBuffer out = ByteBuffer.allocate(HEADER_SIZE + ENTRY_SIZE * sizes.size()
                + entries.stream().mapToInt(entry -> entry.length).sum()).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0).putShort((short) 1).putShort((short) sizes.size());
        int offset = HEADER_SIZE + ENTRY_SIZE * sizes.size();
        for (int i = 0; i < sizes.size(); i++) {
            int size = sizes.get(i);
            out.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
            out.put((byte) 0).put((byte) 0);
            out.putShort((short) 1).putShort((short) 32);
            out.putInt(entries.get(i).length).putInt(offset);
            offset += entries.get(i).length;
        }
        entries.forEach(out::put);

        File icon = this.getIcon().get().getAsFile();
        Files.createDirectories(icon.getParentFile().toPath());
        Files.write(icon.toPath(), out.array());
    }

    private static BufferedImage scale(BufferedImage image, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    @SneakyThrows
    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    /** A {@code BITMAPINFOHEADER}, bottom-up BGRA rows, then an all-zero AND mask: alpha does the masking. */
    private static byte[] bitmap(BufferedImage image) {
        int size = image.getWidth();
        int maskRow = (size + 31) / 32 * 4;
        ByteBuffer out = ByteBuffer.allocate(BITMAP_INFO_HEADER_SIZE + size * size * 4 + maskRow * size)
                .order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(BITMAP_INFO_HEADER_SIZE).putInt(size).putInt(size * 2);
        out.putShort((short) 1).putShort((short) 32);
        out.putInt(0).putInt(size * size * 4).putInt(0).putInt(0).putInt(0).putInt(0);
        for (int y = size - 1; y >= 0; y--) {
            for (int x = 0; x < size; x++) {
                int argb = image.getRGB(x, y);
                out.put((byte) argb).put((byte) (argb >> 8)).put((byte) (argb >> 16)).put((byte) (argb >>> 24));
            }
        }
        return out.array();
    }
}
