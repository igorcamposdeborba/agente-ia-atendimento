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

    public Path escrever(Path arquivo, String titulo, String conteudo) {
        try (XWPFDocument doc = new XWPFDocument()) {
            if (titulo != null && !titulo.isBlank()) {
                titulo(doc, titulo);
            }
            if (conteudo != null) {
                for (String linha : conteudo.split("\r?\n")) {
                    renderizar(doc, linha);
                }
            }
            if (arquivo.getParent() != null) Files.createDirectories(arquivo.getParent());
            try (OutputStream out = Files.newOutputStream(arquivo)) {
                doc.write(out);
            }
            return arquivo.toAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o .docx " + arquivo + ": " + e.getMessage(), e);
        }
    }

    private void titulo(XWPFDocument doc, String texto) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(18);
        r.setText(texto.strip());
    }

    private void renderizar(XWPFDocument doc, String linha) {
        String s = linha.strip();
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
            r.setText("• " + limparMarcacao(s.substring(2)));
        } else {
            paragrafo(doc, s);
        }
    }

    private void heading(XWPFDocument doc, String texto, int size) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(120);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(size);
        r.setText(limparMarcacao(texto));
    }

    /** Paragrafo comum; se houver "Rotulo: valor", deixa o rotulo em negrito. */
    private void paragrafo(XWPFDocument doc, String texto) {
        String limpo = limparMarcacao(texto);
        XWPFParagraph p = doc.createParagraph();
        int idx = limpo.indexOf(':');
        if (idx > 0 && idx <= 30) {
            XWPFRun rotulo = p.createRun();
            rotulo.setBold(true);
            rotulo.setText(limpo.substring(0, idx + 1));
            XWPFRun resto = p.createRun();
            resto.setText(limpo.substring(idx + 1));
        } else {
            p.createRun().setText(limpo);
        }
    }

    /** Remove marcadores Markdown de enfase (** e *) que nao viram formatacao aqui. */
    private String limparMarcacao(String s) {
        return s.replace("**", "").strip();
    }
}
