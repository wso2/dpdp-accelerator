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
-- Drop dependent index/generated column and swap STATUS so unnamed legacy CHECK constraints are cleared.
DROP INDEX IF EXISTS UQ_TOPIC_ORG_ACTIVE_NAME;
ALTER TABLE TOPIC DROP COLUMN IF EXISTS ACTIVE_NAME;
ALTER TABLE TOPIC ADD COLUMN IF NOT EXISTS STATUS_MIG VARCHAR(32);
UPDATE TOPIC SET STATUS_MIG = CASE WHEN LOWER(STATUS) = 'deregistered' THEN 'deleted' ELSE STATUS END;
ALTER TABLE TOPIC DROP COLUMN STATUS;
ALTER TABLE TOPIC ALTER COLUMN STATUS_MIG RENAME TO STATUS;
ALTER TABLE TOPIC ALTER COLUMN STATUS SET DATA TYPE VARCHAR(32);
ALTER TABLE TOPIC ALTER COLUMN STATUS SET DEFAULT 'active';
ALTER TABLE TOPIC ALTER COLUMN STATUS SET NOT NULL;
ALTER TABLE TOPIC DROP CONSTRAINT IF EXISTS CHK_TOPIC_STATUS;
ALTER TABLE TOPIC ADD CONSTRAINT CHK_TOPIC_STATUS CHECK (STATUS IN ('active', 'deleted'));
ALTER TABLE TOPIC ADD COLUMN ACTIVE_NAME VARCHAR(225) GENERATED ALWAYS AS
  (CASE WHEN STATUS = 'active' THEN LOWER(NAME) ELSE NULL END);
CREATE UNIQUE INDEX IF NOT EXISTS UQ_TOPIC_ORG_ACTIVE_NAME ON TOPIC (ORG_ID, ACTIVE_NAME);

-- Backfill WEBHOOK_DELIVERY.STATUS = 'completed' for deliveries completed before v1.1
UPDATE WEBHOOK_DELIVERY SET STATUS = 'completed'
WHERE STATUS = 'delivered'
  AND DELIVERY_ID IN (SELECT DELIVERY_ID FROM WEBHOOK_DELIVERY_ACK WHERE COMPLETION_STATUS = 'completed');

