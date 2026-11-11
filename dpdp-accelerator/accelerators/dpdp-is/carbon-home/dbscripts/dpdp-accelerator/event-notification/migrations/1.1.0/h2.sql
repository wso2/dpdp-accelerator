-- Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
--
-- WSO2 LLC. licenses this file to you under the Apache License,
-- Version 2.0 (the "License"); you may not use this file except
-- in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations
-- under the License.

-- Normalize topic deletion status from 'deregistered' to 'deleted'
-- Recover STATUS/ACTIVE_NAME if a prior run was interrupted mid-DDL, then drop legacy CHECK
-- constraints on TOPIC in-place before updating rows and enforcing CHK_TOPIC_STATUS.
CREATE ALIAS IF NOT EXISTS DPDP_MIGRATE_TOPIC_110 AS $$
void migrateTopic(java.sql.Connection conn) throws java.sql.SQLException {
    boolean hasStatus = false;
    boolean hasStatusMig = false;
    boolean hasActiveName = false;
    try (java.sql.PreparedStatement ps = conn.prepareStatement(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'TOPIC'")) {
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String col = rs.getString(1);
                if ("STATUS".equalsIgnoreCase(col)) {
                    hasStatus = true;
                } else if ("STATUS_MIG".equalsIgnoreCase(col)) {
                    hasStatusMig = true;
                } else if ("ACTIVE_NAME".equalsIgnoreCase(col)) {
                    hasActiveName = true;
                }
            }
        }
    }
    java.util.List<String> checkConstraints = new java.util.ArrayList<>();
    try (java.sql.PreparedStatement ps = conn.prepareStatement(
            "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
            + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'TOPIC' AND CONSTRAINT_TYPE = 'CHECK'")) {
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                checkConstraints.add(rs.getString(1));
            }
        }
    }
    try (java.sql.Statement st = conn.createStatement()) {
        if (!hasStatus && hasStatusMig) {
            st.execute("ALTER TABLE TOPIC ALTER COLUMN STATUS_MIG RENAME TO STATUS");
            hasStatus = true;
            hasStatusMig = false;
        }
        if (hasStatus && hasStatusMig) {
            st.execute("ALTER TABLE TOPIC DROP COLUMN IF EXISTS STATUS_MIG");
        }
        for (String constraintName : checkConstraints) {
            String escaped = constraintName.replace("\"", "\"\"");
            st.execute("ALTER TABLE TOPIC DROP CONSTRAINT IF EXISTS \"" + escaped + "\"");
        }
        if (hasStatus) {
            st.execute("UPDATE TOPIC SET STATUS = 'deleted' WHERE LOWER(STATUS) = 'deregistered'");
            st.execute("ALTER TABLE TOPIC ALTER COLUMN STATUS SET DATA TYPE VARCHAR(32)");
            st.execute("ALTER TABLE TOPIC ALTER COLUMN STATUS SET DEFAULT 'active'");
            st.execute("ALTER TABLE TOPIC ALTER COLUMN STATUS SET NOT NULL");
            st.execute("ALTER TABLE TOPIC ADD CONSTRAINT CHK_TOPIC_STATUS CHECK (STATUS IN ('active', 'deleted'))");
        }
        if (hasStatus && !hasActiveName) {
            st.execute("ALTER TABLE TOPIC ADD COLUMN ACTIVE_NAME VARCHAR(225) GENERATED ALWAYS AS "
                    + "(CASE WHEN STATUS = 'active' THEN LOWER(NAME) ELSE NULL END)");
        }
    }
}
$$;
CALL DPDP_MIGRATE_TOPIC_110();
DROP ALIAS IF EXISTS DPDP_MIGRATE_TOPIC_110;
CREATE UNIQUE INDEX IF NOT EXISTS UQ_TOPIC_ORG_ACTIVE_NAME ON TOPIC (ORG_ID, ACTIVE_NAME);

-- Backfill WEBHOOK_DELIVERY.STATUS = 'completed' for deliveries completed before v1.1
UPDATE WEBHOOK_DELIVERY SET STATUS = 'completed'
WHERE STATUS = 'delivered'
  AND DELIVERY_ID IN (SELECT DELIVERY_ID FROM WEBHOOK_DELIVERY_ACK WHERE COMPLETION_STATUS = 'completed');

