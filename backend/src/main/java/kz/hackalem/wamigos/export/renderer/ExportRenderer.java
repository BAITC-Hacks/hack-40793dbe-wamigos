package kz.hackalem.wamigos.export.renderer;

import kz.hackalem.wamigos.export.model.ExportFormat;
import kz.hackalem.wamigos.export.model.ExportModel;

public interface ExportRenderer {

    ExportFormat format();

    byte[] render(ExportModel model);

    String mediaType();
}
