package kr.co.mash_up.nine_tique.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import kr.co.mash_up.nine_tique.domain.*;
import kr.co.mash_up.nine_tique.exception.IdNotFoundException;
import kr.co.mash_up.nine_tique.repository.CategoryRepository;
import kr.co.mash_up.nine_tique.repository.ImageRepository;
import kr.co.mash_up.nine_tique.repository.ProductRepository;
import kr.co.mash_up.nine_tique.repository.PromotionRepository;
import kr.co.mash_up.nine_tique.repository.SellerRepository;
import kr.co.mash_up.nine_tique.repository.UserRepository;
import kr.co.mash_up.nine_tique.repository.ZzimRepository;
import kr.co.mash_up.nine_tique.service.PromotionService;
import kr.co.mash_up.nine_tique.util.FileUtil;
import kr.co.mash_up.nine_tique.web.dto.BrandDto;
import kr.co.mash_up.nine_tique.web.dto.ImageDto;
import kr.co.mash_up.nine_tique.web.dto.ProductDto;
import kr.co.mash_up.nine_tique.web.dto.PromotionDto;
import kr.co.mash_up.nine_tique.web.vo.DataListRequestVO;
import kr.co.mash_up.nine_tique.web.vo.ProductListRequestVO;
import kr.co.mash_up.nine_tique.web.vo.ProductRequestVO;
import kr.co.mash_up.nine_tique.web.vo.PromotionRequestVO;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by ethankim on 2017. 7. 9..
 */
@Service
@Slf4j
public class PromotionServiceImpl implements PromotionService {

    // Todo: 2017.07.19 프로모션 상태 변경 로직 추가
    // Todo: 2017.07.19 상태에 변경에 따른 히스토리 로직 추가

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ZzimRepository zzimRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Transactional
    @Override
    public void addPromotion(Long registerUserId, PromotionRequestVO promotionVO) {
        Optional<User> userOp = userRepository.findOneById(registerUserId);
        User register = userOp.orElseThrow(() -> new IdNotFoundException("addPromotion -> user not found"));

        Promotion promotion = new Promotion();
        promotion.setName(promotionVO.getName());
        promotion.setDescription(promotionVO.getDescription());
        promotion.setPriority(promotionVO.getPriority());
        promotion.setStartAt(promotionVO.getStartAt().toLocalDateTime());
        promotion.setEndAt(promotionVO.getEndAt().toLocalDateTime());
        promotion.setRegister(register.getName());
        promotion.setStatus(Promotion.Status.SAVED);
        promotion.setPromotionImages(new ArrayList<>());
        promotion.setPromotionProducts(new ArrayList<>());
        promotionRepository.save(promotion);

        promotionVO.getProducts()
                .forEach(productVO -> {
                    Optional<Product> productOp = productRepository.findOneByProductId(productVO.getId());
                    Product product = productOp.orElseThrow(() -> new IdNotFoundException("addPromotion -> product not found"));
                    promotion.addProduct(new PromotionProduct(promotion, product));
                });

        // 현재 프로모션엔 대표 이미지 1개지만 확장성을 고려해 Collection으로 구현
        promotionVO.getImages()
                .forEach(imageDto -> {
                    Optional<Image> imageOptional = imageRepository.findByFileName(FileUtil.getFileName(imageDto.getUrl()));
                    Image image = imageOptional.orElseThrow(() -> new IdNotFoundException("addPromotion -> image not found"));

                    promotion.addImage(new PromotionImage(promotion, image));

                    // file move tmp dir to promotion id dir
                    FileUtil.moveFile(FileUtil.getImageUploadTempPath() + "/" + image.getFileName(),
                            FileUtil.getImageUploadPath(ImageType.PROMOTION, promotion.getId()));
                });
    }

