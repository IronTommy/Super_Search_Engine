package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;

@Service
public class LemmaService {

    private final LemmaRepository lemmaRepository;

    public LemmaService(LemmaRepository lemmaRepository) {
        this.lemmaRepository = lemmaRepository;
    }

    public Lemma getOrCreateLemma(String lemmaText, Site site) {
        return lemmaRepository.findByLemma(lemmaText).orElseGet(()
                -> createLemma(lemmaText, site));
    }


    public void updateLemmaFrequencyForPage(Lemma lemma, int frequency) {
        lemma.setFrequency(lemma.getFrequency() + frequency);
        lemmaRepository.save(lemma);
    }

    public void saveLemma(Lemma lemma) {
        lemmaRepository.save(lemma);
    }

    private Lemma createLemma(String lemmaText, Site site) {
        Lemma lemma = new Lemma();
        lemma.setLemma(lemmaText);
        lemma.setFrequency(1);
        lemma.setSite(site);

        return lemmaRepository.save(lemma);
    }
}
