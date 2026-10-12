package br.com.raizes;

import com.fasterxml.jackson.databind.*;
import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.*;

/** Relatório acadêmico em PDF, gerado em Java a partir dos artefatos versionados. */
public final class AcademicReport implements AutoCloseable {
  static final float CM = 72f / 2.54f, LEFT = 3 * CM, RIGHT = 2 * CM;
  static final float TOP = 3 * CM, BOTTOM = 2 * CM;
  final PDDocument doc = new PDDocument();
  final PDType0Font regular, bold, mono;
  final Properties person = new Properties();
  final List<Entry> toc = new ArrayList<>();
  PDPage page, tocPage;
  PDPageContentStream out;
  float y;
  boolean code;
  int tableNumber;

  record Entry(String title, PDPage page, int number) {}

  AcademicReport() throws Exception {
    regular =
        PDType0Font.load(
            doc, new File("/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf"));
    bold =
        PDType0Font.load(
            doc, new File("/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"));
    mono = PDType0Font.load(doc, new File("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"));
    try (var reader =
        Files.newBufferedReader(
            Path.of("docs/identificacao-academica.properties"), StandardCharsets.UTF_8)) {
      person.load(reader);
    }
  }

  String p(String key) {
    return person.getProperty(key, "A INFORMAR");
  }

  float width() {
    return page.getMediaBox().getWidth() - LEFT - RIGHT;
  }

  float measure(String s, PDType0Font f, float size) throws IOException {
    return f.getStringWidth(s) * size / 1000;
  }

  void closeStream() throws IOException {
    if (out != null) {
      out.close();
      out = null;
    }
  }

  void newPage(boolean landscape) throws IOException {
    closeStream();
    page =
        new PDPage(
            landscape
                ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                : PDRectangle.A4);
    doc.addPage(page);
    out = new PDPageContentStream(doc, page);
    y = page.getMediaBox().getHeight() - TOP;
  }

  void ensure(float height) throws IOException {
    if (page == null || y - height < BOTTOM) newPage(false);
  }

  void draw(String text, float x, float baseline, PDType0Font font, float size) throws IOException {
    out.beginText();
    out.setFont(font, size);
    out.newLineAtOffset(x, baseline);
    out.showText(text);
    out.endText();
  }

  String clean(String s) {
    return s.replace("`", "").replace("**", "").replace('\t', ' ').replace("\u00a0", " ");
  }

  List<String> wrap(String text, PDType0Font f, float size, float max) throws IOException {
    List<String> lines = new ArrayList<>();
    String line = "";
    for (String word : clean(text).split(" +")) {
      if (word.isEmpty()) continue;
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (measure(candidate, f, size) <= max) {
        line = candidate;
        continue;
      }
      if (!line.isEmpty()) {
        lines.add(line);
        line = "";
      }
      while (measure(word, f, size) > max) {
        int n = 1;
        while (n < word.length() && measure(word.substring(0, n + 1), f, size) <= max) n++;
        lines.add(word.substring(0, n));
        word = word.substring(n);
      }
      line = word;
    }
    if (!line.isEmpty()) lines.add(line);
    if (lines.isEmpty()) lines.add(" ");
    return lines;
  }

  void paragraph(String text) throws IOException {
    boolean reference =
        text.startsWith("UNINTER.")
            || text.startsWith("BRASIL.")
            || text.startsWith("SPRING.")
            || text.startsWith("H2.")
            || text.startsWith("OWASP.");
    float indent = reference ? 0 : 1.25f * CM;
    List<String> lines = wrap(text, regular, 12, width() - indent);
    ensure(Math.min(lines.size(), 2) * 18 + 6);
    for (int i = 0; i < lines.size(); i++) {
      ensure(18);
      String line = lines.get(i);
      float x = LEFT + (i == 0 ? indent : 0);
      if (!reference && i < lines.size() - 1 && line.contains(" ")) {
        String[] words = line.split(" ");
        float wordsWidth = 0;
        for (String word : words) wordsWidth += measure(word, regular, 12);
        float spacing = (width() - (i == 0 ? indent : 0) - wordsWidth) / (words.length - 1);
        for (String word : words) {
          draw(word, x, y, regular, 12);
          x += measure(word, regular, 12) + spacing;
        }
      } else draw(line, x, y, regular, 12);
      y -= reference ? 12 : 18;
    }
    y -= reference ? 12 : 6;
  }

  void heading(String title, boolean main) throws IOException {
    if (main) newPage(false);
    else ensure(70);
    List<String> lines = wrap(title, bold, 12, width());
    if (main) toc.add(new Entry(title, page, doc.getNumberOfPages() - 1));
    for (String line : lines) {
      draw(line, LEFT, y, bold, 12);
      y -= 18;
    }
    y -= 18;
  }

