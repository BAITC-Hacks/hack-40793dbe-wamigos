package kz.hackalem.wamigos.export.renderer;

import com.deepoove.poi.XWPFTemplate;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import kz.hackalem.wamigos.error.ExportException;
import kz.hackalem.wamigos.export.model.ExportFormat;
import kz.hackalem.wamigos.export.model.ExportModel;
import kz.hackalem.wamigos.export.model.ExportProblem;
import kz.hackalem.wamigos.export.model.ExportTask;
import kz.hackalem.wamigos.export.model.ExportTranscriptLine;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Component;

@Component
public class DocxExportRenderer implements ExportRenderer {

    private static final String FONT = "DejaVu Sans";
    private static final String DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public ExportFormat format() {
        return ExportFormat.DOCX;
    }

    @Override
    public byte[] render(ExportModel model) {
        try (ByteArrayInputStream source = new ByteArrayInputStream(createTemplate());
             XWPFTemplate template = XWPFTemplate.compile(source).render(Map.of(
                     "title", model.title(),
                     "meetingDate", model.meetingDate(),
                     "speakers", speakers(model)
             ));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFDocument document = template.getXWPFDocument();
            appendHeading(document, "Краткие итоги");
            appendParagraph(document, model.summary());
            appendTasks(document, model);
            appendProblems(document, model);
            appendTranscript(document, model);
            template.write(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new ExportException(exception);
        }
    }

    @Override
    public String mediaType() {
        return DOCX_MEDIA_TYPE;
    }

    private byte[] createTemplate() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            setFont(titleRun);
            titleRun.setText("Протокол совещания: {{title}}");

            appendLabelValue(document, "Дата:", "{{meetingDate}}");
            appendLabelValue(document, "Говорящие:", "{{speakers}}");
            document.write(output);
            return output.toByteArray();
        }
    }

    private void appendTasks(XWPFDocument document, ExportModel model) {
        appendHeading(document, "Поручения");
        if (model.tasks().isEmpty()) {
            appendParagraph(document, "Не указаны");
            return;
        }
        XWPFTable table = document.createTable(1, 4);
        table.setWidth("100%");
        fillRow(table, 0, new String[]{"Действие", "Исполнитель", "Постановщик", "Срок"}, true);
        for (ExportTask task : model.tasks()) {
            int row = table.getNumberOfRows();
            table.createRow();
            fillRow(table, row, new String[]{task.action(), task.assignee(), task.assigner(), task.deadline()}, false);
        }
    }

    private void appendProblems(XWPFDocument document, ExportModel model) {
        appendHeading(document, "Проблемы");
        if (model.problems().isEmpty()) {
            appendParagraph(document, "Не указаны");
            return;
        }
        for (ExportProblem problem : model.problems()) {
            appendParagraph(document, problem.text() + " — сообщил: " + problem.reportedBy());
        }
    }

    private void appendTranscript(XWPFDocument document, ExportModel model) {
        appendHeading(document, "Полный транскрипт");
        if (model.transcript().isEmpty()) {
            appendParagraph(document, "Не указан");
            return;
        }
        for (ExportTranscriptLine line : model.transcript()) {
            appendParagraph(document, "[" + line.timeCode() + "] " + line.speaker() + ": " + line.text());
        }
    }

    private void appendHeading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(180);
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(14);
        setFont(run);
        run.setText(text);
    }

    private void appendParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setFontSize(10);
        setFont(run);
        run.setText(text);
    }

    private void appendLabelValue(XWPFDocument document, String label, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun labelRun = paragraph.createRun();
        labelRun.setBold(true);
        setFont(labelRun);
        labelRun.setText(label + " ");
        XWPFRun valueRun = paragraph.createRun();
        setFont(valueRun);
        valueRun.setText(value);
    }

    private void fillRow(XWPFTable table, int rowIndex, String[] values, boolean bold) {
        for (int index = 0; index < values.length; index++) {
            XWPFTableCell cell = table.getRow(rowIndex).getCell(index);
            cell.removeParagraph(0);
            XWPFParagraph paragraph = cell.addParagraph();
            XWPFRun run = paragraph.createRun();
            run.setBold(bold);
            run.setFontSize(9);
            setFont(run);
            run.setText(values[index]);
        }
    }

    private void setFont(XWPFRun run) {
        run.setFontFamily(FONT);
    }

    private String speakers(ExportModel model) {
        return model.speakers().isEmpty() ? "Не указаны" : String.join(", ", model.speakers());
    }
}
