package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.cartography.sld.UploadedSldValidator;
import cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Maps the framework-free uploaded SLD validator to the business HTTP error contract. */
@Component
public class SpatialSldValidator {

    public static final int MAX_BYTES = UploadedSldValidator.MAX_BYTES;
    private final UploadedSldValidator delegate = new UploadedSldValidator();

    public ValidatedSld validate(String fileName, byte[] bytes, SpatialGeometryFamily family) {
        try {
            UploadedSldValidator.ValidatedSld result = delegate.validate(
                    fileName, bytes, SpatialStyleDocument.GeometryFamily.valueOf(family.name())
            );
            return new ValidatedSld(result.fileName(), result.text(), result.size());
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
    }

    public String forPreview(ValidatedSld validated, String layerName) {
        try {
            return delegate.forPreview(
                    new UploadedSldValidator.ValidatedSld(validated.fileName(), validated.text(), validated.size()),
                    layerName
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ValidatedSld(String fileName, String text, int size) {
    }
}
