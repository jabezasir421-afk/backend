package com.bluecollar.search.service;

import com.bluecollar.availability.service.AvailabilityService;
import com.bluecollar.search.dto.SearchFiltersApplied;
import com.bluecollar.search.dto.WorkerSearchResponse;
import com.bluecollar.search.dto.WorkerSearchResultResponse;
import com.bluecollar.search.entity.WorkerServiceArea;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.storage.service.StoredFileService;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerSearchServiceImpl implements WorkerSearchService {

    private final WorkerRepository workerRepository;
    private final StoredFileService storedFileService;
    private final AvailabilityService availabilityService;

    @Override
    @Cacheable(
            value = "worker-search",
            key = "T(java.util.Objects).hash(#city, #state, #categoryId, #skillIds, #matchAnySkill, #minRating, #available, #availableOn, #minExperience, #maxExperience, #minPrice, #maxPrice, #verified, #sort, #direction, #pageable.pageNumber, #pageable.pageSize)"
    )
    public WorkerSearchResponse searchWorkers(
            String city,
            String state,
            UUID categoryId,
            List<UUID> skillIds,
            boolean matchAnySkill,
            BigDecimal minRating,
            Boolean available,
            LocalDate availableOn,
            Integer minExperience,
            Integer maxExperience,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean verified,
            String sort,
            String direction,
            Pageable pageable
    ) {
        boolean verifiedFilter = verified == null || verified;
        Specification<Worker> spec = buildSpecification(
                city, state, categoryId, skillIds, matchAnySkill, minRating,
                available, minExperience, maxExperience, minPrice, maxPrice, verifiedFilter
        );

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 50),
                resolveSort(sort, direction)
        );

        Page<Worker> page = workerRepository.findAll(spec, sortedPageable);
        List<WorkerSearchResultResponse> results = page.getContent().stream()
                .filter(worker -> availableOn == null || availabilityService.isAvailableOnDate(worker.getId(), availableOn))
                .map(this::toSearchResult)
                .toList();

        SearchFiltersApplied filters = new SearchFiltersApplied(
                city, categoryId, skillIds, minRating, available, availableOn,
                minExperience, maxExperience, minPrice, maxPrice
        );

        return new WorkerSearchResponse(
                results,
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                filters
        );
    }

    private Specification<Worker> buildSpecification(
            String city,
            String state,
            UUID categoryId,
            List<UUID> skillIds,
            boolean matchAnySkill,
            BigDecimal minRating,
            Boolean available,
            Integer minExperience,
            Integer maxExperience,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean verified
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            if (verified) {
                predicates.add(cb.isTrue(root.get("verified")));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (available != null) {
                predicates.add(cb.equal(root.get("available"), available));
            }
            if (minRating != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("averageRating"), minRating));
            }
            if (minExperience != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("experienceYears"), minExperience));
            }
            if (maxExperience != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("experienceYears"), maxExperience));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("hourlyRate"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("hourlyRate"), maxPrice));
            }
            if (city != null && !city.isBlank()) {
                String normalizedCity = city.trim().toLowerCase(Locale.ROOT);
                Subquery<UUID> serviceAreaSubquery = query.subquery(UUID.class);
                var serviceAreaRoot = serviceAreaSubquery.from(WorkerServiceArea.class);
                serviceAreaSubquery.select(serviceAreaRoot.get("worker").get("id"));
                List<Predicate> areaPredicates = new ArrayList<>();
                areaPredicates.add(cb.equal(serviceAreaRoot.get("worker").get("id"), root.get("id")));
                areaPredicates.add(cb.isTrue(serviceAreaRoot.get("active")));
                areaPredicates.add(cb.equal(cb.lower(serviceAreaRoot.get("city")), normalizedCity));
                if (state != null && !state.isBlank()) {
                    areaPredicates.add(cb.equal(cb.lower(serviceAreaRoot.get("state")), state.trim().toLowerCase(Locale.ROOT)));
                }
                serviceAreaSubquery.where(areaPredicates.toArray(Predicate[]::new));
                predicates.add(cb.or(
                        cb.equal(cb.lower(root.get("primaryCity")), normalizedCity),
                        cb.exists(serviceAreaSubquery)
                ));
            }
            if (skillIds != null && !skillIds.isEmpty()) {
                Join<Worker, Skill> skillsJoin = root.join("skills");
                if (matchAnySkill) {
                    predicates.add(skillsJoin.get("id").in(skillIds));
                } else {
                    query.distinct(true);
                    for (UUID skillId : skillIds) {
                        Subquery<UUID> skillSubquery = query.subquery(UUID.class);
                        var skillRoot = skillSubquery.from(Worker.class);
                        var join = skillRoot.join("skills");
                        skillSubquery.select(skillRoot.get("id"));
                        skillSubquery.where(
                                cb.equal(skillRoot.get("id"), root.get("id")),
                                cb.equal(join.get("id"), skillId)
                        );
                        predicates.add(cb.exists(skillSubquery));
                    }
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort resolveSort(String sort, String direction) {
        String property = switch (sort == null ? "createdAt" : sort) {
            case "rating" -> "averageRating";
            case "price" -> "hourlyRate";
            case "experience" -> "experienceYears";
            default -> "createdAt";
        };
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(sortDirection, property);
    }

    private WorkerSearchResultResponse toSearchResult(Worker worker) {
        String photoUrl = worker.getProfilePhotoFileId() == null
                ? null
                : storedFileService.getPublicDownloadUrl(worker.getProfilePhotoFileId());
        List<String> skillNames = worker.getSkills().stream().map(Skill::getName).sorted().toList();
        return new WorkerSearchResultResponse(
                worker.getId(),
                worker.getFirstName() + " " + worker.getLastName(),
                worker.getPrimaryCity(),
                worker.getCategory().getName(),
                worker.getHourlyRate(),
                worker.getAverageRating(),
                worker.getReviewCount(),
                Boolean.TRUE.equals(worker.getAvailable()),
                Boolean.TRUE.equals(worker.getVerified()),
                photoUrl,
                skillNames,
                worker.getProfileCompletionPercent() == null ? 0 : worker.getProfileCompletionPercent()
        );
    }
}