    @Transactional
    @Override
    public void modifyPromotion(Long promotionId, PromotionRequestVO promotionVO) {
        Optional<Promotion> promotionOp = promotionRepository.findOneByPromotionId(promotionId);
        Promotion promotion = promotionOp.orElseThrow(() -> new IdNotFoundException("modifyPromotion -> promotion not found"));

        promotion.setName(promotionVO.getName());
        promotion.setDescription(promotionVO.getDescription());
        promotion.setPriority(promotionVO.getPriority());
        promotion.setStartAt(promotionVO.getStartAt().toLocalDateTime());
        promotion.setEndAt(promotionVO.getEndAt().toLocalDateTime());

        List<PromotionProduct> oldProducts = promotion.getPromotionProducts();
        List<ProductRequestVO> updateProducts = promotionVO.getProducts();

        // 프로모션 상품 삭제
        Map<Long, ProductRequestVO> productMapToUpdate = updateProducts.stream()
                .collect(Collectors.toMap(ProductRequestVO::getId, productRequestVO -> productRequestVO));
        oldProducts.removeIf(promotionProduct -> productMapToUpdate.get(promotionProduct.getProductId()) == null);

        // 프로모션 상품 추가
        Map<Long, PromotionProduct> productMapToOld = oldProducts.stream()
                .collect(Collectors.toMap(PromotionProduct::getProductId, promotionProduct -> promotionProduct));
        updateProducts.forEach(productVO -> {
            if (productMapToOld.get(productVO.getId()) == null) {
                Optional<Product> productOp = productRepository.findOneByProductId(productVO.getId());
                Product product = productOp.orElseThrow(() -> new IdNotFoundException("modifyPromotion -> product not found"));
                promotion.addProduct(new PromotionProduct(promotion, product));
            }
        });

        List<PromotionImage> oldImages = promotion.getPromotionImages();
        List<ImageDto> updateImages = promotionVO.getImages();

        // 프로모션 이미지 삭제
        Map<String, ImageDto> imageMapToUpdate = updateImages.stream()
                .collect(Collectors.toMap(image -> FileUtil.getFileName(image.getUrl()), image -> image));

        Iterator<PromotionImage> imageIterator = oldImages.iterator();
        while (imageIterator.hasNext()) {
            PromotionImage oldImage = imageIterator.next();

            if (imageMapToUpdate.get(oldImage.getImage().getFileName()) == null) {
                // Todo: 2017.07.19 바로 파일 삭제할지 선택
                FileUtil.moveFile(FileUtil.getImageUploadPath(ImageType.PROMOTION, promotionId) + "/" + oldImage.getImage().getFileName()
                        , FileUtil.getImageUploadTempPath());
                imageIterator.remove();
            }
        }

        // 프로모션 이미지 추가
        updateImages.forEach(imageDto -> {
            Optional<Image> imageOptional = imageRepository.findByFileName(FileUtil.getFileName(imageDto.getUrl()));
            Image image = imageOptional.orElseThrow(() -> new IdNotFoundException("modifyPromotion -> image not found"));

            promotion.addImage(new PromotionImage(promotion, image));

            // file move tmp dir to promotion id dir
            FileUtil.moveFile(FileUtil.getImageUploadTempPath() + "/" + image.getFileName(),
                    FileUtil.getImageUploadPath(ImageType.PROMOTION, promotionId));
        });

        promotionRepository.save(promotion);
    }

