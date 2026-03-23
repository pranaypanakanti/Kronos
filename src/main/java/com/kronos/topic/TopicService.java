package com.kronos.topic;

import com.kronos.common.exception.ApiException;
import com.kronos.entity.RevisionPlan;
import com.kronos.entity.RevisionSchedule;
import com.kronos.entity.Topic;
import com.kronos.entity.User;
import com.kronos.entity.enums.RevisionStatus;
import com.kronos.entity.enums.TopicStatus;
import com.kronos.repository.RevisionPlanRepository;
import com.kronos.repository.RevisionScheduleRepository;
import com.kronos.repository.TopicRepository;
import com.kronos.repository.UserRepository;
import com.kronos.topic.dto.CreateTopicRequest;
import com.kronos.topic.dto.PatchTopicRequest;
import com.kronos.topic.dto.TopicResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final RevisionPlanRepository revisionPlanRepository;
    private final RevisionScheduleRepository revisionScheduleRepository;

    @Transactional
    public TopicResponse create(String emailRaw, CreateTopicRequest req) {
        User user = getUserByEmail(emailRaw);

        Topic topic = new Topic();
        topic.setUser(user);
        topic.setSubject(normalize(req.getSubject()));
        topic.setTitle(normalize(req.getTitle()));
        topic.setNotes(req.getNotes() == null ? null : req.getNotes().trim());

        RevisionPlan plan = null;
        if (req.getRevisionPlanId() != null) {
            plan = revisionPlanRepository.findById(req.getRevisionPlanId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid revisionPlanId"));
            topic.setStatus(TopicStatus.ACTIVE);
        } else {
            topic.setStatus(TopicStatus.LEARNED);
        }

        topic.setRevisionPlan(plan);

        topicRepository.save(topic);

        if (plan != null) {
            createSchedules(topic, plan);
        }

        return toResponse(topic);
    }

    @Transactional(readOnly = true)
    public Page<TopicResponse> list(String emailRaw,
                                    TopicStatus status,
                                    LocalDate createdDate,
                                    String q,
                                    Pageable pageable) {
        User user = getUserByEmail(emailRaw);

        LocalDateTime from = null;
        LocalDateTime to = null;
        if (createdDate != null) {
            from = createdDate.atStartOfDay();
            to = createdDate.plusDays(1).atStartOfDay();
        }

        String query = (q == null || q.isBlank()) ? null : q.trim();

        return topicRepository.search(user.getId(), status, from, to, query, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TopicResponse getOne(String emailRaw, UUID topicId) {
        User user = getUserByEmail(emailRaw);
        Topic topic = topicRepository.findActiveByIdAndUserId(topicId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topic not found"));

        return toResponse(topic);
    }

    @Transactional
    public TopicResponse patch(String emailRaw, UUID topicId, PatchTopicRequest req) {
        User user = getUserByEmail(emailRaw);
        Topic topic = topicRepository.findActiveByIdAndUserId(topicId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topic not found"));

        if (topic.getStatus() == TopicStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Archived topic cannot be updated");
        }

        if (req.getSubject() != null) topic.setSubject(normalize(req.getSubject()));
        if (req.getTitle() != null) topic.setTitle(normalize(req.getTitle()));
        if (req.getNotes() != null) topic.setNotes(req.getNotes().trim());

        topicRepository.save(topic);
        return toResponse(topic);
    }

    @Transactional
    public void archive(String emailRaw, UUID topicId) {
        User user = getUserByEmail(emailRaw);
        Topic topic = topicRepository.findActiveByIdAndUserId(topicId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topic not found"));

        if (topic.getStatus() == TopicStatus.ARCHIVED) return;

        topic.setStatus(TopicStatus.ARCHIVED);
        topicRepository.save(topic);

        List<RevisionSchedule> schedules = revisionScheduleRepository.findAllActiveByTopicId(topic.getId());
        schedules.stream()
                .filter(rs -> rs.getStatus() == RevisionStatus.PENDING)
                .forEach(rs -> rs.setStatus(RevisionStatus.CANCELLED));

        revisionScheduleRepository.saveAll(schedules);
    }

    @Transactional
    public void delete(String emailRaw, UUID topicId) {
        User user = getUserByEmail(emailRaw);
        Topic topic = topicRepository.findActiveByIdAndUserId(topicId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topic not found"));

        LocalDateTime now = LocalDateTime.now();
        topic.setDeletedAt(now);
        topicRepository.save(topic);

        List<RevisionSchedule> schedules = revisionScheduleRepository.findAllActiveByTopicId(topic.getId());
        schedules.forEach(rs -> {
            rs.setDeletedAt(now);
            rs.setStatus(RevisionStatus.CANCELLED);
        });
        revisionScheduleRepository.saveAll(schedules);
    }

    private void createSchedules(Topic topic, RevisionPlan plan) {
        LocalDate baseDate = topic.getCreatedAt().toLocalDate();

        List<Integer> days = plan.getRevisionDays().stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        for (int day : days) {
            if (day <= 1) continue;

            RevisionSchedule rs = new RevisionSchedule();
            rs.setTopic(topic);
            rs.setRevisionPlan(plan);
            rs.setDayNumber(day);
            rs.setScheduledDate(baseDate.plusDays(day - 1L));
            rs.setStatus(RevisionStatus.PENDING);
            rs.setNotificationSent(false);

            revisionScheduleRepository.save(rs);
        }
    }

    private User getUserByEmail(String emailRaw) {
        String email = emailRaw.toLowerCase().trim();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private TopicResponse toResponse(Topic t) {
        UUID revisionPlanId = (t.getRevisionPlan() == null) ? null : t.getRevisionPlan().getId();

        return new TopicResponse(
                t.getId(),
                t.getSubject(),
                t.getTitle(),
                t.getNotes(),
                t.getStatus(),
                revisionPlanId,
                t.getCreatedAt()
        );
    }
}