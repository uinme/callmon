package com.uinme.mes.callmonitor.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.uinme.mes.callmonitor.model.CsvModel;

@Mapper
public interface CsvMapper {
    public void insert(CsvModel csvModel);
}
