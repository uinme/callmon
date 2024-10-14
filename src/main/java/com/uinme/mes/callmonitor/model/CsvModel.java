package com.uinme.mes.callmonitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;

@Data
@JsonPropertyOrder({
    "column1",
    "column2",
    "column3",
    "column4",
    "column5"
})
public class CsvModel {
    @JsonProperty("column1")
    private String column1;
    @JsonProperty("column2")
    private String column2;
    @JsonProperty("column3")
    private String column3;
    @JsonProperty("column4")
    private String column4;
    @JsonProperty("column5")
    private String column5;
}
