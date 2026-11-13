package com.rinda.backend.plot;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PhotoStorage;
import com.rinda.backend.common.PhotoStorage.StoredPhoto;
import com.rinda.backend.plot.PlotDtos.DocumentResponse;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The farmer's own proof of the plot: their e-title or a screenshot from the official portal, obtained with their
 * own access. Rinda never contacts NLA systems. Nothing is read from the image (no OCR): the farmer types the area
 * and the agent compares it with the image. Documents are personal data: kept privately, never logged.
 */
@Service
public class PlotDocumentService {

    private final PlotService plotService;
    private final PlotRepository plots;
    private final PhotoStorage storage;
    private final PlotChecks checks;
    private final PlotRules rules;
    private final JdbcTemplate jdbc;

    public PlotDocumentService(PlotService plotService, PlotRepository plots, PhotoStorage storage, PlotChecks checks,
                               PlotRules rules, JdbcTemplate jdbc) {
        this.plotService = plotService;
        this.plots = plots;
        this.storage = storage;
        this.checks = checks;
        this.rules = rules;
        this.jdbc = jdbc;
    }

    @Transactional
    public DocumentResponse upload(AuthPrincipal farmer, long plotId, DocumentType type, BigDecimal documentAreaM2,
                                   MultipartFile file) {
        Plot plot = plotService.findOwned(farmer, plotId);
        if (type.isPlotProof()) {
            if (plot.getVerificationLevel().atLeast(VerificationLevel.FIELD_VERIFIED)) {
                throw new ApiException(HttpStatus.CONFLICT, "PLOT_LOCKED",
                        "The plot's documents can't change after the field check. Ask your agent.");
            }
            if (documentAreaM2 == null || documentAreaM2.signum() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_AREA_REQUIRED",
                        "Type the plot area shown on the document, in square metres.");
            }
        }
        String key = storage.saveDocument("plot-documents", file);
        if (key == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_REQUIRED", "Attach the document.");
        }
        BigDecimal typedArea = type.isPlotProof() ? documentAreaM2.setScale(1, java.math.RoundingMode.HALF_UP) : null;
        long id = insert(plotId, type, key, typedArea, farmer.userId(), plot.isDemo());

        if (type.isPlotProof()) {
            plot.setDocumentArea(typedArea);
            checks.area(plot);
        }
        rules.refresh(plot);
        plots.saveAndFlush(plot);
        return list(plotId).stream().filter(d -> d.id() == id).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(AuthPrincipal principal, long plotId) {
        plotService.findVisible(principal, plotId);
        return list(plotId);
    }

    @Transactional(readOnly = true)
    public StoredPhoto file(AuthPrincipal principal, long plotId, long documentId) {
        plotService.findVisible(principal, plotId);
        String key = jdbc.queryForList("SELECT storage_key FROM plot_documents WHERE id = ? AND plot_id = ?",
                        String.class, documentId, plotId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found."));
        return storage.read(key);
    }

    private List<DocumentResponse> list(long plotId) {
        return jdbc.query("""
                SELECT id, plot_id, type, content_type, typed_area_m2, uploaded_at, is_demo
                FROM plot_documents WHERE plot_id = ? ORDER BY id
                """, (rs, n) -> new DocumentResponse(rs.getLong(1), rs.getLong(2), DocumentType.valueOf(rs.getString(3)),
                rs.getString(4), rs.getBigDecimal(5), rs.getTimestamp(6).toInstant(), rs.getBoolean(7)), plotId);
    }

    private long insert(long plotId, DocumentType type, String key, BigDecimal typedArea, long userId, boolean demo) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO plot_documents (plot_id, type, storage_key, content_type, typed_area_m2, uploaded_by,
                                                is_demo)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, plotId);
            ps.setString(2, type.name());
            ps.setString(3, key);
            ps.setString(4, PhotoStorage.contentType(key));
            ps.setBigDecimal(5, typedArea);
            ps.setLong(6, userId);
            ps.setBoolean(7, demo);
            return ps;
        }, holder);
        return holder.getKey().longValue();
    }
}
