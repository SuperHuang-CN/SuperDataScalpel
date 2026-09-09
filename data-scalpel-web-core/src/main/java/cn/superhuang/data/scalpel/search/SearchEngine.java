package cn.superhuang.data.scalpel.search;

import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Common entity-list search entry point.
 *
 * <p>All participating repositories use the same DSL, type conversion,
 * pagination and sort behavior. The optional base specification is for fixed
 * business context such as a parent entity or catalog; it is not an access-control mechanism.</p>
 */
public final class SearchEngine {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 500;
    public static final int MAX_SORT_FIELDS = 5;

    public <T, ID> Page<T> search(
            SearchRequest request,
            Class<T> entityType,
            SearchRepository<T, ID> repository
    ) {
        return search(request, entityType, repository, Specification.unrestricted());
    }

    public <T, ID> Page<T> search(
            SearchRequest request,
            Class<T> entityType,
            SearchRepository<T, ID> repository,
            Specification<T> baseSpecification
    ) {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(baseSpecification, "baseSpecification");
        SearchRequest effectiveRequest = request == null ? SearchRequest.empty() : request;
        Specification<T> userSpecification = toSpecification(SearchDslParser.parse(effectiveRequest.search()), entityType);
        return repository.findAll(baseSpecification.and(userSpecification), pageRequest(effectiveRequest, entityType));
    }

    private <T> Specification<T> toSpecification(SearchDslParser.SearchNode node, Class<T> entityType) {
        if (node == null) {
            return Specification.unrestricted();
        }
        if (node instanceof SearchDslParser.ConditionNode condition) {
            return conditionSpecification(condition, entityType);
        }
        SearchDslParser.LogicalNode logical = (SearchDslParser.LogicalNode) node;
        Specification<T> left = toSpecification(logical.left(), entityType);
        Specification<T> right = toSpecification(logical.right(), entityType);
        return logical.operator() == SearchDslParser.LogicalOperator.AND ? left.and(right) : left.or(right);
    }

    /** Uses the same DSL and forced scope while selecting only the requested scalar projection. */
    public <T, ID, R> Page<R> search(
            SearchRequest request,
            Class<T> entityType,
            SearchRepository<T, ID> repository,
            Specification<T> baseSpecification,
            Class<R> projectionType
    ) {
        Objects.requireNonNull(baseSpecification, "baseSpecification");
        SearchRequest effective = request == null ? SearchRequest.empty() : request;
        Specification<T> specification = baseSpecification.and(
                toSpecification(SearchDslParser.parse(effective.search()), entityType));
        var pageable = pageRequest(effective, entityType);
        return repository.findBy(specification, query -> query.as(projectionType).page(pageable));
    }

    private <T> Specification<T> conditionSpecification(
            SearchDslParser.ConditionNode condition,
            Class<T> entityType
    ) {
        SearchFieldResolver.ResolvedField field = SearchFieldResolver.resolve(entityType, condition.field());
        validateOperation(condition.operator(), field);
        Object value = condition.value() == null ? null : SearchValueConverter.convert(field.name(), field.type(), condition.value());
        return (root, query, builder) -> predicate(root, builder, field, condition.operator(), value);
    }

    private static void validateOperation(
            SearchDslParser.SearchOperator operation,
            SearchFieldResolver.ResolvedField field
    ) {
        if (isTextOperation(operation) && !SearchFieldResolver.isText(field.type())) {
            throw new InvalidSearchRequestException("text operation requires a String field: " + field.name());
        }
        if (isComparison(operation) && !SearchFieldResolver.isComparable(field.type())) {
            throw new InvalidSearchRequestException("comparison requires a numeric or temporal field: " + field.name());
        }
    }

    private static boolean isTextOperation(SearchDslParser.SearchOperator operation) {
        return operation == SearchDslParser.SearchOperator.CONTAINS
                || operation == SearchDslParser.SearchOperator.STARTS_WITH
                || operation == SearchDslParser.SearchOperator.ENDS_WITH;
    }

