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
-- Drop and recreate ACTIVE_NAME/UQ_TOPIC_ORG_ACTIVE_NAME around MODIFY COLUMN STATUS (MySQL ERROR 3108).
ALTER TABLE `TOPIC`
  DROP INDEX `UQ_TOPIC_ORG_ACTIVE_NAME`,
  DROP COLUMN `ACTIVE_NAME`,
  MODIFY COLUMN `STATUS` enum('active', 'deregistered', 'deleted') NOT NULL DEFAULT 'active',
  ADD COLUMN `ACTIVE_NAME` varchar(225) GENERATED ALWAYS AS
    (CASE WHEN `STATUS` = 'active' THEN LOWER(`NAME`) ELSE NULL END) STORED,
  ADD UNIQUE KEY `UQ_TOPIC_ORG_ACTIVE_NAME` (`ORG_ID`, `ACTIVE_NAME`);
UPDATE `TOPIC` SET `STATUS` = 'deleted' WHERE `STATUS` = 'deregistered';
ALTER TABLE `TOPIC`
  DROP INDEX `UQ_TOPIC_ORG_ACTIVE_NAME`,
  DROP COLUMN `ACTIVE_NAME`,
  MODIFY COLUMN `STATUS` enum('active', 'deleted') NOT NULL DEFAULT 'active',
  ADD COLUMN `ACTIVE_NAME` varchar(225) GENERATED ALWAYS AS
    (CASE WHEN `STATUS` = 'active' THEN LOWER(`NAME`) ELSE NULL END) STORED,
  ADD UNIQUE KEY `UQ_TOPIC_ORG_ACTIVE_NAME` (`ORG_ID`, `ACTIVE_NAME`);

-- Backfill WEBHOOK_DELIVERY.STATUS = 'completed' for deliveries completed before v1.1
UPDATE WEBHOOK_DELIVERY SET STATUS = 'completed'
WHERE STATUS = 'delivered'
  AND DELIVERY_ID IN (SELECT DELIVERY_ID FROM WEBHOOK_DELIVERY_ACK WHERE COMPLETION_STATUS = 'completed');

-- Add index for pending and in-flight webhook delivery range scans (idempotent for MySQL)
SET @idx_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'WEBHOOK_DELIVERY'
    AND INDEX_NAME = 'IDX_WD_STATUS_CREATED'
);
SET @sql := IF(
  @idx_exists = 0,
  'CREATE INDEX IDX_WD_STATUS_CREATED ON WEBHOOK_DELIVERY (STATUS, CREATED_AT)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

