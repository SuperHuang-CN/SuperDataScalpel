package cn.superhuang.data.scalpel.business.dsh;

import cn.superhuang.data.scalpel.business.dsh.service.DshService;
import cn.superhuang.data.scalpel.business.dsh.web.request.UpdateDshSessionRequest;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DshSessionManagementTest {
    private DshService service() {
        var value=spy(new DshService(null,null,null,null));
        doReturn(null).when(value).get(any(),anyString());
        return value;
    }
    @Test void titleIsNormalizedAndValidated() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertEquals("标题",new UpdateDshSessionRequest("  标题  ").title());
            assertFalse(validator.validate(new UpdateDshSessionRequest("   ")).isEmpty());
            assertFalse(validator.validate(new UpdateDshSessionRequest("a".repeat(101))).isEmpty());
            assertTrue(validator.validate(new UpdateDshSessionRequest("正常标题")).isEmpty());
        }
    }
    @Test void filtersRemainEncodedDataAndCannotInjectBridgeParameters() {
        var service=service();var owner=UUID.randomUUID();
        service.list(owner,20,20,"a&archived=true",false);
        verify(service).get(owner,"/sessions?offset=20&limit=20&query=a%26archived%3Dtrue&archived=false");
    }
    @Test void cursorAndOffsetModesAreExplicitAndCompatible() {
        var service=service();var owner=UUID.randomUUID();var id=UUID.randomUUID();
        service.messages(owner,id,null,50,null,null);
        verify(service).get(owner,"/sessions/"+id+"/messages?offset=0&limit=50");
        service.messages(owner,id,null,50,"cursor",12L);
        verify(service).get(owner,"/sessions/"+id+"/messages?mode=cursor&limit=50&beforeSeq=12");
        assertThrows(CodedProblemException.class,()->service.messages(owner,id,0,50,"cursor",null));
        assertThrows(CodedProblemException.class,()->service.messages(owner,id,null,50,null,12L));
    }
}
