package cn.superhuang.data.scalpel.business.dataentry.service;

import java.util.Map;

public record DataEntryRecordUpdateResult(boolean changed, Map<String, Object> before, Map<String, Object> after) {}
