package cn.superhuang.data.scalpel.business.ontology;

import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.DataModelService;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectType;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition;
import cn.superhuang.data.scalpel.business.ontology.repository.BusinessObjectTypeReferenceRepository;
import cn.superhuang.data.scalpel.business.ontology.repository.BusinessObjectTypeRepository;
import cn.superhuang.data.scalpel.business.ontology.service.BusinessObjectTypeService;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessObjectReadBatchTest {
    @Test
    void pageSharesDefinitionParsingAndLoadsOnlyRelevantModelsOnce() {
        var types = mock(BusinessObjectTypeRepository.class);
        var models = mock(DataModelRepository.class);
        var fields = mock(DataModelFieldRepository.class);
        var mapper = spy(new ObjectMapper());
        UUID sharedModel = UUID.randomUUID();
        var first = type("first", sharedModel, mapper);
        var second = type("second", sharedModel, mapper);
        var outsidePage = type("outside", UUID.randomUUID(), mapper);
        when(types.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(types.findAll()).thenReturn(List.of(first, second, outsidePage));
        var service = new BusinessObjectTypeService(types, mock(BusinessObjectTypeReferenceRepository.class),
                models, fields, mock(DirectoryService.class), mock(DataModelService.class), new SearchEngine(), mapper);

        var result = service.search(SearchRequest.empty());

        assertThat(result.content()).extracting(item -> item.code()).containsExactly("first", "second");
        verify(models).findAllById(Set.of(sharedModel));
        verify(fields).findAllByModelIdInOrderByModelAndSort(Set.of(sharedModel));
        verifyNoMoreInteractions(models, fields);
        for (var type : List.of(first, second, outsidePage)) {
            verify(mapper).readValue(type.getDefinition(), BusinessObjectTypeDefinition.class);
        }
    }

    private static BusinessObjectType type(String code, UUID modelId, ObjectMapper mapper) {
        var definition = new BusinessObjectTypeDefinition(
                new BusinessObjectTypeDefinition.MainSource(modelId, UUID.randomUUID(), null, List.of()),
                List.of(), List.of(), List.of(), List.of(), List.of());
        var type = BusinessObjectType.create(code, mapper.writeValueAsString(definition));
        type.update(code, null, null, null);
        ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
        return type;
    }
}
