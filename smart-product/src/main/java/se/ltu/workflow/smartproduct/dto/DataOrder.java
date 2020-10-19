package se.ltu.workflow.smartproduct.dto;

import se.arkalix.dto.DtoReadableAs;
import se.arkalix.dto.DtoToString;
import se.arkalix.dto.DtoWritableAs;

import static se.arkalix.dto.DtoEncoding.JSON;

@DtoReadableAs(JSON)
@DtoWritableAs(JSON)
@DtoToString
public interface DataOrder {
    String articleId();
    long serialId();
}
