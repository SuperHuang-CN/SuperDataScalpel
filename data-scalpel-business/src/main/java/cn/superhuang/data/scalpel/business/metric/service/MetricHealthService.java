package cn.superhuang.data.scalpel.business.metric.service;

import cn.superhuang.data.scalpel.business.metric.domain.*;
import cn.superhuang.data.scalpel.business.metric.repository.*;
import cn.superhuang.data.scalpel.business.metric.web.response.*;
import cn.superhuang.data.scalpel.business.model.domain.*;
import cn.superhuang.data.scalpel.business.model.repository.*;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Management-database diagnostics only. Never opens a physical data-source connection. */
@Service
public class MetricHealthService {
    private final DataModelRepository models;
    private final DataModelFieldRepository fields;
    private final DataMetricRepository metrics;
    private final MetricReleaseRepository releases;
    private final DataServiceRepository services;
    private static final Set<PlatformDataType> NUMERIC=EnumSet.of(PlatformDataType.BYTE,PlatformDataType.SHORT,PlatformDataType.INTEGER,PlatformDataType.LONG,PlatformDataType.FLOAT,PlatformDataType.DOUBLE,PlatformDataType.DECIMAL);
    public MetricHealthService(DataModelRepository models,DataModelFieldRepository fields,DataMetricRepository metrics,MetricReleaseRepository releases,DataServiceRepository services) {
        this.models=models;this.fields=fields;this.metrics=metrics;this.releases=releases;this.services=services;
    }
    public List<MetricReferenceSnapshot> references(MetricDefinition d) {
        List<MetricReferenceSnapshot> result=new ArrayList<>();
        var b=d.binding();
        if(b!=null) {
            add(result,"binding.modelId",MetricDefinition.ResourceKind.MODEL,b.modelId(),null,null,true);
            add(result,"binding.valueFieldId",MetricDefinition.ResourceKind.MODEL_FIELD,b.valueFieldId(),b.modelId(),null,true);
            add(result,"binding.periodFieldId",MetricDefinition.ResourceKind.MODEL_FIELD,b.periodFieldId(),b.modelId(),null,true);
            for(int i=0;i<b.dimensions().size();i++)add(result,"binding.dimensions["+i+"]",MetricDefinition.ResourceKind.MODEL_FIELD,b.dimensions().get(i).fieldId(),b.modelId(),null,true);
            for(int i=0;i<b.supportingFieldIds().size();i++)add(result,"binding.supportingFieldIds["+i+"]",MetricDefinition.ResourceKind.MODEL_FIELD,b.supportingFieldIds().get(i),b.modelId(),null,true);
            for(int i=0;i<b.fixedFilters().size();i++)add(result,"binding.fixedFilters["+i+"]",MetricDefinition.ResourceKind.MODEL_FIELD,b.fixedFilters().get(i).fieldId(),b.modelId(),null,true);
        }
        for(int i=0;i<d.references().size();i++) {
            var r=d.references().get(i);add(result,"references["+i+"]",r.resourceKind(),r.resourceId(),r.parentModelId(),r.targetVersion(),false);
        }
        return List.copyOf(result);
    }
    private void add(List<MetricReferenceSnapshot> out,String path,MetricDefinition.ResourceKind kind,UUID id,UUID parent,Integer version,boolean result) {
        if(id==null)return;
        String name=null,code=null,status=null,contract=null;
        switch(kind) {
            case MODEL -> { var m=models.findById(id).orElse(null);if(m!=null){name=m.getName();code=m.getCode();status=m.getStatus().name();contract=String.join("|",m.getStorageDataSourceId().toString(),Objects.toString(m.getCatalogName(),""),Objects.toString(m.getSchemaName(),""),m.getPhysicalTableName(),m.getPhysicalTableMode().name());} }
            case MODEL_FIELD -> {var f=fields.findById(id).orElse(null);if(f!=null&&(parent==null&&!result||f.getModelId().equals(parent))){parent=f.getModelId();name=f.getName();code=f.getCode();status="EXISTS";contract=f.getCode()+"|"+f.getFieldType()+"|"+f.getLength()+"|"+f.getPrecision()+"|"+f.getScale()+"|"+f.getGeometry();}}
            case METRIC -> {var m=metrics.findById(id).orElse(null);if(m!=null&&(version==null||releases.findByMetricIdAndVersion(id,version).isPresent())){name=m.getName();code=m.getCode();status=m.getStatus().name();contract=id+":"+version;}}
            case DATA_SERVICE -> {var s=services.findById(id).orElse(null);if(s!=null){name=s.getName();status=s.getStatus().name();contract=id.toString();}}
        }
        out.add(new MetricReferenceSnapshot(path,kind,id,parent,version,name,code,status,contract,result));
    }
    public MetricHealthResponse inspect(DataMetric metric,MetricDefinition d,List<MetricReferenceSnapshot> refs,List<MetricReferenceSnapshot> published) {
        List<MetricIssueResponse> issues=new ArrayList<>();
        required(issues,"ownerName",metric.getOwnerName());required(issues,"businessMeaning",d.businessMeaning());required(issues,"calculation",d.calculation());
        required(issues,"statisticalScope",d.statisticalScope());required(issues,"unit",d.unit());required(issues,"grainDescription",d.grainDescription());
        required(issues,"nullHandling",d.nullHandling());required(issues,"aggregationDescription",d.aggregationDescription());
        if(d.statisticalPeriod()!=MetricDefinition.Period.NONE)required(issues,"timeDescription",d.timeDescription());
        for(var r:refs)if(r.contract()==null)issue(issues,"METRIC_REFERENCE_MISSING",r.path(),"引用资源已失效或字段不属于所选模型",r.resultBinding());
        var b=d.binding();
        if(b!=null) {
            if(b.modelId()==null)issue(issues,"METRIC_BINDING_INCOMPLETE","binding.modelId","请选择结果模型",true);
            if(b.valueFieldId()==null)issue(issues,"METRIC_BINDING_INCOMPLETE","binding.valueFieldId","请选择指标值字段",true);
            if(b.modelId()!=null){var m=models.findById(b.modelId()).orElse(null);if(m!=null&&m.getStatus()!=DataModelStatus.PUBLISHED)issue(issues,"METRIC_MODEL_NOT_PUBLISHED","binding.modelId","结果模型必须处于已发布状态",true);}
            if(b.valueFieldId()!=null){var f=fields.findById(b.valueFieldId()).orElse(null);if(f!=null&&!NUMERIC.contains(f.getFieldType()))issue(issues,"METRIC_VALUE_TYPE_INVALID","binding.valueFieldId","指标值字段必须是数值类型",true);}
            if(d.statisticalPeriod()!=MetricDefinition.Period.NONE&&b.periodFieldId()==null)issue(issues,"METRIC_BINDING_INCOMPLETE","binding.periodFieldId","时间型指标需要时间字段",true);
            if(b.periodFieldId()!=null){var f=fields.findById(b.periodFieldId()).orElse(null);if(f!=null){
                if(!Set.of(PlatformDataType.STRING,PlatformDataType.DATE,PlatformDataType.TIMESTAMP,PlatformDataType.TIMESTAMP_NTZ).contains(f.getFieldType()))issue(issues,"METRIC_TIME_TYPE_INVALID","binding.periodFieldId","时间字段类型不支持",true);
                if(f.getFieldType()==PlatformDataType.STRING){required(issues,"periodFormat",d.periodFormat());if(d.periodFormat()!=null&&!d.periodFormat().isBlank())try{DateTimeFormatter.ofPattern(d.periodFormat());}catch(IllegalArgumentException e){issue(issues,"METRIC_TIME_FORMAT_INVALID","periodFormat","时间格式无效",true);}}
            }}
            Set<String> keys=new HashSet<>();Set<UUID> dimensionFields=new HashSet<>();
            for(int i=0;i<b.dimensions().size();i++){var dim=b.dimensions().get(i);String path="binding.dimensions["+i+"]";
                if(dim.key()==null||!dim.key().matches("[a-z][a-z0-9_]{0,63}")||!keys.add(dim.key())||dim.name()==null||dim.name().isBlank()||dim.fieldId()==null||!dimensionFields.add(dim.fieldId()))issue(issues,"METRIC_DIMENSION_INVALID",path,"维度需要唯一编码、名称及不重复的字段",true);
                if(dim.fieldId()!=null){var f=fields.findById(dim.fieldId()).orElse(null);if(f!=null&&(f.getFieldType()==PlatformDataType.BINARY||f.getFieldType()==PlatformDataType.GEOMETRY))issue(issues,"METRIC_DIMENSION_INVALID",path,"维度字段须使用标量类型",true);}
            }
            for(int i=0;i<b.fixedFilters().size();i++){var filter=b.fixedFilters().get(i);String path="binding.fixedFilters["+i+"]";
                if(filter.fieldId()==null||filter.operator()==null){issue(issues,"METRIC_FILTER_INVALID",path,"固定条件需要字段和运算符",true);continue;}
                var f=fields.findById(filter.fieldId()).orElse(null);if(f!=null&&!validFilter(f,filter))issue(issues,"METRIC_FILTER_INVALID",path,"固定条件值与字段类型不匹配",true);
            }
        }
        if(published!=null)for(var before:published)if(before.resultBinding()) {
            var now=refs.stream().filter(r->r.path().equals(before.path())&&r.resourceId().equals(before.resourceId())).findFirst().orElse(null);
            if(now==null||!Objects.equals(before.contract(),now.contract()))issue(issues,"METRIC_RESULT_STRUCTURE_CHANGED",before.path(),"已发布绑定的字段或物理位置发生变化，请修订并重新发布",true);
        }
        boolean valid=issues.stream().noneMatch(MetricIssueResponse::blocking);
        boolean bindingInvalid=issues.stream().anyMatch(i->i.blocking()&&(i.path().startsWith("binding")||i.code().equals("METRIC_RESULT_STRUCTURE_CHANGED")||i.path().equals("periodFormat")));
        return new MetricHealthResponse(valid,b==null?"UNBOUND":bindingInvalid?"INVALID":"VALID",List.copyOf(issues));
    }
    private static boolean validFilter(DataModelField field,MetricDefinition.FixedFilter filter) {
        var type=field.getFieldType();if(type==PlatformDataType.GEOMETRY||type==PlatformDataType.BINARY)return false;
        if(filter.operator()==MetricDefinition.Operator.IS_NULL||filter.operator()==MetricDefinition.Operator.IS_NOT_NULL)return filter.value()==null||filter.value().isEmpty();
        if(filter.value()==null)return false;
        try {
            String v=filter.value();
            switch(type){
                case BYTE -> Byte.parseByte(v);case SHORT -> Short.parseShort(v);case INTEGER -> Integer.parseInt(v);case LONG -> Long.parseLong(v);
                case FLOAT -> {if(!Float.isFinite(Float.parseFloat(v)))return false;}case DOUBLE -> {if(!Double.isFinite(Double.parseDouble(v)))return false;}
                case DECIMAL -> {
                    var n = new BigDecimal(v).stripTrailingZeros();
                    if (field.getScale() != null && n.scale() > field.getScale()) return false;
                    int integerDigits = Math.max(0, n.precision() - n.scale());
                    if (field.getPrecision() != null && integerDigits + (field.getScale() == null ? Math.max(0, n.scale()) : field.getScale()) > field.getPrecision()) return false;
                }
                case BOOLEAN -> {return (v.equals("true")||v.equals("false"))&&(filter.operator()==MetricDefinition.Operator.EQ||filter.operator()==MetricDefinition.Operator.NE);}
                case DATE -> LocalDate.parse(v);case TIMESTAMP -> OffsetDateTime.parse(v);case TIMESTAMP_NTZ -> LocalDateTime.parse(v);
                case STRING -> {if(field.getLength()!=null&&v.length()>field.getLength())return false;}
                default -> {return false;}
            }
            return true;
        }catch(IllegalArgumentException|java.time.DateTimeException e){return false;}
    }
    private static void required(List<MetricIssueResponse> issues,String path,String value){if(value==null||value.isBlank())issue(issues,"METRIC_DEFINITION_INCOMPLETE",path,"发布前请填写："+requiredLabel(path),true);}
    private static String requiredLabel(String path) {
        return switch (path) {
            case "ownerName" -> "业务负责人";
            case "businessMeaning" -> "业务含义";
            case "calculation" -> "计算口径";
            case "statisticalScope" -> "统计范围";
            case "unit" -> "单位";
            case "grainDescription" -> "结果粒度";
            case "nullHandling" -> "空值与零值规则";
            case "aggregationDescription" -> "汇总说明";
            case "timeDescription" -> "时间口径";
            case "periodFormat" -> "字符串时间格式";
            default -> path;
        };
    }
    private static void issue(List<MetricIssueResponse> issues,String code,String path,String message,boolean blocking){issues.add(new MetricIssueResponse(code,path,message,blocking));}
}
