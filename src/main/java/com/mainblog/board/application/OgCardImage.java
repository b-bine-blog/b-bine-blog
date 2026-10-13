package com.mainblog.board.application;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 링크 미리보기 그림: 글 상세 화면을 줄여 놓은 모양의 1200x630 PNG (D-110).
 * 글꼴은 서버에 한글 글꼴이 없어도 되도록 jar 안의 Noto Sans KR(OFL)을 쓴다.
 */
@Component
public class OgCardImage {

    static final int WIDTH = 1200;
    static final int HEIGHT = 630;

    private static final Color BG = new Color(0xf1faf4);
    private static final Color HEADER = new Color(0x145a37);
    private static final Color ACCENT = new Color(0x2fa86b);
    private static final Color BORDER = new Color(0xdff3e7);
    private static final Color TITLE = new Color(0x0f3d26);
    private static final Color META = new Color(0x4b5563);
    private static final Color BODY = new Color(0x374151);

    private volatile Font bold;
    private volatile Font regular;

    /** subtitle: "블로그 이름 · 닉네임"처럼 제목 아래 한 줄. */
    public byte[] render(String siteName, String title, String subtitle, String summary) {
        Font b = bold();
        Font r = regular();
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            g.setColor(BG);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(HEADER);
            g.fillRect(0, 0, WIDTH, 96);
            g.setColor(ACCENT);
            g.fillRect(0, 96, WIDTH, 4);
            g.setColor(Color.WHITE);
            g.setFont(b.deriveFont(38f));
            g.drawString(siteName, 80, 62);

            int cx = 80;
            int cy = 140;
            int cw = WIDTH - 160;
            int ch = HEIGHT - cy - 50;
            RoundRectangle2D card = new RoundRectangle2D.Float(cx, cy, cw, ch, 28, 28);
            g.setColor(Color.WHITE);
            g.fill(card);
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(2f));
            g.draw(card);
            g.setColor(ACCENT);
            g.fillRoundRect(cx, cy, cw, 10, 10, 10);

            int x = cx + 56;
            int maxWidth = cw - 112;
            int y = cy + 104;
            g.setColor(TITLE);
            g.setFont(b.deriveFont(60f));
            for (String line : wrap(g.getFontMetrics(), title, maxWidth, 2)) {
                g.drawString(line, x, y);
                y += 76;
            }
            y += 4;
            g.setColor(META);
            g.setFont(r.deriveFont(30f));
            for (String line : wrap(g.getFontMetrics(), subtitle, maxWidth, 1)) {
                g.drawString(line, x, y);
                y += 40;
            }
            g.setColor(BORDER);
            g.fillRect(x, y - 8, maxWidth, 2);
            y += 46;
            g.setColor(BODY);
            g.setFont(r.deriveFont(30f));
            int bodyLines = Math.max(0, (cy + ch - 36 - y) / 44 + 1);
            for (String line : wrap(g.getFontMetrics(), summary, maxWidth, bodyLines)) {
                g.drawString(line, x, y);
                y += 44;
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** 글자 단위로 줄을 나누고, 줄 수를 넘으면 마지막 줄 끝을 "…"로 줄인다. */
    static List<String> wrap(FontMetrics fm, String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxLines <= 0) {
            return lines;
        }
        String rest = text.strip().replaceAll("\\s+", " ");
        while (!rest.isEmpty() && lines.size() < maxLines) {
            int end = fit(fm, rest, maxWidth);
            if (end < rest.length()) {
                int space = rest.lastIndexOf(' ', end);
                if (space > end / 2) {
                    end = space;
                }
            }
            String line = rest.substring(0, end).stripTrailing();
            rest = rest.substring(end).stripLeading();
            if (!rest.isEmpty() && lines.size() == maxLines - 1) {
                line = ellipsize(fm, line, maxWidth);
            }
            lines.add(line);
        }
        return lines;
    }

    private static int fit(FontMetrics fm, String s, int maxWidth) {
        int i = 0;
        while (i < s.length()) {
            int next = s.offsetByCodePoints(i, 1);
            if (fm.stringWidth(s.substring(0, next)) > maxWidth) {
                break;
            }
            i = next;
        }
        return Math.max(i, Math.min(s.length(), s.offsetByCodePoints(0, 1)));
    }

    private static String ellipsize(FontMetrics fm, String line, int maxWidth) {
        String s = line;
        while (!s.isEmpty() && fm.stringWidth(s + "…") > maxWidth) {
            s = s.substring(0, s.offsetByCodePoints(s.length(), -1));
        }
        return s.stripTrailing() + "…";
    }

    private Font bold() {
        if (bold == null) {
            bold = load("fonts/NotoSansKR-Bold.otf");
        }
        return bold;
    }

    private Font regular() {
        if (regular == null) {
            regular = load("fonts/NotoSansKR-Regular.otf");
        }
        return regular;
    }

    private static Font load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (FontFormatException e) {
            throw new IllegalStateException(path, e);
        }
    }
}
