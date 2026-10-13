package com.estateflow.dao.jdbc;

import com.estateflow.dao.PropertyDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.Property;
import com.estateflow.model.enums.PropertyType;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class JdbcPropertyDao extends BaseJdbcDao implements PropertyDao {

    private static final String COLUMNS = "p.property_id, p.owner_id, p.name, p.property_type, p.address_line, "
            + "p.city, p.state, p.postal_code, p.bedrooms, p.area_sqft, p.listed_rent, p.is_active, p.created_at";

    private static final RowMapper<Property> MAPPER = rs -> {
        Property p = new Property();
        p.setPropertyId(rs.getLong("property_id"));
        p.setOwnerId(rs.getLong("owner_id"));
        p.setName(rs.getString("name"));
        p.setPropertyType(PropertyType.valueOf(rs.getString("property_type")));
        p.setAddressLine(rs.getString("address_line"));
        p.setCity(rs.getString("city"));
        p.setState(rs.getString("state"));
        p.setPostalCode(rs.getString("postal_code"));
        p.setBedrooms(intOrNull(rs, "bedrooms"));
        p.setAreaSqft(rs.getBigDecimal("area_sqft"));
        p.setListedRent(rs.getBigDecimal("listed_rent"));
        p.setActive(rs.getBoolean("is_active"));
        p.setCreatedAt(dateTime(rs, "created_at"));
        return p;
    };

    public JdbcPropertyDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    private static void bindFields(PreparedStatement ps, Property p) throws SQLException {
        ps.setLong(1, p.getOwnerId());
        ps.setString(2, p.getName());
        ps.setString(3, p.getPropertyType().name());
        ps.setString(4, p.getAddressLine());
        ps.setString(5, p.getCity());
        ps.setString(6, p.getState());
        ps.setString(7, p.getPostalCode());
        if (p.getBedrooms() == null) {
            ps.setNull(8, Types.TINYINT);
        } else {
            ps.setInt(8, p.getBedrooms());
        }
        ps.setBigDecimal(9, p.getAreaSqft());
        ps.setBigDecimal(10, p.getListedRent());
        ps.setBoolean(11, p.isActive());
    }

    @Override
    public Property insert(Property p) {
        long id = insertReturningKey("insert property", "INSERT INTO property (owner_id, name, property_type, "
                + "address_line, city, state, postal_code, bedrooms, area_sqft, listed_rent, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ps -> bindFields(ps, p));
        p.setPropertyId(id);
        return p;
    }

    @Override
    public int update(Property p) {
        return executeUpdate("update property", "UPDATE property SET owner_id = ?, name = ?, property_type = ?, "
                + "address_line = ?, city = ?, state = ?, postal_code = ?, bedrooms = ?, area_sqft = ?, "
                + "listed_rent = ?, is_active = ? WHERE property_id = ?", ps -> {
            bindFields(ps, p);
            ps.setLong(12, p.getPropertyId());
        });
    }

    @Override
    public Optional<Property> findById(Long id) {
        return queryOne("find property by id", "SELECT " + COLUMNS + " FROM property p WHERE p.property_id = ?",
                ps -> ps.setLong(1, id), MAPPER);
    }

    @Override
    public Optional<Property> findByIdForUpdate(Long id) {
        return queryOne("lock property", "SELECT " + COLUMNS + " FROM property p WHERE p.property_id = ? FOR UPDATE",
                ps -> ps.setLong(1, id), MAPPER);
    }

    @Override
    public List<Property> findAll() {
        return queryList("list properties", "SELECT " + COLUMNS + " FROM property p ORDER BY p.name", Binder.NONE, MAPPER);
    }

    @Override
    public List<Property> findByOwner(Long ownerId) {
        return queryList("list properties of owner", "SELECT " + COLUMNS
                + " FROM property p WHERE p.owner_id = ? ORDER BY p.name", ps -> ps.setLong(1, ownerId), MAPPER);
    }

    @Override
    public List<Property> search(String text) {
        return queryList("search properties", "SELECT " + COLUMNS + " FROM property p WHERE p.name LIKE ? "
                + "OR p.address_line LIKE ? OR p.city LIKE ? ORDER BY p.name", ps -> {
            for (int i = 1; i <= 3; i++) {
                ps.setString(i, like(text));
            }
        }, MAPPER);
    }

    @Override
    public List<Property> findVacantOn(LocalDate date) {
        return queryList("list vacant properties", "SELECT " + COLUMNS + " FROM property p WHERE p.is_active = 1 "
                + "AND NOT EXISTS (SELECT 1 FROM rental_agreement ra WHERE ra.property_id = p.property_id AND "
                + SqlFragments.OCCUPIES + ") ORDER BY p.name", ps -> {
            ps.setObject(1, date);
            ps.setObject(2, date);
        }, MAPPER);
    }

    @Override
    public int countActiveByOwner(Long ownerId) {
        return queryOne("count active properties of owner",
                "SELECT COUNT(*) AS n FROM property WHERE owner_id = ? AND is_active = 1",
                ps -> ps.setLong(1, ownerId), rs -> rs.getInt("n")).orElse(0);
    }

    @Override
    public List<Property> search(String text, int limit) {
        return queryList("search properties (capped)", "SELECT " + COLUMNS + " FROM property p WHERE p.name LIKE ? "
                + "OR p.address_line LIKE ? OR p.city LIKE ? ORDER BY p.name LIMIT ?", ps -> {
            for (int i = 1; i <= 3; i++) {
                ps.setString(i, like(text));
            }
            ps.setInt(4, limit);
        }, MAPPER);
    }
}
