package kr.co.mash_up.nine_tique.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.Generated;
import com.querydsl.core.types.Path;


/**
 * QProductImage_Id is a Querydsl query type for Id
 */
@Generated("com.querydsl.codegen.EmbeddableSerializer")
public class QProductImage_Id extends BeanPath<ProductImage.Id> {

    private static final long serialVersionUID = -782990582L;

    public static final QProductImage_Id id = new QProductImage_Id("id");

    public final NumberPath<Long> imageId = createNumber("imageId", Long.class);

    public final NumberPath<Long> productId = createNumber("productId", Long.class);

    public QProductImage_Id(String variable) {
        super(ProductImage.Id.class, forVariable(variable));
    }

    public QProductImage_Id(Path<? extends ProductImage.Id> path) {
        super(path.getType(), path.getMetadata());
    }

    public QProductImage_Id(PathMetadata metadata) {
        super(ProductImage.Id.class, metadata);
    }

}

