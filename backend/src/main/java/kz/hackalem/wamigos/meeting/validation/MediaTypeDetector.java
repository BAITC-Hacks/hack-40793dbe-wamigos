package kz.hackalem.wamigos.meeting.validation;

import java.io.IOException;
import java.io.InputStream;
import kz.hackalem.wamigos.error.ValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class MediaTypeDetector {

    private final Tika tika;

    public String detect(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        } catch (IOException exception) {
            throw new ValidationException("Не удалось прочитать загруженный файл.");
        }
    }
}
