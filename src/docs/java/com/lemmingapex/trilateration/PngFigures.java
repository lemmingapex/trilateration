package com.lemmingapex.trilateration;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.w3c.dom.Node;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;

/**
 * PNG copies of the README figures, for sharing where SVG is not accepted. Each is rendered at
 * twice the SVG's size in the light theme; an animation is rendered as its final frame.
 *
 * <p>PNG rendering depends on the fonts installed, so the same SVG gives slightly different PNG
 * bytes on different machines. To keep {@code renderDocs} reproducible anyway, each PNG records
 * a hash of the SVG it was rendered from, and is rendered again only when that hash changes. CI
 * then fails if a PNG is missing or was rendered from an older SVG, exactly as for the SVGs.
 */
final class PngFigures {

    /** Pixels per SVG unit. */
    static final int SCALE = 2;

    /**
     * Part of the recorded hash: change it to render every PNG again, for example after changing
     * how they are drawn.
     */
    private static final String RENDERING = "jsvg, scale 2, light theme, final frame; 1";

    /** The PNG text chunk keyword under which the hash is recorded. */
    private static final String KEYWORD = "Source-SVG-SHA-256";

    private static final String PNG_FORMAT = "javax_imageio_png_1.0";

    private PngFigures() {
    }

    /**
     * Renders {@code svg} to {@code png}, unless {@code png} was already rendered from it.
     *
     * @return {@code true} if the PNG was written
     * @throws IOException if the PNG cannot be read or written
     */
    static boolean update(Path png, String svg) throws IOException {
        String hash = hash(svg);
        if (Files.exists(png) && hash.equals(recordedHash(png))) {
            return false;
        }
        Files.write(png, encode(render(svg), hash));
        return true;
    }

    static BufferedImage render(String svg) {
        SVGDocument document = new SVGLoader().load(
                new ByteArrayInputStream(forRaster(svg).getBytes(StandardCharsets.UTF_8)), null, LoaderContext.createDefault());
        if (document == null) {
            throw new IllegalStateException("JSVG could not parse the figure.");
        }
        int width = (int) Math.ceil(document.size().width * SCALE);
        int height = (int) Math.ceil(document.size().height * SCALE);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.scale(SCALE, SCALE);
            document.render(null, g);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Rewrites a figure into the subset of SVG and CSS that JSVG draws correctly:
     * <ul>
     *   <li>the colour variables are replaced by their light-theme values, and the dark-theme
     *       media query is dropped;</li>
     *   <li>SMIL animations are removed, which leaves an animation's final frame, because its
     *       static attributes hold that frame;</li>
     *   <li>the {@code font} shorthand is expanded, with a generic sans-serif family;</li>
     *   <li>an element with several classes gets their rules as an inline style, because JSVG
     *       applies class rules only to elements with a single class.</li>
     * </ul>
     */
    static String forRaster(String svg) {
        Matcher root = Pattern.compile("svg\\{([^}]*)}").matcher(svg);
        if (!root.find()) {
            throw new IllegalStateException("The figure has no colour variables.");
        }
        Map<String, String> variables = new HashMap<>();
        for (String declaration : root.group(1).split(";")) {
            int colon = declaration.indexOf(':');
            variables.put(declaration.substring(0, colon).trim(), declaration.substring(colon + 1).trim());
        }
        String s = svg.replaceFirst("@media \\(prefers-color-scheme:dark\\)\\{svg\\{[^}]*}}", "");
        s = replace(s, Pattern.compile("var\\((--[a-z0-9]+)\\)"), m -> {
            String value = variables.get(m.group(1));
            if (value == null) {
                throw new IllegalStateException("Undefined colour variable " + m.group(1));
            }
            return value;
        });
        s = s.replaceAll("<animate(Transform)? [^>]*/>", "");
        s = replace(s, Pattern.compile("font:(?:(\\d+) )?(\\d+(?:\\.\\d+)?)px [^;}]*"), m ->
                "font-weight:" + (m.group(1) == null ? "normal" : m.group(1)) + ";font-size:" + m.group(2)
                        + "px;font-family:sans-serif");

        Map<String, String> rules = new HashMap<>();
        Matcher style = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL).matcher(s);
        if (style.find()) {
            Matcher rule = Pattern.compile("([^{}]+)\\{([^{}]*)}").matcher(style.group(1));
            while (rule.find()) {
                for (String selector : rule.group(1).split(",")) {
                    if (selector.trim().matches("\\.[a-z-]+")) {
                        rules.merge(selector.trim().substring(1), rule.group(2), (a, b) -> a + ";" + b);
                    }
                }
            }
        }
        return replace(s, Pattern.compile("class=\"([a-z-]+(?: [a-z-]+)+)\""), m -> {
            StringBuilder inline = new StringBuilder();
            for (String name : m.group(1).split(" ")) {
                String declarations = rules.get(name);
                if (declarations != null) {
                    inline.append(inline.length() == 0 ? "" : ";").append(declarations);
                }
            }
            return m.group() + " style=\"" + inline + "\"";
        });
    }

    private static String replace(String text, Pattern pattern, Function<Matcher, String> replacement) {
        Matcher m = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(replacement.apply(m)));
        }
        return m.appendTail(out).toString();
    }

    static String hash(String svg) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((RENDERING + "\n" + svg).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** PNG bytes with the source hash in a text chunk. */
    static byte[] encode(BufferedImage image, String hash) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
            IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
            entry.setAttribute("keyword", KEYWORD);
            entry.setAttribute("value", hash);
            IIOMetadataNode text = new IIOMetadataNode("tEXt");
            text.appendChild(entry);
            IIOMetadataNode tree = new IIOMetadataNode(PNG_FORMAT);
            tree.appendChild(text);
            metadata.mergeTree(PNG_FORMAT, tree);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(image, null, metadata), param);
            }
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /** The source hash recorded in a PNG, or {@code null} if it has none or cannot be read. */
    static String recordedHash(Path png) throws IOException {
        try (InputStream file = Files.newInputStream(png); ImageInputStream in = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                Node tree = reader.getImageMetadata(0).getAsTree(PNG_FORMAT);
                for (Node chunk = tree.getFirstChild(); chunk != null; chunk = chunk.getNextSibling()) {
                    if (!chunk.getNodeName().equals("tEXt")) {
                        continue;
                    }
                    for (Node e = chunk.getFirstChild(); e != null; e = e.getNextSibling()) {
                        Node keyword = e.getAttributes().getNamedItem("keyword");
                        if (keyword != null && KEYWORD.equals(keyword.getNodeValue())) {
                            return e.getAttributes().getNamedItem("value").getNodeValue();
                        }
                    }
                }
                return null;
            } finally {
                reader.dispose();
            }
        }
    }
}