  void codeLine(String line) throws IOException {
    for (String original : line.split("\\n", -1))
      for (String part : wrap(original, mono, 9, width() - 12)) {
        ensure(12);
        draw(part, LEFT + 6, y, mono, 9);
        y -= 12;
      }
  }

  String[] cells(String line) {
    return line.substring(1, line.lastIndexOf('|')).split("\\|", -1);
  }

  void table(List<String> source) throws IOException {
    if (source.size() == 1 || (source.size() == 2 && source.get(1).matches("\\|[|: -]+\\|"))) {
      paragraph("Não se aplicam parâmetros path/query nesta operação.");
      return;
    }
    String[] header = cells(source.get(0));
    int n = header.length;
    float[] fraction =
        n == 3
            ? new float[] {.13f, .35f, .52f}
            : n == 4
                ? new float[] {.19f, .37f, .21f, .23f}
                : n == 5 ? new float[] {.10f, .28f, .18f, .25f, .19f} : null;
    float[] widths = new float[n];
    for (int i = 0; i < n; i++) widths[i] = width() * (fraction == null ? 1f / n : fraction[i]);
    ensure(85);
    draw(
        "Tabela "
            + (++tableNumber)
            + " — "
            + clean(header[0].trim())
            + " e informações relacionadas",
        LEFT,
        y,
        regular,
        10);
    y -= 18;
    tableRow(header, widths, true);
    for (int i = 1; i < source.size(); i++) {
      if (source.get(i).matches("\\|[|: -]+\\|")) continue;
      String[] row = cells(source.get(i));
      float height = rowHeight(row, widths, regular);
      if (y - height < BOTTOM) {
        newPage(false);
        tableRow(header, widths, true);
      }
      tableRow(row, widths, false);
    }
    ensure(22);
    draw("Fonte: artefatos do projeto Raízes do Nordeste (2026).", LEFT, y - 10, regular, 10);
    y -= 30;
  }

  float rowHeight(String[] values, float[] widths, PDType0Font font) throws IOException {
    int lines = 1;
    for (int i = 0; i < widths.length; i++)
      lines =
          Math.max(
              lines,
              wrap(i < values.length ? values[i].trim() : "", font, 9, widths[i] - 10).size());
    return lines * 11 + 10;
  }

  void tableRow(String[] values, float[] widths, boolean header) throws IOException {
    var font = header ? bold : regular;
    float height = rowHeight(values, widths, font), x = LEFT;
    if (header) {
      out.setNonStrokingColor(new Color(235, 235, 235));
      out.addRect(LEFT, y - height + 3, width(), height);
      out.fill();
      out.setNonStrokingColor(Color.BLACK);
    }
    out.setLineWidth(.3f);
    for (int i = 0; i < widths.length; i++) {
      out.addRect(x, y - height + 3, widths[i], height);
      out.stroke();
      List<String> lines = wrap(i < values.length ? values[i].trim() : "", font, 9, widths[i] - 10);
      float baseline = y - 9;
      for (String line : lines) {
        draw(line, x + 5, baseline, font, 9);
        baseline -= 11;
      }
      x += widths[i];
    }
    y -= height;
  }

