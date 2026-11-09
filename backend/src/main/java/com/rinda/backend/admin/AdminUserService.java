package com.rinda.backend.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinda.backend.admin.AdminDtos.CooperativeRequest;
import com.rinda.backend.admin.AdminDtos.UserRequest;
import com.rinda.backend.admin.AdminDtos.UserResponse;
import com.rinda.backend.auth.AuthService;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.user.CooperativeResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff accounts (spec §2.2 #3): sign-up only ever creates farmers, so agents, officers and admins are created
 * here by an admin. An agent is always linked to one cooperative. Accounts are suspended, never deleted, so
 * decisions keep their author.
 */
@Service
public class AdminUserService {

    private static final String SELECT = """
            SELECT u.id, u.phone, u.full_name, u.role, u.status, u.language,
                   COALESCE(f.cooperative_id, a.cooperative_id), co.name, u.is_demo, u.created_at
            FROM users u
            LEFT JOIN farmers f ON f.user_id = u.id
            LEFT JOIN agents a ON a.user_id = u.id
            LEFT JOIN cooperatives co ON co.id = COALESCE(f.cooperative_id, a.cooperative_id)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AdminConfigService config;

    public AdminUserService(JdbcTemplate jdbc, ObjectMapper mapper, AdminConfigService config) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.config = config;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String role, String search, int page, int size) {
        PageResponse.check(page, size);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        if (role != null) {
            where.append(" AND u.role = ?");
            args.add(role);
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (u.full_name LIKE ? OR u.phone LIKE ?)");
            String like = "%" + search.trim().replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM users u" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        List<UserResponse> items = jdbc.query(SELECT + where + " ORDER BY u.role, u.full_name LIMIT ? OFFSET ?",
                (rs, n) -> user(rs), args.toArray());
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    /** The new person logs in with their phone and the usual one-time code. */
    @Transactional
    public UserResponse create(long adminId, UserRequest r) {
        String phone = AuthService.normalise(r.phone());
        if ("AGENT".equals(r.role())) {
            if (r.cooperativeId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "COOPERATIVE_REQUIRED", "An agent needs a cooperative.");
            }
            if (jdbc.queryForList("SELECT id FROM cooperatives WHERE id = ?", Long.class, r.cooperativeId()).isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "COOPERATIVE_NOT_FOUND", "That cooperative does not exist.");
            }
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO users (phone, full_name, role, language) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, phone);
                ps.setString(2, r.fullName().trim());
                ps.setString(3, r.role());
                ps.setString(4, r.language() == null ? "rw" : r.language());
                return ps;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_IN_USE", "This phone number already has an account.");
        }
        long id = Objects.requireNonNull(keys.getKey()).longValue();
        if ("AGENT".equals(r.role())) {
            jdbc.update("INSERT INTO agents (user_id, cooperative_id, approved_by) VALUES (?, ?, ?)",
                    id, r.cooperativeId(), adminId);
        }
        UserResponse created = get(id);
        config.audit(adminId, "USER_CREATED", "users", id, null, mapper.valueToTree(created));
        return created;
    }

    /** Suspending also ends every session; the access token in use expires within 15 minutes. */
    @Transactional
    public UserResponse setStatus(long adminId, long userId, String status) {
        if (adminId == userId) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_CHANGE_SELF", "You can't suspend your own account.");
        }
        UserResponse before = get(userId);
        jdbc.update("UPDATE users SET status = ? WHERE id = ?", status, userId);
        if ("SUSPENDED".equals(status)) {
            jdbc.update("UPDATE refresh_tokens SET revoked_at = CURRENT_TIMESTAMP(6) WHERE user_id = ? AND revoked_at IS NULL",
                    userId);
        }
        ObjectNode was = mapper.createObjectNode().put("status", before.status());
        ObjectNode now = mapper.createObjectNode().put("status", status);
        config.audit(adminId, "SUSPENDED".equals(status) ? "USER_SUSPENDED" : "USER_REACTIVATED", "users", userId,
                was, now);
        return get(userId);
    }

    @Transactional
    public CooperativeResponse createCooperative(long adminId, CooperativeRequest r) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO cooperatives (name, district, sector) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, r.name().trim());
                ps.setString(2, r.district().trim());
                ps.setString(3, r.sector() == null || r.sector().isBlank() ? null : r.sector().trim());
                return ps;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "COOPERATIVE_EXISTS", "A cooperative with this name exists.");
        }
        long id = Objects.requireNonNull(keys.getKey()).longValue();
        CooperativeResponse created = jdbc.queryForObject(
                "SELECT id, name, district, sector, is_demo FROM cooperatives WHERE id = ?",
                (rs, n) -> new CooperativeResponse(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getBoolean(5)), id);
        config.audit(adminId, "COOPERATIVE_CREATED", "cooperatives", id, null, mapper.valueToTree(created));
        return created;
    }

    private UserResponse get(long id) {
        return jdbc.query(SELECT + " WHERE u.id = ?", (rs, n) -> user(rs), id).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
    }

    private static UserResponse user(ResultSet rs) throws SQLException {
        return new UserResponse(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getObject(7, Long.class), rs.getString(8), rs.getBoolean(9),
                rs.getTimestamp(10).toInstant());
    }
}
