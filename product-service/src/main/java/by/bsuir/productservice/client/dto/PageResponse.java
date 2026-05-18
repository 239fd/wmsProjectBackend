package by.bsuir.productservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PageResponse<T>(List<T> content) {
    public List<T> contentOrEmpty() {
        return content != null ? content : Collections.emptyList();
    }
}
