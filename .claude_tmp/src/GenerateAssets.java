import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;

public class GenerateAssets {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: GenerateAssets <iconSrc> <bgSrc> <resDir>");
        }

        File iconSrc = new File(args[0]);
        File bgSrc = new File(args[1]);
        File resDir = new File(args[2]);

        BufferedImage icon = ImageIO.read(iconSrc);
        if (icon == null) {
            throw new IllegalStateException("Failed to read icon source: " + iconSrc);
        }
        BufferedImage bg = ImageIO.read(bgSrc);
        if (bg == null) {
            throw new IllegalStateException("Failed to read background source: " + bgSrc);
        }

        int[][] launcherSizes = {
                {48, 48},
                {72, 72},
                {96, 96},
                {144, 144},
                {192, 192}
        };
        String[] mipmaps = {
                "mipmap-mdpi",
                "mipmap-hdpi",
                "mipmap-xhdpi",
                "mipmap-xxhdpi",
                "mipmap-xxxhdpi"
        };

        for (int i = 0; i < launcherSizes.length; i++) {
            int w = launcherSizes[i][0];
            int h = launcherSizes[i][1];
            BufferedImage square = centerCrop(icon, w, h, false);
            BufferedImage round = centerCrop(icon, w, h, true);
            write(square, new File(resDir, mipmaps[i] + "/ic_launcher.png"));
            write(round, new File(resDir, mipmaps[i] + "/ic_launcher_round.png"));
        }

        BufferedImage fg = centerCrop(icon, 432, 432, false);
        write(fg, new File(resDir, "drawable/ic_launcher_foreground.png"));
        write(bg, new File(resDir, "drawable/bookshelf_background.jpg"));
    }

    private static BufferedImage centerCrop(BufferedImage src, int targetW, int targetH, boolean circleMask) {
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        applyQuality(g);
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, targetW, targetH);
        g.setComposite(AlphaComposite.SrcOver);
        if (circleMask) {
            g.setClip(new Ellipse2D.Float(0, 0, targetW, targetH));
        }

        double scale = Math.max((double) targetW / src.getWidth(), (double) targetH / src.getHeight());
        int drawW = (int) Math.round(src.getWidth() * scale);
        int drawH = (int) Math.round(src.getHeight() * scale);
        int dx = (targetW - drawW) / 2;
        int dy = (targetH - drawH) / 2;
        g.drawImage(src, dx, dy, drawW, drawH, null);
        g.dispose();
        return out;
    }

    private static void write(BufferedImage image, File file) throws Exception {
        file.getParentFile().mkdirs();
        String format = file.getName().toLowerCase().endsWith(".jpg") ? "jpg" : "png";
        BufferedImage output = image;
        if ("jpg".equals(format) && image.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            applyQuality(g);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
            output = rgb;
        }
        ImageIO.write(output, format, file);
    }

    private static void applyQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }
}
