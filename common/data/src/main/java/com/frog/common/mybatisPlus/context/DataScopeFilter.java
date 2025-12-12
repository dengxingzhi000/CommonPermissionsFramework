package com.frog.common.mybatisPlus.context;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
@AllArgsConstructor
public class DataScopeFilter {
    private String clause;
    private Map<String, Object> params;

    public static DataScopeFilter empty() {
        return new DataScopeFilter(null, Collections.emptyMap());
    }
}
