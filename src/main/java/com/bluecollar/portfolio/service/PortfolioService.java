package com.bluecollar.portfolio.service;

import com.bluecollar.portfolio.dto.*;

import java.util.List;
import java.util.UUID;

public interface PortfolioService {

    PortfolioSummaryResponse getMyPortfolio();

    ProfileCompletionResponse getProfileCompletion();

    PortfolioImageResponse addPortfolioImage(AddPortfolioImageRequest request);

    PortfolioImageResponse updatePortfolioImage(UUID id, UpdatePortfolioImageRequest request);

    void deletePortfolioImage(UUID id);

    CertificateResponse addCertificate(AddCertificateRequest request);

    CertificateResponse updateCertificate(UUID id, UpdateCertificateRequest request);

    void deleteCertificate(UUID id);

    IdentityDocumentResponse addIdentityDocument(AddIdentityDocumentRequest request);

    List<IdentityDocumentResponse> getMyIdentityDocuments();

    void deleteIdentityDocument(UUID id);

    PortfolioSummaryResponse setProfilePhoto(SetProfilePhotoRequest request);

    List<PortfolioImageResponse> getPublicPortfolio(UUID workerId);
}
