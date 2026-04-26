package com.catechesis.backend.church;

import com.catechesis.backend.church.dto.CreateChurchRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChurchService {

    private final ChurchRepository churchRepository;

    public ChurchService(ChurchRepository churchRepository) {
        this.churchRepository = churchRepository;
    }

    @Transactional
    public Church createChurch(CreateChurchRequest request) {
        Church church = new Church(request.displayName(), request.timezone());
        return churchRepository.save(church);
    }
}