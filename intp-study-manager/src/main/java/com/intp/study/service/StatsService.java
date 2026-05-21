package com.intp.study.service;

import com.intp.study.model.StatsModels.KnowledgeLinkView;
import com.intp.study.model.StatsModels.LowMasteryCard;
import com.intp.study.model.StatsModels.MistakeCauseCount;
import com.intp.study.model.StatsModels.OpenParkingQuestion;
import com.intp.study.model.StatsModels.RecentBlocker;
import com.intp.study.model.StudyConstants;
import com.intp.study.repository.StatsRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StatsService {
    private final StatsRepository statsRepository;

    public StatsService(StatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    public long count(String table) {
        return statsRepository.count(table);
    }

    public List<LowMasteryCard> lowMasteryCards() {
        return lowMasteryCards(10);
    }

    public List<LowMasteryCard> lowMasteryCards(int limit) {
        return statsRepository.lowMasteryCards(limit);
    }

    public List<RecentBlocker> recentBlockers() {
        return recentBlockers(8);
    }

    public List<RecentBlocker> recentBlockers(int limit) {
        return statsRepository.recentBlockers(limit);
    }

    public List<OpenParkingQuestion> openParkingQuestions() {
        return openParkingQuestions(10);
    }

    public List<OpenParkingQuestion> openParkingQuestions(int limit) {
        return statsRepository.openParkingQuestions(StudyConstants.STATUS_PARKING_DONE, limit);
    }

    public List<KnowledgeLinkView> recentKnowledgeLinks() {
        return recentKnowledgeLinks(8);
    }

    public List<KnowledgeLinkView> recentKnowledgeLinks(int limit) {
        return statsRepository.recentKnowledgeLinks(limit);
    }

    public List<MistakeCauseCount> mistakeCauseCounts() {
        return mistakeCauseCounts(null);
    }

    public List<MistakeCauseCount> mistakeCauseCounts(String subject) {
        return statsRepository.mistakeCauseCounts(subject);
    }
}
