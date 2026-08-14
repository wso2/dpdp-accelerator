package com.openfgc.nomineeservice.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Stores a nomination's permissions in a single column.
 *
 * <p>The permissions are a small, closed set that is always loaded with its
 * nomination and always replaced whole, so they behave as a field of the
 * nomination rather than as related records. Keeping them in one column means a
 * nomination is one row in one table, which is also what lets it carry a single
 * organization id like every other table in OpenFGC.
 *
 * <p>Names are written sorted, so two nominations granting the same permissions
 * always store identical text and can be compared or diffed directly.
 *
 * <p><b>Never match this column with LIKE.</b> {@code LIKE '%CONSENT_APPROVE%'}
 * would also match a future {@code CONSENT_APPROVE_LIMITED}, reporting an
 * authority that was never granted. Application code never string-matches: it
 * converts to an {@link EnumSet} and tests membership. Raw SQL must use an exact
 * list match such as {@code FIND_IN_SET('CONSENT_APPROVE', permissions)}.
 */
@Converter
public class NomineePermissionSetConverter
        implements AttributeConverter<Set<NomineePermission>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(Set<NomineePermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "";
        }
        return permissions.stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .collect(Collectors.joining(SEPARATOR));
    }

    @Override
    public Set<NomineePermission> convertToEntityAttribute(String column) {
        Set<NomineePermission> permissions = EnumSet.noneOf(NomineePermission.class);
        if (column == null || column.isBlank()) {
            return permissions;
        }
        Arrays.stream(column.split(SEPARATOR))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(NomineePermission::valueOf)
                .forEach(permissions::add);
        return permissions;
    }
}
