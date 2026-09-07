package com.civileng.marketplace.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Read model of the service catalogue (booking-service's ServiceCategory) — backs the
 * ServicesPage grid's search box and category filtering.
 *
 * <p>Per-tenant index, on the same terms as {@link ProfileDocument}: a tenant's catalogue comes
 * from its own booking-service schema, so it must not share an index with another tenant's.
 */
@Document(indexName = "#{@tenantIndex.services()}", createIndex = false)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Integer)
    private Integer sortOrder;

    @Field(type = FieldType.Boolean)
    private Boolean active;
}
