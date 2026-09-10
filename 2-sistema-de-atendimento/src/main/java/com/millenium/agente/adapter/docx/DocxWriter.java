package com.millenium.agente.adapter.docx;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Escreve um documento Word (.docx) usando Apache POI (XWPF) - sem Python nem dependencia externa.
 * Converte um texto com marcacao leve em paragrafos Word:
 * <ul>
 *   <li>{@code # titulo}   -> titulo grande</li>
 *   <li>{@code ## sub}     -> subtitulo</li>
 *   <li>{@code ### sub}    -> subtitulo menor</li>
 *   <li>{@code - item} ou {@code * item} -> lista com marcador</li>
 *   <li>linha vazia        -> separa paragrafos</li>
 *   <li>demais linhas      -> paragrafo comum (negrito no texto antes de ':' quando houver)</li>
 * </ul>
 */
public class DocxWriter {

    public Path write(Path file, String title, String content) {
        try (XWPFDocument doc = new XWPFDocument()) {
            if (title != null && !title.isBlank()) {
                title(doc, title);
            }
            if (content != null) {
                for (String line : content.split("\r?\n")) {
                    render(doc, line);
                }
            }
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                doc.write(out);
            }
            return file.toAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o .docx " + file + ": " + e.getMessage(), e);
        }
    }

    private void title(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(18);
        r.setText(text.strip());
    }

    private void render(XWPFDocument doc, String line) {
        String s = line.strip();
        if (s.isEmpty()) {
            doc.createParagraph();
            return;
        }
        if (s.startsWith("### ")) {
            heading(doc, s.substring(4), 13);
        } else if (s.startsWith("## ")) {
            heading(doc, s.substring(3), 15);
        } else if (s.startsWith("# ")) {
            heading(doc, s.substring(2), 16);
        } else if (s.startsWith("- ") || s.startsWith("* ")) {
            XWPFParagraph p = doc.createParagraph();
            p.setIndentationLeft(360);
            XWPFRun r = p.createRun();
            r.setText("• " + stripMarkup(s.substring(2)));
        } else {
            paragraph(doc, s);
        }
    }

    private void heading(XWPFDocument doc, String text, int size) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(120);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(size);
        r.setText(stripMarkup(text));
    }

    /** Paragrafo comum; se houver "Rotulo: valor", deixa o rotulo em negrito. */
    private void paragraph(XWPFDocument doc, String text) {
        String clean = stripMarkup(text);
        XWPFParagraph p = doc.createParagraph();
        int idx = clean.indexOf(':');
        if (idx > 0 && idx <= 30) {
            XWPFRun label = p.createRun();
            label.setBold(true);
            label.setText(clean.substring(0, idx + 1));
            XWPFRun rest = p.createRun();
            rest.setText(clean.substring(idx + 1));
        } else {
            p.createRun().setText(clean);
        }
    }

    /** Remove marcadores Markdown de enfase (** e *) que nao viram formatacao aqui. */
    private String stripMarkup(String s) {
        return s.replace("**", "").strip();
    }
}
