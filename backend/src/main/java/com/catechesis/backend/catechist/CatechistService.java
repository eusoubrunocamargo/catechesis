package com.catechesis.backend.catechist;

import com.catechesis.backend.catechist.dto.CreateLeadRequest;
import com.catechesis.backend.church.ChurchRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatechistService {

    private final CatechistRepository catechistRepository;
    private final ChurchRepository churchRepository;

    public CatechistService(CatechistRepository catechistRepository,
                            ChurchRepository churchRepository) {
        this.catechistRepository = catechistRepository;
        this.churchRepository = churchRepository;
    }

    /**
     * Creates the first Lead Catechist for the given church.
     *
     * @throws ResponseStatusException 404 if the church doesn't exist;
     *                                 409 if the church already has an
     *                                 active Lead.
     */
    @Transactional
    public Catechist createFirstLead(UUID churchId, CreateLeadRequest request) {
        if (!churchRepository.existsById(churchId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Church not found: " + churchId);
        }

        if (catechistRepository.existsByChurchIdAndRoleAndActiveTrue(churchId, CatechistRole.LEAD)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Church already has an active Lead: " + churchId);
        }

        Catechist catechist = new Catechist(
                churchId,
                request.email(),
                request.name(),
                CatechistRole.LEAD
        );
        return catechistRepository.save(catechist);
    }
}