package com.jz.logger.core.annotation;

import com.jz.logger.core.TraceInfo;
import com.jz.logger.core.converters.Converter;
import com.jz.logger.core.converters.DefaultConverter;
import com.jz.logger.core.handler.DefaultFieldHandler;
import com.jz.logger.core.handler.FieldHandler;
import com.jz.logger.core.matcher.DefaultMatcher;
import com.jz.logger.core.matcher.Matcher;

import java.lang.annotation.*;
import java.util.Collection;

/**
 * @author jz
 */
@Inherited
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Trace {

    /**
     * 该 trace 的 标签
     */
    String tag() default "";

    /**
     * 该属性值应为spel表达式
     * 该属性用于提取字段中指定字段的值
     */
    String targetValue() default "";

    /**
     * 指定后将在指定 {@link Logger#topic} 时生效
     */
    String[] topic() default {};

    /**
     * 指定后将在指定 {@link Logger#resourceType} 时生效
     */
    int[] resourceType() default {};

    /**
     * 指定后将在指定 {@link Logger#operationType()} 时生效
     */
    int[] operationType() default {};

    /**
     * 转换器，用于对 {@link TraceInfo#oldValue} 和 {@link TraceInfo#newValue} 的转化
     */
    Class<? extends Converter> converter() default DefaultConverter.class;

    /**
     * 若为 true 将对该字段中标注trace注解的字段进行解析
     */
    boolean permeate() default false;

    /**
     * 当标注在 {@link Collection} 类型的字段上，且 {@link #permeate()} 为 true 时生效，用于匹配新旧集合中元素是否为同一个
     */
    Class<? extends Matcher> collElementMatcher() default DefaultMatcher.class;

    /**
     * 字段处理器，
     * 若自定义该处理器且 {@link #permeate()} 为 false，其余属性将全部失效；
     * 若自定义该处理器且 {@link #permeate()} 为 true，仍会对该字段执行 {@link #permeate()} 相关逻辑
     */
    Class<? extends FieldHandler> fieldHandler() default DefaultFieldHandler.class;

    int order() default 0;

}
