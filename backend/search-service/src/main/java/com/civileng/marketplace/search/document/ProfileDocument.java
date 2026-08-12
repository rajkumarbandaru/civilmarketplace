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
 * Denormalised read model of a supply-side profile, assembled from auth-service (identity),
 * user-service (profile) and review-service (reputation). Never written to by anything except
 * the reindex pipeline — the owning services remain the source of truth.
 */
@Document(indexName = "profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Keyword)
    private String role;

    @Field(type = FieldType.Text)
    private String city;

    @Field(type = FieldType.Keyword)
    private String cityKeyword;

    @Field(type = FieldType.Text)
    private String state;

    @Field(type = FieldType.Text)
    private String bio;

    @Field(type = FieldType.Text)
    private String languages;

    @Field(type = FieldType.Integer)
    private Integer experienceYears;

    @Field(type = FieldType.Double)
    private Double hourlyRate;

    @Field(type = FieldType.Double)
    private Double averageRating;

    @Field(type = FieldType.Integer)
    private Integer totalReviews;

    @Field(type = FieldType.Boolean)
    private Boolean isVerified;

    @Field(type = FieldType.Boolean)
    private Boolean isAvailable;
}
