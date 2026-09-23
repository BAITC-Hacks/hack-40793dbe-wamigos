package kz.hackalem.wamigos.export.renderer;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import kz.hackalem.wamigos.config.ExportProperties;
import kz.hackalem.wamigos.error.ExportException;
import kz.hackalem.wamigos.export.model.ExportFormat;
import kz.hackalem.wamigos.export.model.ExportModel;
import kz.hackalem.wamigos.export.model.ExportProblem;
import kz.hackalem.wamigos.export.model.ExportTask;
import kz.hackalem.wamigos.export.model.ExportTranscriptLine;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import org.vandeseer.easytable.RepeatedHeaderTableDrawer;
import org.vandeseer.easytable.structure.Row;
import org.vandeseer.easytable.structure.Table;
import org.vandeseer.easytable.structure.cell.TextCell;

@Component
@RequiredArgsConstructor
public class PdfExportRenderer implements ExportRenderer {

    private static final float MARGIN = 50f;

    private final ExportProperties exportProperties;

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public byte[] render(ExportModel model) {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = Files.newInputStream(exportProperties.fontPath());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, fontStream, true);
            writeOverview(document, font, model);
            writeTasks(document, font, model);
            writeProblemsAndTranscript(document, font, model);
            document.save(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new ExportException(exception);
        }
    }

    @Override
    public String mediaType() {
        return "application/pdf";
    }

    private void writeOverview(PDDocument document, PDType0Font font, ExportModel model) throws IOException {
        try (PdfTextWriter writer = new PdfTextWriter(document, font)) {
            writer.writeTitle("Протокол совещания: " + model.title());
            writer.writeParagraph("Дата: " + model.meetingDate());
            writer.writeParagraph("Говорящие: " + speakers(model));
            writer.writeHeading("Краткие итоги");
            writer.writeParagraph(model.summary());
        }
    }

    private void writeTasks(PDDocument document, PDType0Font font, ExportModel model) throws IOException {
        if (model.tasks().isEmpty()) {
            try (PdfTextWriter writer = new PdfTextWriter(document, font)) {
                writer.writeHeading("Поручения");
                writer.writeParagraph("Не указаны");
            }
            return;
        }

        float firstStartY;
        try (PdfTextWriter writer = new PdfTextWriter(document, font)) {
            writer.writeHeading("Поручения");
            firstStartY = writer.currentY();
        }

        Table table = buildTaskTable(font, model);
        RepeatedHeaderTableDrawer drawer = RepeatedHeaderTableDrawer.builder()
                .table(table)
                .startX(MARGIN)
                .startY(firstStartY)
                .endY(MARGIN)
                .numberOfRowsToRepeat(1)
                .build();
        drawer.draw(() -> document, () -> new PDPage(PDRectangle.A4), MARGIN);
    }

    private Table buildTaskTable(PDType0Font font, ExportModel model) {
        Table.TableBuilder table = Table.builder()
                .addColumnsOfWidth(195f, 100f, 100f, 100f)
                .font(font)
                .fontSize(8)
                .wordBreak(true)
                .borderColor(Color.GRAY)
                .borderWidth(0.5f)
                .padding(4f);
        table.addRow(Row.builder()
                .backgroundColor(new Color(224, 231, 239))
                .add(TextCell.builder().text("Действие").build())
                .add(TextCell.builder().text("Исполнитель").build())
                .add(TextCell.builder().text("Постановщик").build())
                .add(TextCell.builder().text("Срок").build())
                .build());
        for (ExportTask task : model.tasks()) {
            table.addRow(Row.builder()
                    .add(TextCell.builder().text(task.action()).build())
                    .add(TextCell.builder().text(task.assignee()).build())
                    .add(TextCell.builder().text(task.assigner()).build())
                    .add(TextCell.builder().text(task.deadline()).build())
                    .build());
        }
        return table.build();
    }

    private void writeProblemsAndTranscript(PDDocument document, PDType0Font font, ExportModel model) throws IOException {
        try (PdfTextWriter writer = new PdfTextWriter(document, font)) {
            writer.writeHeading("Проблемы");
            if (model.problems().isEmpty()) {
                writer.writeParagraph("Не указаны");
            } else {
                for (ExportProblem problem : model.problems()) {
                    writer.writeParagraph(problem.text() + " — сообщил: " + problem.reportedBy());
                }
            }

            writer.writeHeading("Полный транскрипт");
            if (model.transcript().isEmpty()) {
                writer.writeParagraph("Не указан");
            } else {
                for (ExportTranscriptLine line : model.transcript()) {
                    writer.writeParagraph("[" + line.timeCode() + "] " + line.speaker() + ": " + line.text());
                }
            }
        }
    }

    private String speakers(ExportModel model) {
        return model.speakers().isEmpty() ? "Не указаны" : String.join(", ", model.speakers());
    }
}
