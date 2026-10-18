package com.crowdrewards.api;

import com.crowdrewards.api.ApiModels.*;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Statement;
import java.util.*;

@RestController
@RequestMapping("/api/merchants")
public class MerchantController {
    private final JdbcTemplate jdbc;
    public MerchantController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    public ResponseEntity<Merchant> create(@Valid @RequestBody CreateMerchant request) {
        var holder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var ps = connection.prepareStatement(
                    "INSERT INTO merchants(name,email) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, request.name().trim());
                ps.setString(2, request.email().trim().toLowerCase(Locale.ROOT));
                return ps;
            }, holder);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Merchant email already exists");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
            new Merchant(Objects.requireNonNull(holder.getKey()).longValue(),
                         request.name().trim(), request.email().trim().toLowerCase(Locale.ROOT)));
    }

    @GetMapping
    public List<Merchant> list() {
        return jdbc.query("SELECT id,name,email FROM merchants ORDER BY id DESC",
            (rs, row) -> new Merchant(rs.getLong("id"), rs.getString("name"), rs.getString("email")));
    }
}
