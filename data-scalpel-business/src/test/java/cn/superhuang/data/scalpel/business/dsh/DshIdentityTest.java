package cn.superhuang.data.scalpel.business.dsh;

import cn.superhuang.data.scalpel.business.dsh.config.DshProperties;
import cn.superhuang.data.scalpel.business.dsh.service.*;
import cn.superhuang.data.scalpel.business.dsh.security.DshLoginIdentity;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DshIdentityTest {
    @Test void encryptedCredentialIsRandomizedBoundToOwnerAndFailsClosed() {
        var properties=new DshProperties();properties.setCredentialKey(Base64.getEncoder().encodeToString(new byte[32]));
        var cipher=new DshCredentialCipher(properties);UUID owner=UUID.randomUUID();
        String first=cipher.encrypt(owner,"private-secret"), second=cipher.encrypt(owner,"private-secret");
        assertNotEquals(first,second);assertEquals("private-secret",cipher.decrypt(owner,first));
        assertFalse(first.contains("private-secret"));
        assertThrows(CodedProblemException.class,()->cipher.decrypt(UUID.randomUUID(),first));
        assertThrows(CodedProblemException.class,()->cipher.decrypt(owner,first.substring(0,first.length()-5)));
        properties.setCredentialKey("");assertThrows(CodedProblemException.class,()->cipher.decrypt(owner,first));
    }
    @Test void signedUuidCannotFallBackToRecreatedUsernameAndUserStateIsReadAgain() {
        var users=mock(SystemUserRepository.class);var service=new DshIdentityService(users);
        var auth=new UsernamePasswordAuthenticationToken("same-name","unused",List.of());
        assertEquals("DSH_RELOGIN_REQUIRED",assertThrows(CodedProblemException.class,()->service.require(auth)).code());
        UUID old=UUID.randomUUID();auth.setDetails(new DshLoginIdentity(old,Instant.now().plusSeconds(60)));
        when(users.findById(old)).thenReturn(Optional.empty());
        assertThrows(CodedProblemException.class,()->service.require(auth));
        var user=mock(SystemUser.class);when(user.isEnabled()).thenReturn(true);when(users.findById(old)).thenReturn(Optional.of(user));
        assertEquals(old,service.require(auth).userId());when(user.isEnabled()).thenReturn(false);
        assertThrows(CodedProblemException.class,()->service.require(auth));
    }
    @Test void optionAnswerOmitsAbsentCustomTextForNativeQuestionContract() {
        var answer=new cn.superhuang.data.scalpel.business.dsh.web.request.DshQuestionAnswerRequest("color",List.of("blue"),null);
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        assertFalse(mapper.readTree(mapper.writeValueAsString(answer)).has("custom"));
    }
}