    private static boolean isComparison(SearchDslParser.SearchOperator operation) {
        return operation == SearchDslParser.SearchOperator.GREATER_THAN
                || operation == SearchDslParser.SearchOperator.GREATER_THAN_OR_EQUAL
                || operation == SearchDslParser.SearchOperator.LESS_THAN
                || operation == SearchDslParser.SearchOperator.LESS_THAN_OR_EQUAL;
    }

    private static Predicate predicate(
            Root<?> root,
            CriteriaBuilder builder,
            SearchFieldResolver.ResolvedField field,
            SearchDslParser.SearchOperator operation,
            Object value
    ) {
        Path<?> path = root.get(field.name());
        return switch (operation) {
            case EQUAL -> builder.equal(path, value);
            case NOT_EQUAL -> builder.notEqual(path, value);
            case GREATER_THAN -> greaterThan(builder, path, value);
            case GREATER_THAN_OR_EQUAL -> greaterThanOrEqualTo(builder, path, value);
            case LESS_THAN -> lessThan(builder, path, value);
            case LESS_THAN_OR_EQUAL -> lessThanOrEqualTo(builder, path, value);
            case CONTAINS -> like(builder, path, value.toString(), "%", "%");
            case STARTS_WITH -> like(builder, path, value.toString(), "", "%");
            case ENDS_WITH -> like(builder, path, value.toString(), "%", "");
            case IS_NULL -> builder.isNull(path);
            case IS_NOT_NULL -> builder.isNotNull(path);
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate greaterThan(CriteriaBuilder builder, Path<?> path, Object value) {
        return builder.greaterThan((Expression) path, (Comparable) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate greaterThanOrEqualTo(CriteriaBuilder builder, Path<?> path, Object value) {
        return builder.greaterThanOrEqualTo((Expression) path, (Comparable) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate lessThan(CriteriaBuilder builder, Path<?> path, Object value) {
        return builder.lessThan((Expression) path, (Comparable) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate lessThanOrEqualTo(CriteriaBuilder builder, Path<?> path, Object value) {
        return builder.lessThanOrEqualTo((Expression) path, (Comparable) value);
    }

    @SuppressWarnings("unchecked")
    private static Predicate like(CriteriaBuilder builder, Path<?> path, String value, String prefix, String suffix) {
        return builder.like((Expression<String>) path, prefix + escapeLikeLiteral(value) + suffix, '\\');
    }

    private static String escapeLikeLiteral(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\\' || current == '%' || current == '_') {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return escaped.toString();
    }

    private static PageRequest pageRequest(SearchRequest request, Class<?> entityType) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();
        if (page < 0) {
            throw new InvalidSearchRequestException("page must not be negative");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new InvalidSearchRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, sort(request.sort(), entityType));
    }

    private static Sort sort(String sort, Class<?> entityType) {
        if (sort == null || sort.isBlank()) {
            requireIdField(entityType);
            return Sort.by(Sort.Order.desc("id"));
        }
        String[] entries = sort.split(",", -1);
        if (entries.length > MAX_SORT_FIELDS) {
            throw new InvalidSearchRequestException("sort supports at most " + MAX_SORT_FIELDS + " fields");
        }
        List<Sort.Order> orders = new ArrayList<>(entries.length + 1);
        boolean hasId = false;
        for (String entry : entries) {
            String item = entry.trim();
            boolean descending = item.startsWith("-");
            String field = descending ? item.substring(1) : item;
            if (field.isBlank()) {
                throw new InvalidSearchRequestException("sort contains a blank field");
            }
            SearchFieldResolver.resolve(entityType, field);
            if ("id".equals(field)) {
                hasId = true;
            }
            orders.add(descending ? Sort.Order.desc(field) : Sort.Order.asc(field));
        }
        if (!hasId) {
            requireIdField(entityType);
            orders.add(orders.getLast().isDescending() ? Sort.Order.desc("id") : Sort.Order.asc("id"));
        }
        return Sort.by(orders);
    }

    private static void requireIdField(Class<?> entityType) {
        SearchFieldResolver.resolve(entityType, "id");
    }
}
