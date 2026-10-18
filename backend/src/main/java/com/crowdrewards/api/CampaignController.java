package com.crowdrewards.api;

import com.crowdrewards.api.ApiModels.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Statement;
import java.util.*;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {
    private final JdbcTemplate jdbc;
    public CampaignController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Campaign> list() {
        return jdbc.query("SELECT * FROM campaigns ORDER BY id DESC", (rs, row) ->
            new Campaign(rs.getLong("id"), rs.getLong("merchant_id"),
                rs.getString("title"), rs.getString("description"),
                rs.getInt("discount"), rs.getInt("goal"), rs.getInt("joined")));
    }

    @PostMapping
    public ResponseEntity<Campaign> create(@Valid @RequestBody CreateCampaign request) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM merchants WHERE id = ?",
            Integer.class, request.merchantId());
        if (count == null || count == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found");
        var holder = new GeneratedKeyHolder();
        String description = request.description() == null ? "" : request.description().trim();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                "INSERT INTO campaigns(merchant_id,title,description,discount,goal) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, request.merchantId());
            ps.setString(2, request.title().trim());
            ps.setString(3, description);
            ps.setInt(4, request.discount());
            ps.setInt(5, request.goal());
            return ps;
        }, holder);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            new Campaign(Objects.requireNonNull(holder.getKey()).longValue(),
                request.merchantId(), request.title().trim(), description,
                request.discount(), request.goal(), 0));
    }
    @GetMapping("/{id}")
    public Campaign get(@PathVariable long id) {
        return jdbc.query("SELECT * FROM campaigns WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found");
            return mapCampaign(rs);
        }, id);
    }

    private Campaign mapCampaign(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Campaign(rs.getLong("id"), rs.getLong("merchant_id"),
            rs.getString("title"), rs.getString("description"),
            rs.getInt("discount"), rs.getInt("goal"), rs.getInt("joined"));
    }

    @PutMapping("/{id}")
    public Campaign update(@PathVariable long id, @Valid @RequestBody UpdateCampaign request) {
        Campaign existing = get(id);
        if (existing.joined() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Campaign with participants cannot be edited");
        int changed = jdbc.update(
            "UPDATE campaigns SET title = ?, description = ?, discount = ?, goal = ? WHERE id = ? AND joined = 0",
            request.title().trim(), request.description() == null ? "" : request.description().trim(),
            request.discount(), request.goal(), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Campaign changed; retry");
        return get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        int deleted = jdbc.update("DELETE FROM campaigns WHERE id = ? AND joined = 0", id);
        if (deleted == 0) {
            get(id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Campaign with participants cannot be deleted");
        }
        return ResponseEntity.noContent().build();
    }

}
