package com.icthh.xm.ms.entity.domain.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "stateKey", "name", "inputSpec", "inputForm", "actionName", "showResponse" })
@Data
@Builder
public class NextSpec {

    @JsonProperty("stateKey")
    private String stateKey;
    /** Localized action name via map where key is ISO 639-1 code */
    @JsonProperty("name")
    private Map<String, String> name;

    @JsonProperty("inputSpec")
    private String inputSpec;

    @JsonProperty("inputForm")
    private String inputForm;

    @JsonProperty("actionName")
    private Map<String, String>  actionName;

    @JsonProperty("showResponse")
    private Boolean showResponse;

}
