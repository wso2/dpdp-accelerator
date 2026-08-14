package com.openfgc.nomineeservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NomineePermissionSetConverterTest {

    private final NomineePermissionSetConverter converter = new NomineePermissionSetConverter();

    @Test
    void writesNamesSortedSoIdenticalGrantsStoreIdenticalText() {
        String fromOneOrder = converter.convertToDatabaseColumn(
                Set.of(NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_APPROVE,
                        NomineePermission.CONSENT_REVOKE));
        String fromAnother = converter.convertToDatabaseColumn(
                Set.of(NomineePermission.CONSENT_REVOKE, NomineePermission.CONSENT_VIEW,
                        NomineePermission.CONSENT_APPROVE));

        assertThat(fromOneOrder).isEqualTo("CONSENT_APPROVE,CONSENT_REVOKE,CONSENT_VIEW");
        assertThat(fromAnother).isEqualTo(fromOneOrder);
    }

    @Test
    void roundTripsEveryPermission() {
        Set<NomineePermission> all = EnumSet.allOf(NomineePermission.class);

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(all)))
                .isEqualTo(all);
    }

    @Test
    void roundTripsASingleValue() {
        Set<NomineePermission> one = Set.of(NomineePermission.CONSENT_VIEW);

        assertThat(converter.convertToDatabaseColumn(one)).isEqualTo("CONSENT_VIEW");
        assertThat(converter.convertToEntityAttribute("CONSENT_VIEW")).isEqualTo(one);
    }

    @Test
    void treatsAnEmptyGrantAndNullAsNoPermissions() {
        assertThat(converter.convertToDatabaseColumn(Set.of())).isEmpty();
        assertThat(converter.convertToDatabaseColumn(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    /**
     * The stored form is a list, not free text. A value must match a whole entry:
     * a column containing CONSENT_VIEW does not grant CONSENT_VIEW_EXTENDED, and
     * the reverse must hold too. This is the reason the class comment forbids
     * matching the column with LIKE.
     */
    @Test
    void readsWholeEntriesRatherThanSubstrings() {
        Set<NomineePermission> parsed =
                converter.convertToEntityAttribute("CONSENT_VIEW,CONSENT_REVOKE");

        assertThat(parsed).containsExactlyInAnyOrder(
                NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_REVOKE);
        assertThat(parsed).doesNotContain(NomineePermission.CONSENT_APPROVE);
    }

    @Test
    void toleratesSpacingAroundStoredNames() {
        assertThat(converter.convertToEntityAttribute(" CONSENT_VIEW , CONSENT_REVOKE "))
                .containsExactlyInAnyOrder(
                        NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_REVOKE);
    }
}