    @Transactional
    @Override
    public void removePromotion(Long promotionId) {
        Optional<Promotion> promotionOp = promotionRepository.findOneByPromotionId(promotionId);
        promotionOp.orElseThrow(() -> new IdNotFoundException("removePromotion -> promotion not found"));

        // dir move tmp dir to promotion id dir
        FileUtil.moveFile(FileUtil.getImageUploadPath(ImageType.PROMOTION, promotionId), FileUtil.getImageUploadTempPath());
        promotionRepository.delete(promotionId);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<PromotionDto> readPromotions(DataListRequestVO requestVO) {
        Pageable pageable = requestVO.getPageable();

        Page<Promotion> promotionPage = promotionRepository.findPromotions(pageable);

        List<PromotionDto> promotions = promotionPage.getContent().stream()
                .map(promotion -> {
                    List<ImageDto> images = promotion.getPromotionImages().stream()
                            .map(PromotionImage::getImage)
                            .map(image ->
                                    new ImageDto.Builder()
                                            .url(FileUtil.getImageUrl(ImageType.PROMOTION, promotion.getId(), image.getFileName()))
                                            .build())
                            .collect(Collectors.toList());

                    return new PromotionDto.Builder()
                            .id(promotion.getId())
                            .name(promotion.getName())
                            .images(images)
                            .build();
                }).collect(Collectors.toList());

        Pageable resultPageable = new PageRequest(promotionPage.getNumber(), promotionPage.getSize());

        return new PageImpl<>(promotions, resultPageable, promotionPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    @Override
    public PromotionDto readPromotion(Long promotionId) {
        Optional<Promotion> promotionOptional = promotionRepository.findOneByPromotionId(promotionId);
        Promotion promotion = promotionOptional.orElseThrow(() -> new IdNotFoundException("readPromotion -> promotion not found"));

        List<ImageDto> images = promotion.getPromotionImages().stream()
                .map(PromotionImage::getImage)
                .map(image ->
                        new ImageDto.Builder()
                                .url(FileUtil.getImageUrl(ImageType.PROMOTION, promotionId, image.getFileName()))
                                .build())
                .collect(Collectors.toList());

        return new PromotionDto.Builder()
                .id(promotion.getId())
                .name(promotion.getName())
                .images(images)
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ProductDto> readPromotionProducts(Long promotionId, Long userId, ProductListRequestVO requestVO) {
        Pageable pageable = requestVO.getPageable();
        String mainCategory = requestVO.getMainCategory().toUpperCase();
        String subCategory = requestVO.getSubCategory().toUpperCase();

        Page<Product> productPage;

        if (mainCategory.equalsIgnoreCase("NEW")) {
            productPage = productRepository.findPromotionProducts(promotionId, pageable);
        } else {
            if (subCategory.equalsIgnoreCase("ALL")) {
                productPage = productRepository.findPromotionProductsByMainCategory(promotionId, mainCategory, pageable);
            } else {
                Optional<Category> categoryOptional = categoryRepository.findOneByMainAndSub(mainCategory, subCategory);
                Category category = categoryOptional.orElseThrow(() -> new IdNotFoundException("find product by category -> category not found"));

                log.debug(category.getMain() + " " + category.getSub() + " " + category.getId());

                productPage = productRepository.findPromotionProductsByCategory(promotionId, category, pageable);
            }
        }

        List<ZzimProduct> zzimProducts = zzimRepository.findZzimProducts(userId);
        List<SellerProduct> sellerProducts = sellerRepository.findSellerProducts(userId);

        List<ProductDto> productDtos = productPage.getContent().stream()
                .map(product -> {

                    List<ImageDto> productImages = product.getProductImages().stream()
                            .map(ProductImage::getImage)
                            .sorted(Comparator.comparing(Image::getId))
                            .limit(1)
                            .map(image ->
                                    new ImageDto.Builder()
                                            .url(FileUtil.getImageUrl(ImageType.PRODUCT, product.getId(), image.getFileName()))
                                            .build())
                            .collect(Collectors.toList());

                    boolean isZzim = product.checkProductZzim(zzimProducts);
                    boolean isSeller = product.checkSeller(sellerProducts);

                    return new ProductDto.Builder()
                            .withId(product.getId())
                            .withName(product.getName())
                            .withBrand(BrandDto.fromBrand(product.getBrand()))
                            .withSize(product.getSize())
                            .withPrice(product.getPrice())
//                            .withDescription(product.getDescription())
                            .withStatus(product.getStatus())
//                            .withMainCategory(product.getCategory().getMain())
//                            .withSubCategory(product.getCategory().getSub())
//                            .withShop(shopDto)
                            .withImages(productImages)
                            .withZzimStatus(isZzim)
//                            .withCreatedAt(product.getCreatedTimestamp())
//                            .withUpdatedAt(product.getUpdatedTimestamp())
                            .withSeller(isSeller)
                            .build();
                })
                .collect(Collectors.toList());

        Pageable resultPageable = new PageRequest(productPage.getNumber(), productPage.getSize());

        return new PageImpl<>(productDtos, resultPageable, productPage.getTotalElements());
    }
}