  void markdown(Path path, boolean appendix) throws Exception {
    List<String> lines = Files.readAllLines(path);
    StringBuilder paragraph = new StringBuilder();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.startsWith("```")) {
        flush(paragraph);
        code = !code;
        y -= 6;
        continue;
      }
      if (code) {
        codeLine(line);
        continue;
      }
      if (line.startsWith("# ")) continue;
      if (line.startsWith("Autor:") || line.startsWith("RU:")) continue;
      if (line.startsWith("##")) {
        flush(paragraph);
        heading(line.replaceFirst("^#+\\s*", ""), !appendix && line.startsWith("## "));
        continue;
      }
      if (line.startsWith("|")) {
        flush(paragraph);
        List<String> table = new ArrayList<>();
        while (i < lines.size() && lines.get(i).startsWith("|")) table.add(lines.get(i++));
        i--;
        table(table);
        continue;
      }
      if (line.isBlank()) {
        flush(paragraph);
        continue;
      }
      if (line.matches("^\\d+\\..*")) {
        flush(paragraph);
        paragraph(line);
        continue;
      }
      if (!paragraph.isEmpty()) paragraph.append(' ');
      paragraph.append(line);
    }
    flush(paragraph);
  }

  void flush(StringBuilder b) throws IOException {
    if (!b.isEmpty()) {
      paragraph(b.toString());
      b.setLength(0);
    }
  }

  void centered(String text, float baseline, boolean strong) throws IOException {
    var f = strong ? bold : regular;
    for (String line : wrap(text, f, 12, width())) {
      draw(line, LEFT + (width() - measure(line, f, 12)) / 2, baseline, f, 12);
      baseline -= 18;
    }
  }

  void pretext() throws IOException {
    newPage(false);
    centered(p("instituicao").toUpperCase(Locale.ROOT), 745, true);
    centered("CURSO: " + p("curso"), 718, false);
    centered("JEFERSON RIVALDO SANTOS", 645, true);
    centered("RU: " + p("ru"), 623, false);
    centered("RAÍZES DO NORDESTE", 460, true);
    centered(p("disciplina").toUpperCase(Locale.ROOT), 432, true);
    centered("Polo de apoio: " + p("polo"), 345, false);
    centered("Semestre/período: " + p("semestre"), 322, false);
    centered("Professor(a): " + p("professor"), 299, false);
    centered(p("cidade"), 100, false);
    centered(p("ano"), 78, false);
    newPage(false);
    centered(p("nome").toUpperCase(Locale.ROOT), 745, true);
    centered("RU: " + p("ru"), 721, false);
    centered("RAÍZES DO NORDESTE", 545, true);
    centered(p("disciplina").toUpperCase(Locale.ROOT), 518, true);
    y = 395;
    for (String line :
        wrap(
            "Trabalho apresentado à disciplina "
                + p("disciplina")
                + ", do curso "
                + p("curso")
                + ", no Centro Universitário Internacional UNINTER. Desenvolvimento de uma API REST"
                + " em Java para a rede Raízes do Nordeste. Professor(a): "
                + p("professor")
                + ".",
            regular,
            12,
            width() / 2)) {
      draw(line, LEFT + width() / 2, y, regular, 12);
      y -= 14;
    }
    centered(p("cidade"), 100, false);
    centered(p("ano"), 78, false);
    newPage(false);
    tocPage = page;
  }

  void evidence() throws Exception {
    heading("APÊNDICE C — EVIDÊNCIAS DE EXECUÇÃO", true);
    paragraph(
        "Os registros abaixo provêm dos artefatos de execução disponíveis no projeto. A execução de"
            + " teste em banco temporário é distinta da verificação de persistência em arquivo após"
            + " reinício. Não são utilizados dados pessoais de clientes reais.");
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    List<String> rows =
        new ArrayList<>(List.of("| Suíte | Executados | Falhas / erros / ignorados |"));
    for (String name : List.of("ApiIntegrationTest", "CollectionContractTest", "DatabaseMigrationTest")) {
      Path file = Path.of("target/surefire-reports/TEST-br.com.raizes." + name + ".xml");
      if (!Files.exists(file))
        file =
            Path.of(
                "docs/evidencias-junit-" + (name.startsWith("Api") ? "api" : name.startsWith("Collection") ? "colecao" : "migracao") + ".xml");
      var root = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
      rows.add(
          "| "
              + name
              + " | "
              + root.getAttribute("tests")
              + " | "
              + root.getAttribute("failures")
              + " / "
              + root.getAttribute("errors")
              + " / "
              + root.getAttribute("skipped")
              + " |");
    }
    table(rows);
    paragraph(
        "Comando de validação: mvn verify. A execução registrada nesta revisão de 11 out. 2026 "
            + "aprovou 33 testes Java: 31 de integração, um executor de 23 requisições da coleção "
            + "e um de migração em arquivo H2 existente; zero falhas, erros ou ignorados. "
            + "O executor percorre as sete pastas da coleção na ordem do fluxo. "
            + "Os relatórios sanitizados estão em docs/evidencias-junit-api.xml, "
            + "docs/evidencias-junit-colecao.xml e docs/evidencias-junit-migracao.xml.");
    ObjectMapper mapper = new ObjectMapper();
    for (String file :
        List.of("docs/evidencias-colecao-java.json", "docs/evidencias-runtime-java.json", "docs/evidencias-postman-newman.json")) {
      paragraph(
          "Registro: "
              + file
              + ". A data executadoEm está expressa em UTC. A coleção foi validada em Java com"
              + " asserções equivalentes em Java; o arquivo separado de Newman registra a execução"
              + " dos scripts Postman reais. O registro de runtime mantém sua data histórica.");
      codeLine(
          mapper
              .writerWithDefaultPrettyPrinter()
              .writeValueAsString(mapper.readTree(Path.of(file).toFile()))
              .replace("\r", ""));
    }
    paragraph(
        "Cobertura complementar: auditoriaEOpenApi verifica PEDIDO_CRIADO na auditoria;"
            + " cupomERecursoAusente verifica unidade inexistente (404);"
            + " quantidadeFracionariaRejeitada verifica formato numérico inválido (422). Estes são"
            + " testes JUnit adicionais, ainda não representados por requisições próprias na"
            + " coleção Postman.");
  }

  void diagram(String name, String title, boolean landscape) throws Exception {
    newPage(landscape);
    toc.add(new Entry(title, page, doc.getNumberOfPages() - 1));
    draw(title, LEFT, y, bold, 12);
    y -= 35;
    var image =
        LosslessFactory.createFromImage(
            doc, ImageIO.read(Path.of("docs/diagramas/" + name + ".png").toFile()));
    float available = y - BOTTOM - 40,
        scale = Math.min(width() / image.getWidth(), available / image.getHeight());
    out.drawImage(
        image,
        LEFT + (width() - image.getWidth() * scale) / 2,
        y - image.getHeight() * scale,
        image.getWidth() * scale,
        image.getHeight() * scale);
    draw(
        "Fonte: elaboração para o projeto Raízes do Nordeste (2026).",
        LEFT,
        BOTTOM + 14,
        regular,
        10);
    draw(
        "Fonte editável e imagem ampliável: docs/diagramas/" + name + ".",
        LEFT,
        BOTTOM,
        regular,
        10);
  }

  void finish() throws IOException {
    closeStream();
    page = tocPage;
    out = new PDPageContentStream(doc, page);
    y = page.getMediaBox().getHeight() - TOP;
    centered("SUMÁRIO", y, true);
    y -= 40;
    for (Entry entry : toc) {
      List<String> lines = wrap(entry.title(), regular, 12, width() - 38);
      for (int i = 0; i < lines.size(); i++) {
        draw(lines.get(i), LEFT, y, regular, 12);
        if (i == lines.size() - 1) {
          String number = Integer.toString(entry.number());
          draw(
              number,
              page.getMediaBox().getWidth() - RIGHT - measure(number, regular, 12),
              y,
              regular,
              12);
        }
        y -= 18;
      }
      y -= 7;
      if (y < BOTTOM) throw new IllegalStateException("Sumário excedeu a página reservada");
    }
    closeStream();
    for (int i = 3; i < doc.getNumberOfPages(); i++) {
      page = doc.getPage(i);
      out = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true);
      String number = Integer.toString(i);
      draw(
          number,
          page.getMediaBox().getWidth() - RIGHT - measure(number, regular, 10),
          page.getMediaBox().getHeight() - 2 * CM,
          regular,
          10);
      closeStream();
    }
    var outline = new PDDocumentOutline();
    doc.getDocumentCatalog().setDocumentOutline(outline);
    for (Entry entry : toc) {
      var item = new PDOutlineItem();
      item.setTitle(entry.title());
      var destination = new PDPageFitDestination();
      destination.setPage(entry.page());
      item.setDestination(destination);
      outline.addLast(item);
    }
    outline.openNode();
    doc.getDocumentInformation().setTitle("Raízes do Nordeste — Projeto Back-End");
    doc.getDocumentInformation().setAuthor(p("nome"));
    doc.getDocumentInformation().setSubject("Projeto Multidisciplinar — RU " + p("ru"));
    doc.save("docs/" + p("ru") + "_Projeto_Back_End.pdf");
    Files.copy(
        Path.of("docs/" + p("ru") + "_Projeto_Back_End.pdf"),
        Path.of("docs/relatorio-tecnico.pdf"),
        StandardCopyOption.REPLACE_EXISTING);
  }

  public static void generate() throws Exception {
    try (var report = new AcademicReport()) {
      report.pretext();
      report.markdown(Path.of("docs/relatorio.md"), false);
      report.heading("APÊNDICE A — CONTRATOS E EXEMPLOS DA API", true);
      report.paragraph(
          "Os exemplos são ilustrativos e seguem os esquemas da API implementada. IDs e timestamps"
              + " devem ser substituídos pelos dados da execução. Valores monetários são inteiros"
              + " em centavos. Nem todo código de erro listado se aplica a toda operação.");
      report.markdown(Path.of("docs/api.md"), true);
      report.heading("APÊNDICE B — CENÁRIOS DE TESTE DA API", true);
      report.markdown(Path.of("docs/plano-testes.md"), true);
      report.evidence();
      report.diagram("casos-de-uso", "APÊNDICE D — DIAGRAMA DE CASOS DE USO", true);
      report.diagram("classes", "APÊNDICE E — DIAGRAMA CONCEITUAL DE CLASSES", true);
      report.diagram("arquitetura", "APÊNDICE F — ARQUITETURA EM CAMADAS", true);
      report.diagram("der", "APÊNDICE G — DIAGRAMA ENTIDADE-RELACIONAMENTO", true);
      report.finish();
      System.out.println(
          "PDF acadêmico gerado: docs/"
              + report.p("ru")
              + "_Projeto_Back_End.pdf ("
              + report.doc.getNumberOfPages()
              + " páginas)");
    }
  }

  public static void main(String[] args) throws Exception {
    generate();
  }

  public void close() throws IOException {
    closeStream();
    doc.close();
  }
}
