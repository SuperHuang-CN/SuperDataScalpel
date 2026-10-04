package cn.superhuang.data.scalpel.business.quality.service;
import cn.superhuang.data.scalpel.business.quality.repository.ModelQualityStatisticsRepository;
import cn.superhuang.data.scalpel.business.quality.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class ModelQualityStatisticsService {
    private final ModelQualityStatisticsRepository repository;
    public ModelQualityStatisticsService(ModelQualityStatisticsRepository repository) { this.repository=repository; }
    public ModelQualityStatisticsResponse statistics() {
        long passed=0, failed=0, none=0;
        Instant oldest=null, newest=null;
        for (var row : repository.groups()) {
            long count=((Number)row[1]).longValue();
            if (row[0]==QualityConclusion.PASSED) passed+=count;
            else if (row[0]==QualityConclusion.FAILED) failed+=count;
            else none+=count;
            Instant first=(Instant)row[2], last=(Instant)row[3];
            if (first!=null && (oldest==null || first.isBefore(oldest))) oldest=first;
            if (last!=null && (newest==null || last.isAfter(newest))) newest=last;
        }
        return new ModelQualityStatisticsResponse(Instant.now(),passed+failed+none,passed,failed,none,repository.latestFailed(),oldest,newest);
    }
    public PageResponse<ModelQualityStatisticsItem> items(String result, int page, int size) {
        if (!List.of("ALL","PASSED","FAILED","NONE","EXECUTION_FAILED").contains(result)
            || page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"质量分类或分页参数无效");
        long total=repository.count(result);
        var rows=repository.items(result,PageRequest.of(page,size));
        return new PageResponse<>(rows,total,(int)Math.ceil((double)total/size),page,size);
    }
}
