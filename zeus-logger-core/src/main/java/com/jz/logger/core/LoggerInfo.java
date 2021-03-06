package com.jz.logger.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import com.jz.logger.core.annotation.Logger;
import com.jz.logger.core.annotation.Trace;
import com.jz.logger.core.handler.DefaultFieldHandler;
import com.jz.logger.core.handler.FieldHandler;
import com.jz.logger.core.matcher.Matcher;
import com.jz.logger.core.util.ClassUtils;
import com.jz.logger.core.util.CollectionUtils;
import com.jz.logger.core.util.LoggerUtils;
import lombok.*;

import java.lang.reflect.Field;
import java.util.*;

@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoggerInfo {

    private Logger logger;

    private Object oldObject;

    private Object newObject;

    private Map<String, Object> extDataMap;

    @Getter(AccessLevel.NONE)
    private List<LoggerResult> loggerResults;

    /**
     * 日志生成时的时间戳
     */
    private Long createTime;

    public LoggerInfo(Object oldObject, Object newObject, Map<String, Object> extDataMap, Logger logger) {
        this.oldObject = oldObject;
        this.newObject = newObject;
        this.extDataMap = extDataMap;
        this.logger = logger;
        this.createTime = System.currentTimeMillis();
    }

    public void destroyLoggerResults() {
        loggerResults = null;
    }

    public List<LoggerResult> getLoggerResults() {
        if (loggerResults != null) {
            return loggerResults;
        } else if (oldObject == null && newObject == null) {
            loggerResults = Collections.emptyList();
        } else if (oldObject instanceof Collection || newObject instanceof Collection) {
            Matcher matcher = ClassUtils.getMatcherInstance(logger.collElementMatcher());
            Collection<?> oldCollection = oldObject == null ? Collections.emptyList() : ListUtil.toList((Collection<?>) oldObject);
            Collection<?> newCollection = newObject == null ? Collections.emptyList() : ListUtil.toList((Collection<?>) newObject);
            loggerResults = new ArrayList<>(Math.max(oldCollection.size(), newCollection.size()));
            int resourceType;
            for (Object newElement : newCollection) {
                // 旧集合中没找到新的，说明该元素为新增，若找到，则说明为编辑
                Object oldElement = CollectionUtils.findFirst(oldCollection, newElement, matcher);
                resourceType = LoggerUtils.getResourceType(oldElement, newElement, logger.resourceType(), logger.dynamicResourceType());
                loggerResults.add(new LoggerResult(oldElement, newElement, resourceType,
                        getTraceInfos(oldElement, newElement, resourceType)));
            }
            for (Object oldElement : oldCollection) {
                Object newElement = CollectionUtils.findFirst(newCollection, oldElement, matcher);
                if (newElement == null) {
                    // 新集合中没找到旧的，说明该元素被删掉了
                    resourceType = LoggerUtils.getResourceType(oldElement, null, logger.resourceType(), logger.dynamicResourceType());
                    loggerResults.add(new LoggerResult(oldElement, null, resourceType,
                            getTraceInfos(oldElement, null, resourceType)));
                }
            }
        } else {
            int resourceType = LoggerUtils.getResourceType(oldObject, newObject, logger.resourceType(), logger.dynamicResourceType());
            loggerResults = ListUtil.toList(new LoggerResult(oldObject, newObject, resourceType,
                    getTraceInfos(oldObject, newObject, resourceType)));
        }
        return loggerResults;
    }

    @SneakyThrows
    private List<TraceInfo> getTraceInfos(Object oldObject, Object newObject, int resourceType) {
        if (oldObject == null && newObject == null) {
            return Collections.emptyList();
        }
        Class<?> clazz = oldObject != null ? oldObject.getClass() : newObject.getClass();
        if (clazz == null) {
            return Collections.emptyList();
        }
        List<TraceInfo> result = new ArrayList<>();
        List<FieldInfo> fieldInfos = ClassUtils.getTraceFieldInfos(clazz);
        for (FieldInfo fieldInfo : fieldInfos) {
            Trace trace = fieldInfo.getTrace();
            if (DefaultFieldHandler.class != trace.fieldHandler()) {
                FieldHandler fieldHandler = ClassUtils.getFieldHandlerInstance(trace.fieldHandler());
                Field field = fieldInfo.getField();
                field.setAccessible(true);
                List<TraceInfo> fieldHandlerResult = fieldHandler.toTraceInfo(
                        new RuntimeFieldInfo(logger, trace, field, oldObject, newObject, null));
                if (CollUtil.isNotEmpty(fieldHandlerResult)) {
                    result.addAll(fieldHandlerResult);
                }
            }
            if (trace.permeate()) {
                Matcher matcher = ClassUtils.getMatcherInstance(trace.collElementMatcher());
                Field field = fieldInfo.getField();
                field.setAccessible(true);
                Object oldFieldValue = oldObject == null ? null : field.get(oldObject);
                Object newFieldValue = newObject == null ? null : field.get(newObject);
                if (oldFieldValue instanceof Collection || newFieldValue instanceof Collection) {
                    Collection<?> oldCollection = oldFieldValue == null ? Collections.emptyList() : ListUtil.toList((Collection<?>) oldFieldValue);
                    Collection<?> newCollection = newFieldValue == null ? Collections.emptyList() : ListUtil.toList((Collection<?>) newFieldValue);
                    for (Object newElement : newCollection) {
                        Object oldElement = CollectionUtils.findFirst(oldCollection, newElement, matcher);
                        if (oldElement == null) {
                            // 旧集合中没找到新的，说明该元素是新增的
                            result.add(LoggerUtils.buildTraceInfo(resourceType, logger, trace, field.getName(), null, newElement));
                        } else {
                            result.addAll(getTraceInfos(oldElement, newElement, resourceType));
                        }
                    }
                    for (Object oldElement : oldCollection) {
                        Object newElement = CollectionUtils.findFirst(newCollection, oldElement, matcher);
                        if (newElement == null) {
                            // 新集合中没找到旧的，说明该元素被删掉了
                            result.add(LoggerUtils.buildTraceInfo(resourceType, logger, trace, field.getName(), oldElement, null));
                        }
                    }
                } else {
                    result.addAll(getTraceInfos(oldFieldValue, newFieldValue, resourceType));
                }
            } else if (DefaultFieldHandler.class == trace.fieldHandler()) {
                TraceInfo traceInfo = LoggerUtils.buildTraceInfo(resourceType, logger, fieldInfo, oldObject, newObject);
                if (traceInfo != null) {
                    result.add(traceInfo);
                }
            }
        }
        return result;
    }

}
