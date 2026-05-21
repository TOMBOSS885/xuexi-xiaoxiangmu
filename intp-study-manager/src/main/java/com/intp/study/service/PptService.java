package com.intp.study.service;

import com.intp.study.repository.SqlRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class PptService {
    private static final Pattern UNSAFE = Pattern.compile("[^0-9A-Za-z\\u4e00-\\u9fff._-]+");

    private final SqlRepository repo;
    private final Path dataRoot;
    private final Path uploadDir;
    private final Path imageRoot;

    public PptService(SqlRepository repo, @Value("${app.data-dir:data}") String dataDir) {
        this.repo = repo;
        this.dataRoot = Path.of(dataDir).toAbsolutePath().normalize();
        this.uploadDir = dataRoot.resolve("uploads");
        this.imageRoot = dataRoot.resolve("page_images");
    }

    @Transactional
    public long importDeck(MultipartFile file, String subject, String title) {
        if (file == null || file.isEmpty()) {
            throw new PptServiceException("请先选择 PPTX 或 PDF 文件。");
        }
        try {
            Path saved = saveUpload(file);
            String suffix = suffix(saved);
            List<SlideData> slides;
            Map<Integer, Path> images;
            if (".pptx".equals(suffix)) {
                slides = extractPptx(saved);
                images = renderPptx(saved);
            } else if (".pdf".equals(suffix)) {
                slides = extractPdf(saved);
                images = renderPdf(saved);
            } else {
                throw new PptServiceException("仅支持 PPTX 或 PDF 文件。");
            }
            String deckTitle = text(title, stripExtension(saved.getFileName().toString()));
            long deckId = repo.insert("""
                    INSERT INTO ppt_decks (filename, title, subject, file_path, slide_count)
                    VALUES (?, ?, ?, ?, ?)
                    """, saved.getFileName().toString(), deckTitle, text(subject), saved.toString(), slides.size());
            for (SlideData slide : slides) {
                Path image = images.get(slide.slideNumber());
                repo.insert("""
                        INSERT INTO ppt_slides (deck_id, slide_number, title, slide_text, notes, image_path)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, deckId, slide.slideNumber(), slide.title(), slide.text(), slide.notes(), image == null ? "" : image.toString());
            }
            return deckId;
        } catch (PptServiceException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new PptServiceException("资料导入失败：" + exc.getMessage(), exc);
        }
    }

    public List<Map<String, Object>> decks() {
        return repo.query("""
                SELECT *
                FROM ppt_decks
                ORDER BY
                    CASE status
                        WHEN '使用中' THEN 0
                        WHEN '待整理' THEN 1
                        WHEN '暂停' THEN 2
                        WHEN '归档' THEN 3
                        ELSE 9
                    END,
                    category ASC,
                    sort_order ASC,
                    created_at DESC,
                    id DESC
                """);
    }

    public List<Map<String, Object>> slides(long deckId) {
        return repo.query("SELECT * FROM ppt_slides WHERE deck_id = ? ORDER BY slide_number ASC", deckId);
    }

    public List<Map<String, Object>> questions(long deckId) {
        return repo.query("""
                SELECT sq.*, ps.slide_number, ps.title AS slide_title, pd.title AS deck_title
                FROM slide_questions sq
                JOIN ppt_slides ps ON ps.id = sq.slide_id
                JOIN ppt_decks pd ON pd.id = ps.deck_id
                WHERE pd.id = ?
                ORDER BY sq.sort_order ASC, sq.created_at DESC, sq.id DESC
                """, deckId);
    }

    public Map<Long, Map<String, Object>> latestExplanations(List<Map<String, Object>> slides) {
        Map<Long, Map<String, Object>> latest = new LinkedHashMap<>();
        for (Map<String, Object> slide : slides) {
            long slideId = number(slide.get("id"));
            repo.queryOne("""
                    SELECT *
                    FROM slide_explanations
                    WHERE slide_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                    """, slideId).ifPresent(row -> latest.put(slideId, row));
        }
        return latest;
    }

    public String imageUrl(Object imagePath) {
        String value = text(imagePath);
        if (value.isBlank()) {
            return "";
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!path.startsWith(dataRoot)) {
            return "";
        }
        return "/data/" + dataRoot.relativize(path).toString().replace("\\", "/");
    }

    public void saveExplanation(long slideId, String model, String explanation) {
        repo.insert("""
                INSERT INTO slide_explanations (slide_id, model, explanation)
                VALUES (?, ?, ?)
                """, slideId, model, explanation);
    }

    public void saveQuestion(long slideId, String question, String answer, String model) {
        repo.insert("""
                INSERT INTO slide_questions (slide_id, question, answer, model)
                VALUES (?, ?, ?, ?)
                """, slideId, question, answer, model);
    }

    public void updateDeck(long deckId, String category, int sortOrder, String status) {
        repo.update("UPDATE ppt_decks SET category = ?, sort_order = ?, status = ? WHERE id = ?", category, sortOrder, status, deckId);
    }

    public void deleteDeck(long deckId) {
        repo.update("DELETE FROM ppt_decks WHERE id = ?", deckId);
    }

    public String buildSlidePrompt(Map<String, Object> deck, Map<String, Object> slide, String relatedKnowledge) {
        return """
                你是一个严谨的中文课程助教。请按“核心问题 -> 主线逻辑 -> 公式/概念 -> 易混点 -> 闭卷自测”的结构讲解这一页。

                科目：%s
                资料：%s
                页码：第 %s 页
                页面标题：%s

                页面文字：
                %s

                相关知识卡片：
                %s
                """.formatted(
                text(deck.get("subject"), "未分类"),
                text(deck.get("title")),
                text(slide.get("slide_number")),
                text(slide.get("title"), "未命名页面"),
                text(slide.get("slide_text"), "这一页没有解析到文字。"),
                text(relatedKnowledge, "暂无同科目知识卡片。")
        );
    }

    public Map<Integer, Path> renderMissingPageImages(Map<String, Object> deck, List<Map<String, Object>> slides) {
        try {
            Path path = Path.of(text(deck.get("file_path")));
            Map<Integer, Path> images = ".pdf".equals(suffix(path)) ? renderPdf(path) : renderPptx(path);
            for (Map<String, Object> slide : slides) {
                Path image = images.get((int) number(slide.get("slide_number")));
                if (image != null) {
                    repo.update("UPDATE ppt_slides SET image_path = ? WHERE id = ?", image.toString(), slide.get("id"));
                }
            }
            return images;
        } catch (Exception exc) {
            throw new PptServiceException("页面图片补渲染失败：" + exc.getMessage(), exc);
        }
    }

    public int refreshPdfSlideText(Map<String, Object> deck, List<Map<String, Object>> slides) {
        try {
            Path path = Path.of(text(deck.get("file_path")));
            if (!".pdf".equals(suffix(path))) {
                throw new PptServiceException("只有 PDF 资料需要重新提取文字。");
            }
            Map<Integer, SlideData> extracted = new LinkedHashMap<>();
            for (SlideData item : extractPdf(path)) {
                extracted.put(item.slideNumber(), item);
            }
            int updated = 0;
            for (Map<String, Object> slide : slides) {
                SlideData item = extracted.get((int) number(slide.get("slide_number")));
                if (item == null) {
                    continue;
                }
                repo.update("""
                        UPDATE ppt_slides
                        SET title = ?, slide_text = ?, notes = ?
                        WHERE id = ?
                        """, item.title(), item.text(), item.notes(), slide.get("id"));
                if (!item.text().isBlank()) {
                    updated++;
                }
            }
            return updated;
        } catch (PptServiceException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new PptServiceException("PDF 文字刷新失败：" + exc.getMessage(), exc);
        }
    }

    private Path saveUpload(MultipartFile file) throws Exception {
        Files.createDirectories(uploadDir);
        String original = file.getOriginalFilename() == null ? "uploaded" : file.getOriginalFilename();
        String suffix = suffix(Path.of(original));
        String stem = safe(stripExtension(original));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path target = uploadDir.resolve(timestamp + "_" + (stem.isBlank() ? "deck" : stem) + suffix);
        file.transferTo(target);
        return target;
    }

    private List<SlideData> extractPptx(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path); XMLSlideShow show = new XMLSlideShow(input)) {
            List<SlideData> result = new ArrayList<>();
            int index = 1;
            for (XSLFSlide slide : show.getSlides()) {
                List<String> lines = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            lines.add(text.strip());
                        }
                    }
                }
                String body = String.join("\n", lines);
                String title = lines.isEmpty() ? "未命名页面" : clip(lines.get(0), 80);
                result.add(new SlideData(index++, title, body, ""));
            }
            return result;
        }
    }

    private List<SlideData> extractPdf(Path path) throws Exception {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<SlideData> result = new ArrayList<>();
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document).strip();
                String title = firstLine(text, "PDF 第 " + i + " 页");
                result.add(new SlideData(i, clip(title, 80), text, "source=pdf"));
            }
            return result;
        }
    }

    private Map<Integer, Path> renderPdf(Path path) throws Exception {
        Path targetDir = imageRoot.resolve(safe(stripExtension(path.getFileName().toString())));
        Files.createDirectories(targetDir);
        Map<Integer, Path> result = new LinkedHashMap<>();
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 144, ImageType.RGB);
                Path target = targetDir.resolve("page_%03d.png".formatted(i + 1));
                ImageIO.write(image, "png", target.toFile());
                result.put(i + 1, target);
            }
        }
        return result;
    }

    private Map<Integer, Path> renderPptx(Path path) throws Exception {
        Path targetDir = imageRoot.resolve(safe(stripExtension(path.getFileName().toString())));
        Files.createDirectories(targetDir);
        Map<Integer, Path> result = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(path); XMLSlideShow show = new XMLSlideShow(input)) {
            Dimension size = show.getPageSize();
            int width = Math.max(1, size.width * 2);
            int height = Math.max(1, size.height * 2);
            int index = 1;
            for (XSLFSlide slide : show.getSlides()) {
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setPaint(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.scale(2.0, 2.0);
                slide.draw(graphics);
                graphics.dispose();
                Path target = targetDir.resolve("page_%03d.png".formatted(index));
                ImageIO.write(image, "png", target.toFile());
                result.put(index++, target);
            }
        }
        return result;
    }

    private String suffix(Path path) {
        String name = path.getFileName().toString();
        int index = name.lastIndexOf('.');
        return index >= 0 ? name.substring(index).toLowerCase() : "";
    }

    private String stripExtension(String name) {
        int index = name.lastIndexOf('.');
        return index >= 0 ? name.substring(0, index) : name;
    }

    private String safe(String value) {
        String normalized = Normalizer.normalize(text(value), Normalizer.Form.NFKC);
        return UNSAFE.matcher(normalized).replaceAll("_").replaceAll("^[_\\.\\-]+|[_\\.\\-]+$", "");
    }

    private String firstLine(String value, String fallback) {
        for (String line : text(value).split("\\R")) {
            if (!line.isBlank()) {
                return line.strip();
            }
        }
        return fallback;
    }

    private String clip(String value, int limit) {
        String text = text(value);
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private String text(Object value) {
        return text(value, "");
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).strip();
        return text.isBlank() ? fallback : text;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private record SlideData(int slideNumber, String title, String text, String notes) {
    }

    public static class PptServiceException extends RuntimeException {
        public PptServiceException(String message) {
            super(message);
        }

        public PptServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
