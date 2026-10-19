package com.team10.sems.checkin;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal recipient lookup, called while the event mutation holds its lock. */
@Service
public class StaffAssignmentService {
    private final JdbcTemplate db;
    public StaffAssignmentService(JdbcTemplate db) { this.db = db; }
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> recipients(UUID eventId) {
        return db.query("""
                SELECT a.staff_id FROM event_staff_assignments a JOIN users u ON u.id=a.staff_id
                WHERE a.event_id=? AND u.status='ACTIVE' AND EXISTS
                  (SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role_code='STAFF')
                """, (rs, n) -> rs.getObject(1, UUID.class), eventId);
    }
}
