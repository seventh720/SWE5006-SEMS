package com.team10.sems.event.internal.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.team10.sems.event.internal.persistence.EventRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

class EventBrowseServiceTest {
    private final EventRepository repository = mock(EventRepository.class);
    private final EventBrowseService service = new EventBrowseService(repository);

    @Test
    void rejectsInvalidPagesBeforeQueryingDatabase() {
        for (int[] pair : new int[][] {{-1, 10}, {0, 0}, {0, 51}, {Integer.MAX_VALUE, 50}}) {
            var error = assertThrows(ResponseStatusException.class, () -> service.browse(pair[0], pair[1], ""));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void trimsSearchAndSortsByTimeThenId() {
        var pageable = PageRequest.of(2, 10, Sort.by("startsAt", "id"));
        when(repository.findPublished("Open Day", pageable)).thenReturn(Page.empty(pageable));
        var result = service.browse(2, 10, "  Open Day  ");
        assertEquals(2, result.page());
        assertEquals(10, result.size());
        assertTrue(result.items().isEmpty());
        verify(repository).findPublished("Open Day", pageable);
    }

    @Test
    void missingOrUnpublishedEventIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndStatus(id, "PUBLISHED")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.detail(id)).getStatusCode().value());
        verify(repository).findByIdAndStatus(id, "PUBLISHED");
    }
}
